package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Data Access Object for Contact operations.
 * Handles relationships and direct table access.
 */
@Dao
interface ContactDao {

    /**
     * Inserts or replaces a contact.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    /**
     * Deletes a contact.
     */
    @Delete
    suspend fun deleteContact(contact: Contact): Int

    /**
     * Gets all contacts with their associated barcodes.
     * Returns LiveData for UI observation.
     */
    @Transaction
    @Query("SELECT * FROM contacts")
    fun getContactsWithBarcodes(): LiveData<List<ContactWithBarcodes>>

    /**
     * Synchronous fetch of all contacts with barcodes.
     */
    @Transaction
    @Query("SELECT * FROM contacts")
    fun getAllContactsWithBarcodesSync(): List<ContactWithBarcodes>

    /**
     * Gets a specific contact with barcodes by lookup key (LiveData).
     */
    @Transaction
    @Query("SELECT * FROM contacts WHERE lookupKey = :contactLookupKey")
    fun getContactWithBarcodesByLookupKey(contactLookupKey: String): LiveData<ContactWithBarcodes>

    /**
     * Gets a specific contact with barcodes by lookup key (Suspend/Sync).
     */
    @Transaction
    @Query("SELECT * FROM contacts WHERE lookupKey = :contactLookupKey")
    suspend fun getContactWithBarcodesByLookupKeySync(contactLookupKey: String): ContactWithBarcodes?

    /**
     * Gets list of all contacts (without barcodes).
     */
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): LiveData<List<Contact>>

    /**
     * Observes a specific contact by lookup key.
     */
    @Query("SELECT * FROM contacts WHERE lookupKey = :lookupKey")
    fun getContactByLookupKey(lookupKey: String): kotlinx.coroutines.flow.Flow<Contact?>

    /**
     * Updates contact details.
     */
    @Update
    suspend fun updateContact(contact: Contact)
}
