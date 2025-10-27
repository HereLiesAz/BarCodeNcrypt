package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.Random
import com.google.crypto.tink.subtle.X25519
import com.google.gson.Gson
import com.hereliesaz.barcodencrypt.crypto.model.DecryptedMessage
import com.hereliesaz.barcodencrypt.crypto.model.V3Message
import com.hereliesaz.barcodencrypt.data.Contact
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest

object EncryptionManager {

    // V3 Constants
    private const val HEADER_PREFIX_V3 = "~BCEv3~"
    private const val HKDF_ROOT_KEY_INFO = "BCEv3_RootKey"
    private const val HKDF_CHAIN_KEY_INFO = "BCEv3_ChainKey"
    private const val HKDF_MESSAGE_KEY_INFO = "BCEv3_MessageKey"

    // Constants for message options
    const val OPTION_SINGLE_USE = "single-use"
    const val OPTION_TTL_HOURS_PREFIX = "ttl_hours="
    const val OPTION_TTL_ON_OPEN_TRUE = "ttl_on_open=true"

    private const val KEY_DERIVATION_ALGORITHM = "SHA-256"
    private const val HEADER_PREFIX_V4 = "~BCEv4~"
    private const val HKDF_MAC_ALGORITHM = "HMACSHA256" // Corresponds to Tink's PrfHmacSha256
    private const val DERIVED_KEY_SIZE_BYTES = 32 // For AES-256

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun getIkm(barcode: com.hereliesaz.barcodencrypt.data.Barcode, password: String? = null): String {
        return when (barcode.keyType) {
            com.hereliesaz.barcodencrypt.data.KeyType.SINGLE_BARCODE -> barcode.value
            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE -> {
                if (password == null) throw IllegalArgumentException("Password is required for password-protected key")
                sha256(barcode.value + password)
            }
            com.hereliesaz.barcodencrypt.data.KeyType.BARCODE_SEQUENCE -> {
                barcode.barcodeSequence?.joinToString("") ?: ""
            }
            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE -> {
                if (password == null) throw IllegalArgumentException("Password is required for password-protected key")
                sha256((barcode.barcodeSequence?.joinToString("") ?: "") + password)
            }
            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD -> barcode.value
        }
    }

    // --- V3 Double Ratchet Implementation ---

    @Throws(GeneralSecurityException::class)
    fun init() {
        AeadConfig.register()
        HybridConfig.register()
    }

    @Throws(GeneralSecurityException::class)
    internal fun kdfRk(rk: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        val salt = rk
        val prk = Hkdf.computeHkdf(HKDF_MAC_ALGORITHM, dhOutput, salt, HKDF_ROOT_KEY_INFO.toByteArray(), DERIVED_KEY_SIZE_BYTES * 2)
        val newRk = prk.sliceArray(0 until DERIVED_KEY_SIZE_BYTES)
        val newCk = prk.sliceArray(DERIVED_KEY_SIZE_BYTES until DERIVED_KEY_SIZE_BYTES * 2)
        return Pair(newRk, newCk)
    }

    @Throws(GeneralSecurityException::class)
    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = Hkdf.computeHkdf(HKDF_MAC_ALGORITHM, ck, null, HKDF_MESSAGE_KEY_INFO.toByteArray(), DERIVED_KEY_SIZE_BYTES)
        val nextCk = Hkdf.computeHkdf(HKDF_MAC_ALGORITHM, ck, null, HKDF_CHAIN_KEY_INFO.toByteArray(), DERIVED_KEY_SIZE_BYTES)
        return Pair(messageKey, nextCk)
    }

    fun generateV3KeyPair(): ByteArray = X25519.generatePrivateKey()
    fun getV3PublicKey(privateKey: ByteArray): ByteArray = X25519.publicFromPrivate(privateKey)

    @Throws(GeneralSecurityException::class)
    fun encryptV3(
        plaintext: String,
        contact: Contact,
        options: List<String> = emptyList(),
        keyName: String
    ): Pair<Contact, String?> {
        val sendingChainKey = contact.sendingChainKey ?: return Pair(contact, null)
        val dhKeyPair = contact.dhKeyPair ?: return Pair(contact, null)

        val (newSendingChainKey, messageKey) = kdfCk(sendingChainKey)
        val updatedContact = contact.copy(
            sendingChainKey = newSendingChainKey,
            sendingMessageNumber = contact.sendingMessageNumber + 1
        )

        val aead: Aead = AesGcmJce(messageKey)
        val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), null)

        val message = V3Message(
            dhPublicKey = getV3PublicKey(dhKeyPair),
            previousMessageCount = contact.previousSendingChainMessageNumber,
            messageNumber = contact.sendingMessageNumber,
            ciphertext = ciphertext,
            options = options,
            keyName = keyName,
            contactLookupKey = contact.lookupKey
        )
        val gson = Gson()
        val json = gson.toJson(message)
        val encodedMessage = HEADER_PREFIX_V3 + Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return Pair(updatedContact, encodedMessage)
    }

    @Throws(GeneralSecurityException::class)
    fun decryptV3(
        ciphertext: String,
        contact: Contact
    ): Pair<Contact, DecryptedMessage?> {
        val message = com.hereliesaz.barcodencrypt.util.MessageParser.parseV3Message(ciphertext) ?: return Pair(contact, null)

        val (updatedContact, plaintext) = ratchetDecrypt(message, contact)
        if (plaintext != null) {
            val singleUse = message.options.contains(OPTION_SINGLE_USE)
            val ttlOnOpen = message.options.contains(OPTION_TTL_ON_OPEN_TRUE)
            val ttlHoursString = message.options.find { it.startsWith(OPTION_TTL_HOURS_PREFIX) }
            val ttlHours = ttlHoursString?.removePrefix(OPTION_TTL_HOURS_PREFIX)?.toIntOrNull()

            val decryptedMessage = DecryptedMessage(
                plaintext = plaintext,
                keyName = message.keyName,
                counter = message.messageNumber.toLong(),
                maxAttempts = 0,
                singleUse = singleUse,
                ttlHours = ttlHours,
                ttlOnOpen = ttlOnOpen
            )
            return Pair(updatedContact, decryptedMessage)
        }
        return Pair(contact, null)
    }

    private fun ratchetDecrypt(
        message: V3Message,
        contact: Contact
    ): Pair<Contact, String?> {
        // Try to decrypt with skipped message keys first
        val (contactAfterSkipped, plaintext) = trySkippedMessageKeys(message, contact)
        if (plaintext != null) {
            return Pair(contactAfterSkipped, plaintext)
        }

        val remotePublicKey = contact.dhRemotePublicKey
        if (remotePublicKey != null && message.dhPublicKey.contentEquals(remotePublicKey)) {
            // Standard symmetric-key ratchet step
            val receivingChainKey = contact.receivingChainKey ?: return Pair(contact, null)
            val (messageKey, nextReceivingChainKey) = kdfCk(receivingChainKey)
            return try {
                val decryptedText = String(AesGcmJce(messageKey).decrypt(message.ciphertext, null), StandardCharsets.UTF_8)
                val updatedContact = contact.copy(
                    receivingChainKey = nextReceivingChainKey,
                    receivingMessageNumber = contact.receivingMessageNumber + 1
                )
                Pair(updatedContact, decryptedText)
            } catch (e: GeneralSecurityException) {
                // If decryption fails, it might be an out-of-order message.
                val updatedContact = addSkippedMessageKey(contact, message, messageKey)
                Pair(updatedContact, null)
            }
        } else {
            // DH ratchet step
            val rootKey = contact.rootKey ?: return Pair(contact, null)
            val dhKeyPair = contact.dhKeyPair ?: return Pair(contact, null)

            val dhOutput = X25519.computeSharedSecret(dhKeyPair, message.dhPublicKey)
            val (newRootKey, newReceivingChainKey) = kdfRk(rootKey, dhOutput)

            val newDhKeyPair = generateV3KeyPair()
            val (messageKey, nextReceivingChainKey) = kdfCk(newReceivingChainKey)
            return try {
                val decryptedText = String(AesGcmJce(messageKey).decrypt(message.ciphertext, null), StandardCharsets.UTF_8)
                val updatedContact = contact.copy(
                    rootKey = newRootKey,
                    receivingChainKey = nextReceivingChainKey,
                    receivingMessageNumber = 1,
                    dhRemotePublicKey = message.dhPublicKey,
                    dhKeyPair = newDhKeyPair,
                    previousSendingChainMessageNumber = contact.sendingMessageNumber,
                    sendingMessageNumber = 0
                )
                Pair(updatedContact, decryptedText)
            } catch (e: GeneralSecurityException) {
                Pair(contact, null)
            }
        }
    }

    private fun trySkippedMessageKeys(
        message: V3Message,
        contact: Contact
    ): Pair<Contact, String?> {
        val skippedKeysJson = contact.skippedMessageKeys ?: return Pair(contact, null)
        val gson = Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
        val skippedMap: Map<String, String> = gson.fromJson(skippedKeysJson, type)

        val messageKeyB64 = skippedMap[message.id] ?: return Pair(contact, null)

        return try {
            val messageKey = Base64.decode(messageKeyB64, Base64.NO_WRAP)
            val plaintext = String(AesGcmJce(messageKey).decrypt(message.ciphertext, null), StandardCharsets.UTF_8)
            val newSkippedMap = skippedMap - message.id
            val updatedContact = contact.copy(skippedMessageKeys = gson.toJson(newSkippedMap))
            Pair(updatedContact, plaintext)
        } catch (e: GeneralSecurityException) {
            Pair(contact, null)
        }
    }

    private fun addSkippedMessageKey(
        contact: Contact,
        message: V3Message,
        messageKey: ByteArray
    ): Contact {
        val gson = Gson()
        val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, String>>() {}.type
        val skippedMap: MutableMap<String, String> = if (contact.skippedMessageKeys != null) {
            gson.fromJson(contact.skippedMessageKeys, type)
        } else {
            mutableMapOf()
        }
        skippedMap[message.id] = Base64.encodeToString(messageKey, Base64.NO_WRAP)
        return contact.copy(skippedMessageKeys = gson.toJson(skippedMap))
    }


    fun encrypt(
        plaintext: String,
        ikm: String,
        keyName: String,
        counter: Long,
        options: List<String> = emptyList(),
        maxAttempts: Int = 0
    ): String? {
        return try {
            val salt = Random.randBytes(16 + com.google.crypto.tink.subtle.Random.randInt(17)) // Generate a salt of a random length between 16 and 32 bytes

            // Derive encryption key using HKDF
            val derivedKeyBytes = Hkdf.computeHkdf(
                HKDF_MAC_ALGORITHM,
                ikm.toByteArray(StandardCharsets.UTF_8),
                salt,
                null, // No specific "info" field for now
                DERIVED_KEY_SIZE_BYTES
            )

            val aead: Aead = AesGcmJce(derivedKeyBytes)

            val associatedData = createAssociatedData(keyName, counter, options, maxAttempts)
            val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), associatedData)

            val message = com.hereliesaz.barcodencrypt.crypto.model.TinkMessage(
                salt = salt,
                ciphertext = ciphertext,
                keyName = keyName,
                counter = counter,
                options = options,
                maxAttempts = maxAttempts
            )
            val gson = Gson()
            val json = gson.toJson(message)
            HEADER_PREFIX_V4 + Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decrypt(ciphertext: String, ikm: String): com.hereliesaz.barcodencrypt.crypto.model.DecryptedMessage? {
        val message = com.hereliesaz.barcodencrypt.util.MessageParser.parseV4Message(ciphertext)
        return if (message != null) {
            decryptMessage(message, ikm)
        } else {
            null
        }
    }

    private fun decryptMessage(message: com.hereliesaz.barcodencrypt.crypto.model.TinkMessage, ikm: String): com.hereliesaz.barcodencrypt.crypto.model.DecryptedMessage? {
        return try {
            // Derive encryption key using HKDF with the message's salt
            val derivedKeyBytes = Hkdf.computeHkdf(
                HKDF_MAC_ALGORITHM,
                ikm.toByteArray(StandardCharsets.UTF_8),
                message.salt,
                null, // Must match encryption if "info" was used
                DERIVED_KEY_SIZE_BYTES
            )

            val aead: Aead = AesGcmJce(derivedKeyBytes)

            val associatedData = createAssociatedData(message.keyName, message.counter, message.options, message.maxAttempts)
            val decrypted = aead.decrypt(message.ciphertext, associatedData)
            val plaintext = String(decrypted, StandardCharsets.UTF_8)

            val singleUse = message.options.contains(OPTION_SINGLE_USE)
            val ttlOnOpen = message.options.contains(OPTION_TTL_ON_OPEN_TRUE)
            val ttlHoursString = message.options.find { it.startsWith(OPTION_TTL_HOURS_PREFIX) }
            val ttlHours = ttlHoursString?.removePrefix(OPTION_TTL_HOURS_PREFIX)?.toIntOrNull()

            com.hereliesaz.barcodencrypt.crypto.model.DecryptedMessage(
                plaintext = plaintext,
                keyName = message.keyName,
                counter = message.counter,
                maxAttempts = message.maxAttempts,
                singleUse = singleUse,
                ttlHours = ttlHours,
                ttlOnOpen = ttlOnOpen
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createAssociatedData(keyName: String, counter: Long, options: List<String>, maxAttempts: Int): ByteArray {
        val gson = Gson()
        val data = mapOf(
            "keyName" to keyName,
            "counter" to counter,
            "options" to options,
            "maxAttempts" to maxAttempts
        )
        return gson.toJson(data).toByteArray(StandardCharsets.UTF_8)
    }

    // Made internal to be accessible from other modules like OverlayService
}
