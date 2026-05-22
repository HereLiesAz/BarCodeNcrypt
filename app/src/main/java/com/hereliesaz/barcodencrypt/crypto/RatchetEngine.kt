package com.hereliesaz.barcodencrypt.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-function v5 Double Ratchet implementation.
 *
 * Symmetric path (always available): the chain key advances on every encrypt/decrypt,
 * the message key is HKDF-derived from the chain key, and the AES-GCM nonce is
 * deterministically derived from the message key and message number. The whole header
 * is fed to GCM as `associatedData` so tampering with TTL, openCount, dhPublic, n, or
 * any other field fails the tag check.
 *
 * DH path (engaged via [startSession]): each fresh sending chain begins by mixing in
 * `DH(self_private, remote_public)` into the root key via [kdfRk]. A peer detecting an
 * unseen `dhPublic` in an incoming header advances its own DH ratchet first (via
 * [dhRatchetReceive]) before processing the message.
 *
 * Out-of-order delivery is supported within [SKIP_WINDOW] messages per chain. Any keys
 * skipped past are retained in [RatchetState.skipped] with FIFO eviction at the
 * window cap.
 *
 * No Android/IO dependencies — everything is unit-testable on the JVM.
 */
object RatchetEngine {

    private const val KEY_SIZE = 32
    private const val MAC_ALG = "HMACSHA256"
    private const val INFO_ROOT = "BCEv5_Root"
    private const val INFO_SEND_CHAIN = "BCEv5_SendChain"
    private const val INFO_RECV_CHAIN = "BCEv5_RecvChain"
    private const val INFO_MESSAGE_KEY = "BCEv5_MessageKey"
    private const val INFO_NEXT_CHAIN = "BCEv5_NextChain"
    private const val INFO_NONCE = "BCEv5_Nonce"
    private const val INFO_RK_CHAIN = "BCEv5_RkChain"
    const val SKIP_WINDOW = 1000

    fun bootstrap(barcode: ByteArray, password: ByteArray?, contactSalt: ByteArray): RatchetState {
        val keyMaterial = barcode + (password ?: ByteArray(0))
        val bootstrapKey = Argon2.derive(keyMaterial, contactSalt)
        val rk = hkdf(bootstrapKey, ByteArray(0), INFO_ROOT)
        val cks = hkdf(bootstrapKey, ByteArray(0), INFO_SEND_CHAIN)
        val ckr = hkdf(bootstrapKey, ByteArray(0), INFO_RECV_CHAIN)
        return RatchetState(
            rk = rk, cks = cks, ckr = ckr,
            dhSelfPriv = null, dhSelfPub = null, dhRemotePub = null,
            ns = 0, nr = 0, pn = 0, skipped = emptyMap(),
        )
    }

    /**
     * Engages the DH ratchet against an externally-supplied remote public key (e.g.
     * Plan 4's QR key exchange). Generates a fresh X25519 keypair, computes the
     * shared secret, mixes it into the root key, and seeds a new sending chain.
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
            dhSelfPriv = priv,
            dhSelfPub = pub,
            dhRemotePub = remotePublic,
            ns = 0, nr = 0, pn = state.ns,
        )
    }

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
        val nonce = deriveNonce(mk, state.ns)
        val header = MessageHeader(
            contactIdHash = contactIdHash,
            dhPublic = state.dhSelfPub ?: ByteArray(MessageHeader.DH_PUBLIC_SIZE),
            previousChainMessages = state.pn,
            messageNumber = state.ns,
            ttlMs = ttlMs,
            openCountMax = openMax,
            timestampMs = System.currentTimeMillis(),
            nonce = nonce,
            dhRatchetStep = dhRatchetStep,
        )
        val headerBytes = header.encode()
        val aead = AesGcmJce(mk)
        val ciphertext = aead.encrypt(plaintext, headerBytes)
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

        // If we have a DH ratchet engaged and the peer has rotated their key,
        // advance our DH ratchet before processing the message.
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
        var ckr = requireNotNull(workingState.ckr) { "receiving chain not initialized" }
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
        ckr = nextCkr
        nr += 1

        val plain = runCatching { AesGcmJce(mk).decrypt(ciphertext, headerBytes) }.getOrNull()
            ?: return null

        return workingState.copy(ckr = ckr, nr = nr, skipped = newSkipped) to plain
    }

    /**
     * Handles an incoming DH ratchet step from the peer: derives a new receiving chain
     * from the peer's freshly-rotated public key, then rotates our own ephemeral keys
     * and seeds a new sending chain.
     */
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

    private fun deriveNonce(mk: ByteArray, n: Int): ByteArray {
        val mac = Mac.getInstance(MAC_ALG)
        mac.init(SecretKeySpec(mk, MAC_ALG))
        val nBytes = byteArrayOf(
            (n ushr 24).toByte(),
            (n ushr 16).toByte(),
            (n ushr 8).toByte(),
            n.toByte(),
        )
        val full = mac.doFinal(INFO_NONCE.toByteArray() + nBytes)
        return full.copyOf(MessageHeader.NONCE_SIZE)
    }
}
