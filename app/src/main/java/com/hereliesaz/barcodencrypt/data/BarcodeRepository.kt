package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.crypto.KeyManager
import kotlinx.coroutines.flow.Flow

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * Its concerns have been narrowed; it deals only in barcodes.
 *
 * @param barcodeDao The Data Access Object for barcodes.
 */
class BarcodeRepository(private val barcodeDao: BarcodeDao) {

    /**
     * Retrieves a live feed of all barcodes for a given contact.
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
     * This method handles the encryption of the barcode value.
     *
     * @param contactLookupKey The lookup key of the contact.
     * @param rawValue The raw string value from the scanned barcode.
     * @param password An optional password to protect the key.
     */
    suspend fun createAndInsertBarcode(contactLookupKey: String, rawValue: String, password: String? = null, keyType: KeyType = KeyType.SINGLE_BARCODE) {
        val name = "Key ending in...${EncryptionManager.sha256(rawValue).takeLast(6)}"
        val (iv, encryptedValue) = KeyManager.encrypt(rawValue)
        val finalKeyType = if (keyType == KeyType.PASSWORD) KeyType.PASSWORD else if (password.isNullOrEmpty()) KeyType.SINGLE_BARCODE else KeyType.PASSWORD_PROTECTED_BARCODE
        val passwordHash = if (password.isNullOrEmpty()) null else EncryptionManager.sha256(password)
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

    suspend fun createAndInsertBarcodeSequence(contactLookupKey: String, sequence: List<String>, password: String? = null) {
        val rawValue = sequence.joinToString("")
        val name = "Key ending in...${EncryptionManager.sha256(rawValue).takeLast(6)}"
        val (iv, encryptedValue) = KeyManager.encrypt(rawValue)
        val keyType = if (password.isNullOrEmpty()) KeyType.BARCODE_SEQUENCE else KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE
        val passwordHash = if (password.isNullOrEmpty()) null else EncryptionManager.sha256(password)
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
        barcode?.decryptValue()
        return barcode
    }

    /**
     * Deletes a barcode from the.
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
}