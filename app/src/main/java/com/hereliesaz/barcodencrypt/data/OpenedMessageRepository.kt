package com.hereliesaz.barcodencrypt.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenedMessageRepository @Inject constructor(private val openedMessageDao: OpenedMessageDao) {

    suspend fun getById(messageId: String): OpenedMessage? {
        return openedMessageDao.getById(messageId)
    }

    suspend fun incrementOpenCount(messageId: String) {
        val openedMessage = openedMessageDao.getById(messageId)
        if (openedMessage != null) {
            openedMessageDao.update(openedMessage.copy(openCount = openedMessage.openCount + 1))
        } else {
            openedMessageDao.insert(OpenedMessage(messageId, 1))
        }
    }
}
