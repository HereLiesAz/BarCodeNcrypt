package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.Index // Import for Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["lookupKey"], unique = true)] // Added unique index
)
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lookupKey: String,
    val name: String,

    // V3 Double Ratchet State
    val rootKey: ByteArray? = null,
    val sendingChainKey: ByteArray? = null,
    val receivingChainKey: ByteArray? = null,
    val sendingMessageNumber: Int = 0,
    val receivingMessageNumber: Int = 0,
    val previousSendingChainMessageNumber: Int = 0,
    val dhKeyPair: ByteArray? = null, // Storing serialized KeysetHandle
    val dhRemotePublicKey: ByteArray? = null, // Storing serialized KeysetHandle
    val skippedMessageKeys: String? = null // JSON representation of skipped keys
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contact

        if (id != other.id) return false
        if (lookupKey != other.lookupKey) return false
        if (name != other.name) return false
        if (rootKey != null) {
            if (other.rootKey == null) return false
            if (!rootKey.contentEquals(other.rootKey)) return false
        } else if (other.rootKey != null) return false
        if (sendingChainKey != null) {
            if (other.sendingChainKey == null) return false
            if (!sendingChainKey.contentEquals(other.sendingChainKey)) return false
        } else if (other.sendingChainKey != null) return false
        if (receivingChainKey != null) {
            if (other.receivingChainKey == null) return false
            if (!receivingChainKey.contentEquals(other.receivingChainKey)) return false
        } else if (other.receivingChainKey != null) return false
        if (sendingMessageNumber != other.sendingMessageNumber) return false
        if (receivingMessageNumber != other.receivingMessageNumber) return false
        if (previousSendingChainMessageNumber != other.previousSendingChainMessageNumber) return false
        if (dhKeyPair != null) {
            if (other.dhKeyPair == null) return false
            if (!dhKeyPair.contentEquals(other.dhKeyPair)) return false
        } else if (other.dhKeyPair != null) return false
        if (dhRemotePublicKey != null) {
            if (other.dhRemotePublicKey == null) return false
            if (!dhRemotePublicKey.contentEquals(other.dhRemotePublicKey)) return false
        } else if (other.dhRemotePublicKey != null) return false
        if (skippedMessageKeys != other.skippedMessageKeys) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + lookupKey.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (rootKey?.contentHashCode() ?: 0)
        result = 31 * result + (sendingChainKey?.contentHashCode() ?: 0)
        result = 31 * result + (receivingChainKey?.contentHashCode() ?: 0)
        result = 31 * result + sendingMessageNumber
        result = 31 * result + receivingMessageNumber
        result = 31 * result + previousSendingChainMessageNumber
        result = 31 * result + (dhKeyPair?.contentHashCode() ?: 0)
        result = 31 * result + (dhRemotePublicKey?.contentHashCode() ?: 0)
        result = 31 * result + (skippedMessageKeys?.hashCode() ?: 0)
        return result
    }
}
