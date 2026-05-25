package com.hereliesaz.barcodencrypt.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519

/**
 * Pure-function v6 ratchet implementation. No Android/IO/Argon2 dependencies — the
 * Argon2id bootstrap key is computed by the caller ([EncryptionManager]) and handed in,
 * so everything here is unit-testable on the JVM.
 *
 * Symmetric path (always available): both peers derive the *same* chain from the *same*
 * `Argon2(barcode, salt)` bootstrap key, so the sender's chain key for message `n` equals
 * the receiver's chain key for message `n`. The sender carries its salt in every header;
 * the receiver bootstraps the matching receiving chain from it. Because each peer uses its
 * own salt, the two directions are independent and never collide.
 *
 * The AES-GCM message key is HKDF-derived from the chain key, which advances on every
 * encrypt and decrypt. The whole header is fed to GCM as `associatedData`, and the 96-bit
 * GCM IV is generated randomly by the AEAD (carried at the front of the ciphertext).
 *
 * Out-of-order delivery is supported within [SKIP_WINDOW] messages per chain; keys skipped
 * past are retained in [RatchetState.skipped] with FIFO eviction at the window cap.
 */
object RatchetEngine {

    private const val KEY_SIZE = 32
    private const val MAC_ALG = "HMACSHA256"
    private const val INFO_ROOT = "BCEv6_Root"
    private const val INFO_CHAIN = "BCEv6_Chain"
    private const val INFO_MESSAGE_KEY = "BCEv6_MessageKey"
    private const val INFO_NEXT_CHAIN = "BCEv6_NextChain"
    private const val INFO_RK_CHAIN = "BCEv6_RkChain"
    const val SKIP_WINDOW = 1000

    /**
     * Bootstraps the sending side from a pre-derived 32-byte Argon2 key and our own salt.
     * The receiving side is left empty until the first inbound message reveals the peer's
     * salt (see [bootstrapReceiving]).
     */
    fun bootstrapSending(bootstrapKey: ByteArray, sendSalt: ByteArray): RatchetState {
        val rk = hkdf(bootstrapKey, ByteArray(0), INFO_ROOT)
        val cks = hkdf(bootstrapKey, ByteArray(0), INFO_CHAIN)
        return RatchetState(
            sendSalt = sendSalt,
            cks = cks,
            ns = 0,
            recvSalt = null,
            ckr = null,
            nr = 0,
            skipped = emptyMap(),
            rk = rk,
            dhSelfPriv = null,
            dhSelfPub = null,
            dhRemotePub = null,
            pn = 0,
        )
    }

    /**
     * (Re)seeds the receiving chain from the peer's salt and the matching Argon2 key. Any
     * previously stashed skipped keys (which belonged to the old receiving chain) are
     * dropped, since they can no longer be derived from the new chain.
     */
    fun bootstrapReceiving(state: RatchetState, recvBootstrapKey: ByteArray, recvSalt: ByteArray): RatchetState {
        val ckr = hkdf(recvBootstrapKey, ByteArray(0), INFO_CHAIN)
        return state.copy(recvSalt = recvSalt, ckr = ckr, nr = 0, skipped = emptyMap())
    }

    /** True when the receiving chain is missing or seeded from a different salt. */
    fun needsReceivingBootstrap(state: RatchetState, headerSalt: ByteArray): Boolean =
        state.ckr == null || state.recvSalt == null || !state.recvSalt.contentEquals(headerSalt)

    fun encrypt(
        state: RatchetState,
        contactIdHash: ByteArray,
        plaintext: ByteArray,
        ttlMs: Long?,
        openMax: Int?,
        dhRatchetStep: Boolean = false,
    ): Pair<RatchetState, String> {
        val cks = requireNotNull(state.cks) { "sending chain not initialized" }
        val (nextCks, mk) = kdfCk(cks)
        val header = MessageHeader(
            contactIdHash = contactIdHash,
            salt = state.sendSalt,
            dhPublic = state.dhSelfPub ?: ByteArray(MessageHeader.DH_PUBLIC_SIZE),
            previousChainMessages = state.pn,
            messageNumber = state.ns,
            ttlMs = ttlMs,
            openCountMax = openMax,
            timestampMs = System.currentTimeMillis(),
            dhRatchetStep = dhRatchetStep,
        )
        val headerBytes = header.encode()
        val ciphertext = AesGcmJce(mk).encrypt(plaintext, headerBytes)
        val payload = headerBytes + ciphertext
        val newState = state.copy(cks = nextCks, ns = state.ns + 1)
        return newState to MessageEnvelope.encode(payload)
    }

    fun decrypt(state: RatchetState, envelope: String): Pair<RatchetState, ByteArray>? {
        val payload = MessageEnvelope.decode(envelope) ?: return null
        val header = runCatching { MessageHeader.decode(payload) }.getOrNull() ?: return null
        val headerLen = header.encodedSize()
        if (payload.size < headerLen) return null
        val headerBytes = payload.copyOfRange(0, headerLen)
        val ciphertext = payload.copyOfRange(headerLen, payload.size)
        if (header.ttlMs != null && System.currentTimeMillis() - header.timestampMs > header.ttlMs) {
            return null
        }

        // If the DH ratchet is engaged and the peer rotated their key, advance first.
        var workingState = state
        val peerHasNewDh = workingState.dhSelfPriv != null && workingState.dhRemotePub != null &&
            !header.dhPublic.contentEquals(workingState.dhRemotePub) &&
            !header.dhPublic.contentEquals(ByteArray(MessageHeader.DH_PUBLIC_SIZE))
        if (peerHasNewDh) {
            workingState = dhRatchetReceive(workingState, header.dhPublic)
        }

        // Skipped-key fast path: messages that arrived after a higher-numbered one.
        val skippedId = SkippedKeyId(header.dhPublic, header.messageNumber)
        val skippedMk = workingState.skipped[skippedId]
        if (skippedMk != null) {
            val plain = runCatching {
                AesGcmJce(skippedMk).decrypt(ciphertext, headerBytes)
            }.getOrNull() ?: return null
            val newSkipped = workingState.skipped - skippedId
            return workingState.copy(skipped = newSkipped) to plain
        }

        // Otherwise advance the receiving chain, stashing any keys we step over.
        var ckr = workingState.ckr ?: return null
        var nr = workingState.nr
        val newSkipped = workingState.skipped.toMutableMap()
        if (header.messageNumber < nr) return null  // replay or stale
        val skipsNeeded = header.messageNumber - nr
        if (skipsNeeded > SKIP_WINDOW) return null
        repeat(skipsNeeded) {
            val (nextCkr, mkSkipped) = kdfCk(ckr)
            ckr = nextCkr
            newSkipped[SkippedKeyId(header.dhPublic, nr)] = mkSkipped
            nr += 1
            if (newSkipped.size > SKIP_WINDOW) {
                val eldest = newSkipped.keys.iterator().next()
                newSkipped.remove(eldest)
            }
        }
        val (nextCkr, mk) = kdfCk(ckr)

        // Authenticate before committing the advanced chain so a forged ciphertext can't
        // ratchet our state forward.
        val plain = runCatching { AesGcmJce(mk).decrypt(ciphertext, headerBytes) }.getOrNull()
            ?: return null

        return workingState.copy(ckr = nextCkr, nr = nr + 1, skipped = newSkipped) to plain
    }

    /**
     * Engages the DH ratchet against an externally-supplied remote public key (Plan 4's
     * QR key exchange). Generates a fresh X25519 keypair, mixes the shared secret into the
     * root key, and seeds a new sending chain.
     */
    fun startSession(state: RatchetState, remotePublic: ByteArray): RatchetState {
        val priv = X25519.generatePrivateKey()
        val pub = X25519.publicFromPrivate(priv)
        val shared = X25519.computeSharedSecret(priv, remotePublic)
        val (newRk, sendingChain) = kdfRk(state.rk, shared)
        return state.copy(
            rk = newRk,
            cks = sendingChain,
            ckr = null,
            recvSalt = null,
            dhSelfPriv = priv,
            dhSelfPub = pub,
            dhRemotePub = remotePublic,
            ns = 0,
            nr = 0,
            pn = state.ns,
        )
    }

    private fun dhRatchetReceive(state: RatchetState, remotePublic: ByteArray): RatchetState {
        val privExisting = requireNotNull(state.dhSelfPriv) {
            "cannot receive DH ratchet step before session is started"
        }
        val incomingShared = X25519.computeSharedSecret(privExisting, remotePublic)
        val (rkAfterIn, recvChain) = kdfRk(state.rk, incomingShared)
        val newPriv = X25519.generatePrivateKey()
        val newPub = X25519.publicFromPrivate(newPriv)
        val outgoingShared = X25519.computeSharedSecret(newPriv, remotePublic)
        val (rkAfterOut, sendChain) = kdfRk(rkAfterIn, outgoingShared)
        return state.copy(
            rk = rkAfterOut,
            cks = sendChain,
            ckr = recvChain,
            dhSelfPriv = newPriv,
            dhSelfPub = newPub,
            dhRemotePub = remotePublic,
            pn = state.ns,
            ns = 0,
            nr = 0,
        )
    }

    // ---- KDFs ----

    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val mk = Hkdf.computeHkdf(MAC_ALG, ck, null, INFO_MESSAGE_KEY.toByteArray(), KEY_SIZE)
        val next = Hkdf.computeHkdf(MAC_ALG, ck, null, INFO_NEXT_CHAIN.toByteArray(), KEY_SIZE)
        return next to mk
    }

    private fun kdfRk(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val out = Hkdf.computeHkdf(MAC_ALG, dhOut, rk, INFO_RK_CHAIN.toByteArray(), KEY_SIZE * 2)
        return out.copyOfRange(0, KEY_SIZE) to out.copyOfRange(KEY_SIZE, KEY_SIZE * 2)
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: String): ByteArray =
        Hkdf.computeHkdf(MAC_ALG, ikm, salt, info.toByteArray(), KEY_SIZE)
}
