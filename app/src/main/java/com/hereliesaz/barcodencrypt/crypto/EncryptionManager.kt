package com.hereliesaz.barcodencrypt.crypto

import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.RatchetStateRepository
import com.hereliesaz.barcodencrypt.util.Hashing
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level v6 crypto entry point. Stateless façade over [RatchetEngine] +
 * [RatchetStateRepository] that owns the Argon2id bootstrap.
 *
 * On the first send to a contact we generate a random per-contact salt, derive the
 * Argon2 bootstrap key over `(barcode ‖ password, salt)`, and seed a sending chain. The
 * salt rides in every outbound header. On receive we read the peer's salt from the
 * header and seed (or re-seed) the receiving chain from the matching Argon2 key, so both
 * sides derive identical message keys.
 */
@Singleton
class EncryptionManager @Inject constructor(
    private val ratchetStateRepository: RatchetStateRepository,
) {
    private val random = SecureRandom()

    suspend fun encrypt(
        plaintext: String,
        contactLookupKey: String,
        barcode: Barcode,
        password: String? = null,
        ttlMs: Long? = null,
        openMax: Int? = null,
    ): String? {
        val barcodeValue = resolveBarcodeValue(barcode) ?: return null
        val state = loadOrBootstrapSending(contactLookupKey, barcodeValue, password)
        val contactIdHash = contactIdHashFor(contactLookupKey)
        val (newState, envelope) = RatchetEngine.encrypt(
            state = state,
            contactIdHash = contactIdHash,
            plaintext = plaintext.toByteArray(Charsets.UTF_8),
            ttlMs = ttlMs,
            openMax = openMax,
        )
        ratchetStateRepository.save(contactLookupKey, newState)
        return envelope
    }

    suspend fun decrypt(
        envelope: String,
        contactLookupKey: String,
        barcode: Barcode,
        password: String? = null,
    ): String? {
        val payload = MessageEnvelope.decode(envelope) ?: return null
        val header = runCatching { MessageHeader.decode(payload) }.getOrNull() ?: return null
        val barcodeValue = resolveBarcodeValue(barcode) ?: return null

        var state = loadOrBootstrapSending(contactLookupKey, barcodeValue, password)
        if (RatchetEngine.needsReceivingBootstrap(state, header.salt)) {
            val recvKey = deriveBootstrapKey(barcodeValue, password, header.salt)
            state = RatchetEngine.bootstrapReceiving(state, recvKey, header.salt)
        }

        val (newState, plaintext) = RatchetEngine.decrypt(state, envelope) ?: return null
        ratchetStateRepository.save(contactLookupKey, newState)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Backwards-compatible signature retained for the
     * [com.hereliesaz.barcodencrypt.viewmodel.EncryptionOverlayViewModel] callsite, which
     * doesn't yet have a contact context. Falls back to the barcode's own contact.
     */
    @Deprecated(
        message = "Pass a contactLookupKey. Plan 4 will thread it through the overlay.",
        replaceWith = ReplaceWith("encrypt(plaintext, contactLookupKey, barcode, password, ttl, openCount)"),
    )
    suspend fun encrypt(
        plaintext: String,
        barcode: Barcode,
        password: String? = null,
        ttl: Long? = null,
        openCount: Int? = null,
    ): String? = encrypt(
        plaintext = plaintext,
        contactLookupKey = barcode.contactLookupKey,
        barcode = barcode,
        password = password,
        ttlMs = ttl,
        openMax = openCount,
    )

    private suspend fun loadOrBootstrapSending(
        contactLookupKey: String,
        barcodeValue: String,
        password: String?,
    ): RatchetState {
        ratchetStateRepository.load(contactLookupKey)?.let { return it }
        val sendSalt = ByteArray(MessageHeader.SALT_SIZE).also { random.nextBytes(it) }
        val bootstrapKey = deriveBootstrapKey(barcodeValue, password, sendSalt)
        val state = RatchetEngine.bootstrapSending(bootstrapKey, sendSalt)
        ratchetStateRepository.save(contactLookupKey, state)
        return state
    }

    private fun deriveBootstrapKey(barcodeValue: String, password: String?, salt: ByteArray): ByteArray {
        val keyMaterial = barcodeValue.toByteArray(Charsets.UTF_8) +
            (password?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))
        return Argon2.derive(keyMaterial, salt)
    }

    private fun resolveBarcodeValue(barcode: Barcode): String? {
        val value = barcode.value.takeIf { it.isNotEmpty() } ?: run {
            barcode.decryptValue()
            barcode.value
        }
        return value.takeIf { it.isNotEmpty() }
    }

    private fun contactIdHashFor(contactLookupKey: String): ByteArray {
        val hex = Hashing.sha256(contactLookupKey)
        // First 16 bytes (32 hex chars) of SHA-256 keeps it deterministic, stable across
        // installs, and short enough for the on-wire CONTACT_ID_HASH_SIZE slot.
        return ByteArray(MessageHeader.CONTACT_ID_HASH_SIZE) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
