package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encrypted_script_log")
data class EncryptedScriptLog(
    @PrimaryKey
    val barcodeIdentifier: String,
    val masterKey: ByteArray,
    val currentChainKey: ByteArray,
    val messageNumber: Int
)
