package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The Data Access Object for Barcodes. The Scribe's direct interface with its archives.
 * The rituals here are concerned only with the sigils, not the people they belong to.
 */
@Dao
interface BarcodeDao {

    /**
     * Inserts a new barcode into the archive.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBarcode(barcode: Barcode): Long

    /**
     * Finds a barcode by its display name.
     */
    @Query("SELECT * FROM barcodes WHERE name = :name LIMIT 1")
    suspend fun getBarcodeByName(name: String): Barcode?

    /**
     * Retrieves all barcodes for a contact and ensures their values are decrypted.
     * Uses a LiveData transformation to decrypt on the fly.
     */
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>> {
        return getBarcodesForContactRaw(contactLookupKey).map { barcodes ->
            barcodes.onEach { it.decryptValue() }
        }
    }

    /**
     * Internal raw query for LiveData.
     */
    @Query("SELECT * FROM barcodes WHERE contactLookupKey = :contactLookupKey ORDER BY name ASC")
    fun getBarcodesForContactRaw(contactLookupKey: String): LiveData<List<Barcode>>

    /**
     * Retrieves a flow of all barcodes for a contact and ensures their values are decrypted.
     */
    fun getBarcodesForContactFlow(contactLookupKey: String): Flow<List<Barcode>> {
        return getBarcodesForContactRawFlow(contactLookupKey).map { barcodes ->
            barcodes.onEach { it.decryptValue() }
        }
    }

    /**
     * Internal raw query for Flow.
     */
    @Query("SELECT * FROM barcodes WHERE contactLookupKey = :contactLookupKey ORDER BY name ASC")
    fun getBarcodesForContactRawFlow(contactLookupKey: String): Flow<List<Barcode>>

    /**
     * Removes a barcode from existence.
     */
    @Delete
    suspend fun deleteBarcode(barcode: Barcode): Int

    /**
     * Updates an existing barcode.
     */
    @Update
    suspend fun updateBarcode(barcode: Barcode): Int

    /**
     * Retrieves a single barcode by ID.
     */
    @Query("SELECT * FROM barcodes WHERE id = :barcodeId")
    suspend fun getBarcode(barcodeId: Int): Barcode?

    /**
     * Indexed lookup of barcodes whose plaintext raw value produces the keyed search token
     * [hash] (see KeyManager.searchToken). Lets the scanner resolve a scan without
     * decrypting the whole table; callers still decrypt the (few) matches and confirm the
     * value to rule out collisions.
     */
    @Query("SELECT * FROM barcodes WHERE rawValueHash = :hash")
    suspend fun getBarcodesByRawValueHash(hash: String): List<Barcode>

    /**
     * Legacy rows created before the `rawValueHash` column existed. Decrypted once on first
     * scan to backfill the hash, after which they move onto the indexed fast path.
     */
    @Query("SELECT * FROM barcodes WHERE rawValueHash IS NULL")
    suspend fun getBarcodesWithoutHash(): List<Barcode>

    /**
     * Increments the usage counter for a barcode.
     * Used for non-ratchet, counter-based encryption schemes.
     */
    @Query("UPDATE barcodes SET counter = counter + 1 WHERE id = :barcodeId")
    suspend fun incrementCounter(barcodeId: Int): Int

    /**
     * Resets the counter to zero.
     */
    @Query("UPDATE barcodes SET counter = 0 WHERE id = :barcodeId")
    suspend fun resetCounter(barcodeId: Int): Int
}
