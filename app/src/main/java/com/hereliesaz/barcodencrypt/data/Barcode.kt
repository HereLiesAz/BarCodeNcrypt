package com.hereliesaz.barcodencrypt.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hereliesaz.barcodencrypt.crypto.KeyManager

/**
 * Defines the type of key generation used for this barcode.
 */
enum class KeyType {
    /**
     * Standard mode: The barcode value itself is the Master Key.
     */
    SINGLE_BARCODE,

    /**
     * Enhanced security: The Master Key is derived from a combination of the
     * barcode value and a user-provided password.
     */
    PASSWORD_PROTECTED_BARCODE,

    /**
     * The key is derived from a sequence of barcodes.
     */
    BARCODE_SEQUENCE,

    /**
     * The key is derived from a sequence of barcodes plus a password.
     */
    PASSWORD_PROTECTED_BARCODE_SEQUENCE
}

/**
 * Represents a stored barcode entity in the local database.
 *
 * This entity serves as the persistent record of a physical barcode that has been
 * assigned to a contact. Crucially, it stores the barcode's raw value in an
 * encrypted format (using Android Keystore-backed encryption) to prevent
 * plaintext leakage if the database file is compromised.
 *
 * @property id The unique primary key ID for this barcode entry (auto-generated).
 * @property contactLookupKey The persistent lookup key (from Android's ContactsContract) that links this barcode to a specific contact.
 * @property name A human-readable label for the barcode (e.g., "Gym Card", "Library Card").
 * @property encryptedValue The raw value of the barcode, encrypted using [KeyManager].
 * @property iv The Initialization Vector (IV) used during the encryption of [encryptedValue]. Required for decryption.
 * @property counter The message counter for the rolling key. Incremented for each encrypted message.
 * @property keyType The [KeyType] strategy used for this barcode (e.g., whether it requires a password).
 * @property passwordHash The SHA-256 hash of the password (if [keyType] is [KeyType.PASSWORD_PROTECTED_BARCODE]). Used for verification.
 * @property barcodeSequence A list of barcode values if this represents a sequence (future feature).
 */
@Entity(
    tableName = "barcodes",
    indices = [Index(value = ["contactLookupKey"])]
)
data class Barcode(
    /**
     * The auto-generated primary key.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * The foreign key linking to the [Contact]. This corresponds to [Contact.lookupKey].
     */
    val contactLookupKey: String,

    /**
     * The display name for this barcode.
     */
    val name: String,

    /**
     * The encrypted byte array of the barcode's raw value.
     * Stored as a BLOB in SQLite.
     */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val encryptedValue: ByteArray,

    /**
     * The initialization vector used to encrypt [encryptedValue].
     * Stored as a BLOB in SQLite.
     */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val iv: ByteArray,

    /**
     * A counter used in legacy or simple encryption schemes.
     * Defaults to 0L.
     */
    val counter: Long = 0L,

    /**
     * The strategy used for key derivation.
     * Defaults to [KeyType.SINGLE_BARCODE].
     */
    val keyType: KeyType = KeyType.SINGLE_BARCODE,

    /**
     * The hash of the associated password, if any.
     * Null if not password protected.
     */
    val passwordHash: String? = null,

    /**
     * A list of constituent barcode strings if this is a composite key.
     */
    val barcodeSequence: List<String>? = null
) {
    /**
     * The decrypted, plaintext value of the barcode.
     * This field is NOT stored in the database (@Ignore) and is only populated
     * temporarily in memory when [decryptValue] is called.
     */
    @Ignore
    var value: String = ""
        private set

    /**
     * Decrypts the [encryptedValue] using the stored [iv] and the system [KeyManager].
     * Populates the transient [value] property.
     *
     * This method must be called explicitly after retrieving the entity from the database
     * if the plaintext value is needed.
     */
    fun decryptValue() {
        value = KeyManager.decrypt(iv, encryptedValue)
    }

    /**
     * Checks equality based on all persisted fields.
     * Overridden because [encryptedValue] and [iv] are ByteArrays.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Barcode

        if (id != other.id) return false
        if (contactLookupKey != other.contactLookupKey) return false
        if (name != other.name) return false
        if (!encryptedValue.contentEquals(other.encryptedValue)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (counter != other.counter) return false

        return true
    }

    /**
     * Generates a hash code including array contents.
     */
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + contactLookupKey.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + encryptedValue.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + counter.hashCode()
        return result
    }
}
