package com.hereliesaz.barcodencrypt.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the master key stored in the Android Keystore and provides methods
 * for encrypting and decrypting data using that key.
 */
object KeyManager {

    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "barcodencrypt_master_key"
    private const val SEARCH_HMAC_KEY_ALIAS = "barcodencrypt_search_hmac_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val GCM_IV_LENGTH = 12 // 96 bits

    private val keyStore = KeyStore.getInstance(PROVIDER).apply {
        load(null)
    }

    /**
     * Retrieves the master key from the Keystore, generating it if it doesn't exist.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateMasterKey()
    }

    /**
     * Generates a new AES-256 master key and stores it in the Android Keystore.
     */
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
        }.build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the given plaintext.
     * @param plaintext The data to encrypt.
     * @return A Pair containing the IV and the encrypted data.
     */
    fun encrypt(plaintext: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Pair(cipher.iv, encrypted)
    }

    /**
     * Decrypts the given ciphertext.
     * @param iv The initialization vector used for encryption.
     * @param ciphertext The data to decrypt.
     * @return The decrypted plaintext.
     */
    fun decrypt(iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Derives a deterministic, searchable token for [value] using an HMAC-SHA256 key held
     * in the Keystore. Unlike a plain hash, the token can't be precomputed or dictionary-
     * attacked from the database file alone, because the HMAC key is non-exportable and
     * never leaves the Keystore. Same input always yields the same token, so it can be
     * stored and queried as an exact-match handle for otherwise-encrypted values.
     */
    fun searchToken(value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getOrCreateSearchHmacKey())
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateSearchHmacKey(): SecretKey {
        return (keyStore.getKey(SEARCH_HMAC_KEY_ALIAS, null) as? SecretKey) ?: generateSearchHmacKey()
    }

    private fun generateSearchHmacKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
        val parameterSpec = KeyGenParameterSpec.Builder(
            SEARCH_HMAC_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN
        ).build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }
}
