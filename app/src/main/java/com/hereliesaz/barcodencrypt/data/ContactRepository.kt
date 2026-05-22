package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * It abstracts the origin of the data, providing a clean API for the ViewModel to consume.
 *
 * @param contactDao The Data Access Object for contacts.
 */
@Singleton
class ContactRepository @Inject constructor(private val contactDao: ContactDao) {

    /**
     * A direct, live feed of all contacts, complete with their sigils, from the database.
     * Exposed as LiveData for UI observation.
     */
    val allContacts: LiveData<List<ContactWithBarcodes>> = contactDao.getContactsWithBarcodes()

    /**
     * Retrieves a single contact with their associated barcodes using their lookup key.
     * @param contactLookupKey The lookup key of the contact to retrieve.
     * @return A LiveData object holding the contact data.
     */
    fun getContactWithBarcodesByLookupKey(contactLookupKey: String): LiveData<ContactWithBarcodes> {
        return contactDao.getContactWithBarcodesByLookupKey(contactLookupKey)
    }

    /**
     * Synchronous retrieval of contact data (Suspension function).
     */
    suspend fun getContactWithBarcodesByLookupKeySync(contactLookupKey: String): ContactWithBarcodes? {
        return contactDao.getContactWithBarcodesByLookupKeySync(contactLookupKey)
    }

    /**
     * Synchronous retrieval of all contacts.
     * Note: Should be called on a background thread.
     */
    fun getAllContactsWithBarcodesSync(): List<ContactWithBarcodes> {
        return contactDao.getAllContactsWithBarcodesSync()
    }

    /**
     * Inserts a new contact into the database via a coroutine.
     *
     * @param contact The [Contact] to insert.
     */
    suspend fun insertContact(contact: Contact) {
        contactDao.insertContact(contact)
    }

    /**
     * Deletes a contact from the database.
     * @param contact The contact to be deleted.
     */
    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
    }

    /**
     * Observes contact updates via Flow.
     */
    fun getContactByLookupKey(lookupKey: String): kotlinx.coroutines.flow.Flow<Contact?> {
        return contactDao.getContactByLookupKey(lookupKey)
    }

    /**
     * Updates an existing contact record.
     */
    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact)
    }
}
