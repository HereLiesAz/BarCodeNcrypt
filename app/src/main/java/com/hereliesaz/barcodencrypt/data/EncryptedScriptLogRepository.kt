package com.hereliesaz.barcodencrypt.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedScriptLogRepository @Inject constructor(private val encryptedScriptLogDao: EncryptedScriptLogDao) {
    suspend fun insert(encryptedScriptLog: EncryptedScriptLog) {
        encryptedScriptLogDao.insert(encryptedScriptLog)
    }

    suspend fun update(encryptedScriptLog: EncryptedScriptLog) {
        encryptedScriptLogDao.update(encryptedScriptLog)
    }

    suspend fun getByIdentifier(barcodeIdentifier: String): EncryptedScriptLog? {
        return encryptedScriptLogDao.getByIdentifier(barcodeIdentifier)
    }
}
