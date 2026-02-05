package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a contact within the BarcodeNcrypt application.
 *
 * This entity mirrors a contact from the device's address book but is stored locally
 * to associate BarcodeNcrypt-specific data (like assigned barcodes) with the person.
 *
 * @property id The auto-generated local database ID.
 * @property lookupKey The stable, unique identifier for the contact provided by Android's ContactsContract API.
 *                     This key persists even if the contact's name changes or other details are updated.
 * @property name The display name of the contact (cached for display purposes).
 * @property dhKeyPair Local Diffie-Hellman Key Pair (Private/Public) for V3 ratchet.
 * @property dhRemotePublicKey Remote public key for V3 ratchet.
 * @property rootKey Double Ratchet Root Key.
 * @property sendingChainKey Current sending chain key.
 * @property receivingChainKey Current receiving chain key.
 * @property sendingMessageNumber Counter for sent messages.
 * @property receivingMessageNumber Counter for received messages.
 * @property previousSendingChainMessageNumber Checkpoint for ratchet steps.
 * @property skippedMessageKeys Map of skipped keys for out-of-order decryption.
 */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["lookupKey"], unique = true)]
)
data class Contact(
    /**
     * The primary key for the local database table.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * The unique lookup key from the system Contacts provider.
     * This serves as the stable link to the user's phone book.
     */
    val lookupKey: String,

    /**
     * The contact's display name.
     */
    val name: String,

    // V3 Double Ratchet State fields (Optional/Nullable)
    val dhKeyPair: ByteArray? = null,
    val dhRemotePublicKey: ByteArray? = null,
    val rootKey: ByteArray? = null,
    val sendingChainKey: ByteArray? = null,
    val receivingChainKey: ByteArray? = null,
    val sendingMessageNumber: Int = 0,
    val receivingMessageNumber: Int = 0,
    val previousSendingChainMessageNumber: Int = 0,
    val skippedMessageKeys: String? = null // Serialized map
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contact

        if (id != other.id) return false
        if (lookupKey != other.lookupKey) return false
        if (name != other.name) return false
        if (dhKeyPair != null) {
            if (other.dhKeyPair == null) return false
            if (!dhKeyPair.contentEquals(other.dhKeyPair)) return false
        } else if (other.dhKeyPair != null) return false
        if (dhRemotePublicKey != null) {
            if (other.dhRemotePublicKey == null) return false
            if (!dhRemotePublicKey.contentEquals(other.dhRemotePublicKey)) return false
        } else if (other.dhRemotePublicKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + lookupKey.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (dhKeyPair?.contentHashCode() ?: 0)
        result = 31 * result + (dhRemotePublicKey?.contentHashCode() ?: 0)
        return result
    }
}
