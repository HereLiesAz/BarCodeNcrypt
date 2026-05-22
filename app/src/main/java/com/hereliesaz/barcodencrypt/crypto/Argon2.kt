package com.hereliesaz.barcodencrypt.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/**
 * Argon2id KDF used to bootstrap the v5 Double Ratchet from a barcode and optional password.
 *
 * Parameters are fixed by the wire-format spec (`docs/crypto/wire-format.md`):
 * - mode  = Argon2id
 * - t     = 3
 * - m     = 64 MiB
 * - p     = 2
 * - hash len = 32 bytes
 *
 * These values are part of the protocol — changing them breaks interoperability with the
 * checked-in test vectors and any persisted [RatchetState].
 */
object Argon2 {
    private const val TIME_COST = 3
    private const val MEMORY_COST_KIB = 65_536
    private const val PARALLELISM = 2
    private const val HASH_LEN = 32

    private val engine = Argon2Kt()

    fun derive(password: ByteArray, salt: ByteArray): ByteArray {
        require(salt.size in 8..64) { "salt must be 8..64 bytes" }
        val res = engine.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = TIME_COST,
            mCostInKibibyte = MEMORY_COST_KIB,
            parallelism = PARALLELISM,
            hashLengthInBytes = HASH_LEN,
        )
        return res.rawHashAsByteArray()
    }
}
