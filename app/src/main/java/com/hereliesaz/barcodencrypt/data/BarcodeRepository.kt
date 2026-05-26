package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import com.hereliesaz.barcodencrypt.crypto.KeyManager
import com.hereliesaz.barcodencrypt.util.Hashing
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * Its concerns have been narrowed; it deals only in barcodes.
 *
 * This repository handles the business logic of encrypting barcode values before they are
 * persisted to the database, ensuring that sensitive key material is never stored in plaintext.
 *
 * @param barcodeDao The Data Access Object for barcodes.
 */
@Singleton
class BarcodeRepository @Inject constructor(private val barcodeDao: BarcodeDao) {

    /**
     * Retrieves a live feed of all barcodes for a given contact.
     *
     * The DAO handles the on-the-fly decryption of the values as they are emitted.
     *
     * @param contactLookupKey The persistent key for the contact.
     * @return A [LiveData] list of barcodes.
     */
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>> {
        return barcodeDao.getBarcodesForContact(contactLookupKey)
    }

    /**
     * Retrieves a flow of all barcodes for a given contact.
     *
     * @param contactLookupKey The persistent key for the contact.
     * @return A [Flow] of a list of barcodes.
     */
    fun getBarcodesForContactFlow(contactLookupKey: String): Flow<List<Barcode>> {
        return barcodeDao.getBarcodesForContactFlow(contactLookupKey)
    }

    /**
     * Creates and inserts a new barcode into the database.
     *
     * **Security Logic:**
     * 1. Generates a safe display name based on a hash of the raw value.
     * 2. Encrypts the raw barcode value using [KeyManager] (AES-GCM backed by Keystore).
     * 3. Calculates a password hash if a password is provided.
     * 4. Constructs the [Barcode] entity with the encrypted payload and IV.
     *
     * @param contactLookupKey The lookup key of the contact.
     * @param rawValue The raw string value from the scanned barcode.
     * @param password An optional password to protect the key.
     * @param keyType The type of key (Single vs Password protected).
     */
    suspend fun createAndInsertBarcode(contactLookupKey: String, rawValue: String, password: String? = null, keyType: KeyType = KeyType.SINGLE_BARCODE) {
        // Generate a non-sensitive name using the last 6 chars of the SHA256 hash
        val name = "Key ending in...${Hashing.sha256(rawValue).takeLast(6)}"

        // Encrypt the sensitive raw value
        val (iv, encryptedValue) = KeyManager.encrypt(rawValue)

        // Determine the final key type based on inputs
        // If a password is provided, upgrade the key type to PASSWORD_PROTECTED_BARCODE
        val finalKeyType = if (!password.isNullOrEmpty()) KeyType.PASSWORD_PROTECTED_BARCODE else keyType

        // Hash the password if present (never store plaintext password)
        val passwordHash = if (password.isNullOrEmpty()) null else Hashing.sha256(password)

        val barcode = Barcode(
            contactLookupKey = contactLookupKey,
            name = name,
            encryptedValue = encryptedValue,
            iv = iv,
            keyType = finalKeyType,
            passwordHash = passwordHash
        )
        barcodeDao.insertBarcode(barcode)
    }

    /**
     * Creates and inserts a sequence of barcodes (composite key).
     *
     * @param contactLookupKey The contact ID.
     * @param sequence The list of barcode strings.
     * @param password Optional password.
     */
    suspend fun createAndInsertBarcodeSequence(contactLookupKey: String, sequence: List<String>, password: String? = null) {
        // Concatenate sequence for encryption
        val rawValue = sequence.joinToString("")
        val name = "Key ending in...${Hashing.sha256(rawValue).takeLast(6)}"

        // Encrypt combined value
        val (iv, encryptedValue) = KeyManager.encrypt(rawValue)

        val keyType = if (password.isNullOrEmpty()) KeyType.BARCODE_SEQUENCE else KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE
        val passwordHash = if (password.isNullOrEmpty()) null else Hashing.sha256(password)

        val barcode = Barcode(
            contactLookupKey = contactLookupKey,
            name = name,
            encryptedValue = encryptedValue,
            iv = iv,
            keyType = keyType,
            passwordHash = passwordHash,
            barcodeSequence = sequence
        )
        barcodeDao.insertBarcode(barcode)
    }


    /**
     * Seeks a specific barcode by its name from the Scribe's records.
     *
     * @param name The name of the barcode.
     * @return The [Barcode] if it exists, otherwise null.
     */
    suspend fun getBarcodeByName(name: String): Barcode? {
        val barcode = barcodeDao.getBarcodeByName(name)
        // Decrypt on retrieval
        barcode?.decryptValue()
        return barcode
    }

    /**
     * Deletes a barcode from the database.
     * @param barcode The barcode to be deleted.
     */
    suspend fun deleteBarcode(barcode: Barcode) {
        barcodeDao.deleteBarcode(barcode)
    }

    /**
     * Updates a barcode in the database.
     * @param barcode The barcode to update.
     */
    suspend fun updateBarcode(barcode: Barcode) {
        barcodeDao.updateBarcode(barcode)
    }

    /**
     * Gets a single barcode by its ID.
     * @param barcodeId The ID of the barcode.
     * @return The [Barcode] object or null if not found.
     */
    suspend fun getBarcode(barcodeId: Int): Barcode? {
        val barcode = barcodeDao.getBarcode(barcodeId)
        barcode?.decryptValue()
        return barcode
    }

    /**
     * Finds every stored barcode whose decrypted value exactly equals [rawValue].
     *
     * Used by the scanner to resolve which key(s) a freshly-scanned barcode unlocks.
     * Matching on the real decrypted value (rather than a 6-char hash suffix of the
     * display name) is collision-free; a barcode shared by multiple contacts yields
     * multiple candidates, and the caller tries each. Rows that fail to decrypt (e.g.
     * corrupt ciphertext) are skipped rather than aborting the search.
     */
    suspend fun findBarcodesByRawValue(rawValue: String): List<Barcode> =
        barcodeDao.getAllBarcodes().filter { barcode ->
            runCatching {
                barcode.decryptValue()
                barcode.value == rawValue
            }.getOrDefault(false)
        }
}
