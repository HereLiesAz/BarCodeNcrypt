package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.gson.Gson
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.OpenedMessageRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class MessageHeader(
    val appIdentifier: String = "BCE",
    val messageCount: Int,
    val ttl: Long?,
    val openCount: Int?,
    val timestamp: Long = System.currentTimeMillis(),
    val messageNumber: Int
)

@Singleton
class EncryptionManager @Inject constructor(
    private val encryptedScriptLogManager: EncryptedScriptLogManager,
    private val openedMessageRepository: OpenedMessageRepository
) {

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun getBarcodeIdentifier(barcode: Barcode, password: String? = null): String {
        val valueToHash = if (barcode.keyType == com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE) {
            barcode.value + (password ?: "")
        } else {
            barcode.value
        }
        return sha256(valueToHash)
    }

    suspend fun encrypt(
        plaintext: String,
        barcode: Barcode,
        password: String? = null,
        ttl: Long? = null,
        openCount: Int? = null
    ): String? {
        val barcodeIdentifier = getBarcodeIdentifier(barcode, password)
        val result = encryptedScriptLogManager.getTemporaryKey(barcodeIdentifier, barcode.value) ?: return null
        val temporaryKey = result.first
        val messageNumber = result.second
        val aead: Aead = AesGcmJce(temporaryKey)
        val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), null)
        val header = MessageHeader(
            messageCount = 1,
            ttl = ttl,
            openCount = openCount,
            messageNumber = messageNumber
        )
        val gson = Gson()
        val headerJson = gson.toJson(header)
        val headerBase64 = Base64.encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$headerBase64.$ciphertextBase64"
    }

    suspend fun decrypt(
        ciphertext: String,
        barcode: Barcode,
        password: String? = null
    ): String? {
        val parts = ciphertext.split(".")
        if (parts.size != 2) return null
        val headerBase64 = parts[0]
        val ciphertextBase64 = parts[1]
        val headerJson = String(Base64.decode(headerBase64, Base64.NO_WRAP), StandardCharsets.UTF_8)
        val gson = Gson()
        val header = gson.fromJson(headerJson, MessageHeader::class.java)

        if (header.ttl != null && System.currentTimeMillis() - header.timestamp > header.ttl) {
            return null // Message has expired
        }

        val messageId = sha256(ciphertext)
        if (header.openCount != null) {
            val openedMessage = openedMessageRepository.getById(messageId)
            if (openedMessage != null && openedMessage.openCount >= header.openCount) {
                return null // Message has been opened too many times
            }
        }

        val barcodeIdentifier = getBarcodeIdentifier(barcode, password)
        val temporaryKey = encryptedScriptLogManager.getKeyForMessageNumber(barcodeIdentifier, barcode.value, header.messageNumber) ?: return null
        val aead: Aead = AesGcmJce(temporaryKey)
        return try {
            val decrypted = aead.decrypt(Base64.decode(ciphertextBase64, Base64.NO_WRAP), null)
            val plaintext = String(decrypted, StandardCharsets.UTF_8)
            if (header.openCount != null) {
                openedMessageRepository.incrementOpenCount(messageId)
            }
            plaintext
        } catch (e: Exception) {
            null
        }
    }
}
