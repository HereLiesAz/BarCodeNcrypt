package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "opened_messages")
data class OpenedMessage(
    @PrimaryKey
    val messageId: String,
    val openCount: Int
)
