package com.hereliesaz.barcodencrypt.crypto

import com.google.crypto.tink.subtle.Hkdf
import com.hereliesaz.barcodencrypt.data.EncryptedScriptLog
import com.hereliesaz.barcodencrypt.data.EncryptedScriptLogRepository
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedScriptLogManager @Inject constructor(
    private val encryptedScriptLogRepository: EncryptedScriptLogRepository
) {
    private val DERIVED_KEY_SIZE_BYTES = 32
    private val HKDF_MAC_ALGORITHM = "HMACSHA256"
    private val HKDF_CHAIN_KEY_INFO = "BCEv4_ChainKey"
    private val HKDF_MESSAGE_KEY_INFO = "BCEv4_MessageKey"

    @Throws(GeneralSecurityException::class)
    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = Hkdf.computeHkdf(HKDF_MAC_ALGORITHM, ck, null, HKDF_MESSAGE_KEY_INFO.toByteArray(), DERIVED_KEY_SIZE_BYTES)
        val nextCk = Hkdf.computeHkdf(HKDF_MAC_ALGORITHM, ck, null, HKDF_CHAIN_KEY_INFO.toByteArray(), DERIVED_KEY_SIZE_BYTES)
        return Pair(messageKey, nextCk)
    }

    suspend fun getTemporaryKey(barcodeIdentifier: String, barcodeValue: String): Pair<ByteArray, Int>? {
        var log = encryptedScriptLogRepository.getByIdentifier(barcodeIdentifier)
        if (log == null) {
            val masterKey = barcodeValue.toByteArray(StandardCharsets.UTF_8)
            val initialChainKey = Hkdf.computeHkdf(HKDF_MAC_ALGORITHM, masterKey, null, HKDF_CHAIN_KEY_INFO.toByteArray(), DERIVED_KEY_SIZE_BYTES)
            log = EncryptedScriptLog(
                barcodeIdentifier = barcodeIdentifier,
                masterKey = masterKey,
                currentChainKey = initialChainKey,
                messageNumber = 0
            )
            encryptedScriptLogRepository.insert(log)
        }

        val (messageKey, nextChainKey) = kdfCk(log.currentChainKey)
        val updatedLog = log.copy(
            currentChainKey = nextChainKey,
            messageNumber = log.messageNumber + 1
        )
        encryptedScriptLogRepository.update(updatedLog)

        return Pair(messageKey, updatedLog.messageNumber)
    }

    suspend fun getKeyForMessageNumber(barcodeIdentifier: String, barcodeValue: String, messageNumber: Int): ByteArray? {
        val log = encryptedScriptLogRepository.getByIdentifier(barcodeIdentifier) ?: return null

        var chainKey = log.masterKey
        for (i in 0 until messageNumber) {
            val (_, nextCk) = kdfCk(chainKey)
            chainKey = nextCk
        }
        val (messageKey, _) = kdfCk(chainKey)
        return messageKey
    }
}
