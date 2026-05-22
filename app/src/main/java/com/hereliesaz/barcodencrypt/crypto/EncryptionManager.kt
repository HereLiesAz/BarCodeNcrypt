package com.hereliesaz.barcodencrypt.crypto

import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.RatchetStateRepository
import com.hereliesaz.barcodencrypt.util.Hashing
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level v5 crypto entry point. Stateless façade over [RatchetEngine] +
 * [RatchetStateRepository].
 *
 * Bootstrap (first encrypt/decrypt for a contact) runs Argon2id over the barcode and
 * optional password to derive the root key; subsequent calls advance the symmetric
 * chain and persist the updated [RatchetState]. The DH ratchet engages when Plan 4's
 * QR key-exchange UI lands; until then both sides ride the symmetric chain.
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
        val (salt, state) = loadOrBootstrap(contactLookupKey, barcode, password) ?: return null
        val contactIdHash = contactIdHashFor(contactLookupKey)
        val (newState, envelope) = RatchetEngine.encrypt(
            state = state,
            contactIdHash = contactIdHash,
            plaintext = plaintext.toByteArray(Charsets.UTF_8),
            ttlMs = ttlMs,
            openMax = openMax,
        )
        ratchetStateRepository.save(contactLookupKey, salt, newState)
        return envelope
    }

    suspend fun decrypt(
        envelope: String,
        contactLookupKey: String,
        barcode: Barcode,
        password: String? = null,
    ): String? {
        val (salt, state) = loadOrBootstrap(contactLookupKey, barcode, password) ?: return null
        val (newState, plaintext) = RatchetEngine.decrypt(state, envelope) ?: return null
        ratchetStateRepository.save(contactLookupKey, salt, newState)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Backwards-compatible signature retained for the existing
     * [com.hereliesaz.barcodencrypt.viewmodel.EncryptionOverlayViewModel] callsite,
     * which doesn't yet have a contact context. Returns null until Plan 4 threads the
     * contact through the overlay flow.
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

    private suspend fun loadOrBootstrap(
        contactLookupKey: String,
        barcode: Barcode,
        password: String?,
    ): Pair<ByteArray, RatchetState>? {
        ratchetStateRepository.load(contactLookupKey)?.let { return it }
        // First encrypt/decrypt for this contact: bootstrap a fresh state.
        val barcodeValue = barcode.value.takeIf { it.isNotEmpty() } ?: run {
            barcode.decryptValue()
            barcode.value
        }
        if (barcodeValue.isEmpty()) return null
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val state = RatchetEngine.bootstrap(
            barcode = barcodeValue.toByteArray(Charsets.UTF_8),
            password = password?.toByteArray(Charsets.UTF_8),
            contactSalt = salt,
        )
        ratchetStateRepository.save(contactLookupKey, salt, state)
        return salt to state
    }

    private fun contactIdHashFor(contactLookupKey: String): ByteArray {
        val hex = Hashing.sha256(contactLookupKey)
        // First 16 bytes (32 hex chars) of SHA-256 keeps it deterministic, stable across
        // installs, and short enough for the on-wire CONTACT_ID_HASH_SIZE slot.
        return ByteArray(MessageHeader.CONTACT_ID_HASH_SIZE) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val SALT_SIZE = 16
    }
}
