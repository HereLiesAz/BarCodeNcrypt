package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the cryptographic state log for a specific barcode.
 *
 * This entity is the core of the Rolling Key (Ratchet) protocol (V4). It tracks the current
 * state of the key derivation chain, ensuring that each message uses a unique, non-reusable key.
 *
 * The rolling key mechanism provides Forward Secrecy:
 * 1. A [masterKey] is derived from the physical barcode.
 * 2. A [currentChainKey] is derived from the Master Key (initially) or the previous Chain Key.
 * 3. Each message consumes the current Chain Key to produce a Message Key and the *next* Chain Key.
 *
 * By storing only the *current* state, the app ensures that past keys cannot be easily recovered
 * from the database state alone.
 *
 * @property barcodeIdentifier The unique identifier of the barcode this log belongs to. This acts as the Primary Key.
 * @property masterKey The static root key derived from the barcode (and optional password). Used for recovering the chain state during decryption.
 * @property currentChainKey The current ephemeral key in the ratchet. Used to encrypt the *next* outgoing message.
 * @property messageNumber The sequence number of the next message to be sent. Incremented with every encryption operation.
 */
@Entity(tableName = "encrypted_script_log")
data class EncryptedScriptLog(
    /**
     * Unique identifier matching [Barcode.identifier] logic (Hash of value).
     */
    @PrimaryKey
    val barcodeIdentifier: String,

    /**
     * The root key derived from the barcode material.
     * Stored as a byte array.
     */
    val masterKey: ByteArray,

    /**
     * The current ratchet state key.
     * This key is advanced (hashed) every time a message is encrypted.
     */
    val currentChainKey: ByteArray,

    /**
     * The sequential index of the next message.
     * Used to synchronize the receiver's state with the sender's.
     */
    val messageNumber: Int
) {
    /**
     * Generated equals method to handle ByteArray comparison.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedScriptLog

        if (barcodeIdentifier != other.barcodeIdentifier) return false
        if (!masterKey.contentEquals(other.masterKey)) return false
        if (!currentChainKey.contentEquals(other.currentChainKey)) return false
        if (messageNumber != other.messageNumber) return false

        return true
    }

    /**
     * Generated hashCode method to handle ByteArray hashing.
     */
    override fun hashCode(): Int {
        var result = barcodeIdentifier.hashCode()
        result = 31 * result + masterKey.contentHashCode()
        result = 31 * result + currentChainKey.contentHashCode()
        result = 31 * result + messageNumber
        return result
    }
}
