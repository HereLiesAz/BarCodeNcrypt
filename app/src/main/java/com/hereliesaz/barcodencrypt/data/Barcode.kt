package com.hereliesaz.barcodencrypt.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hereliesaz.barcodencrypt.crypto.KeyManager

enum class KeyType {
    SINGLE_BARCODE,
    PASSWORD_PROTECTED_BARCODE,
}

/**
 * A record of a single cryptographic sigil. A barcode.
 * It is forever bound to a contact, not by a fragile integer ID, but by their persistent,
 * cryptic `lookupKey` from the master Android `ContactsContract`.
 *
 * @param id The primary key, a meaningless number for the Scribe's internal use.
 * @param contactLookupKey The persistent key that identifies a contact in the Android system. This is our link to a real person.
 * @param name A non-secret, human-readable name for this key, derived from a hash of its value.
 * @param encryptedValue The encrypted IKM, protected by the Android Keystore.
 * @param iv The initialization vector used for encrypting the [encryptedValue].
 * @param counter The message counter for the rolling key. Incremented for each encrypted message.
 * @param keyType The type of key, used to determine how to derive the IKM.
 * @param passwordHash The hash of the password, if the key is password-protected.
 * @param barcodeSequence The list of barcode values for a sequence key.
 */
@Entity(
    tableName = "barcodes",
    indices = [Index(value = ["contactLookupKey"])]
)
data class Barcode(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val contactLookupKey: String,
    val name: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val encryptedValue: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val iv: ByteArray,
    val counter: Long = 0L,
    val keyType: KeyType = KeyType.SINGLE_BARCODE,
    val passwordHash: String? = null,
    val barcodeSequence: List<String>? = null
) {
    /**
     * The decrypted, raw, sacred text of the barcode itself. This is the Initial Keying Material (IKM).
     * This field is populated on-demand after being retrieved from the database.
     */
    @Ignore
    var value: String = ""
        private set

    /**
     * Decrypts the [encryptedValue] and populates the transient [value] field.
     * This should be called after the entity is retrieved from the database.
     */
    fun decryptValue() {
        value = KeyManager.decrypt(iv, encryptedValue)
    }

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