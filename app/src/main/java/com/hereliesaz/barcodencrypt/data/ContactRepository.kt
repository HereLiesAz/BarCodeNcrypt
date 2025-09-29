package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * It abstracts the origin of the data, providing a clean API for the ViewModel to consume.
 *
 * @param contactDao The Data Access Object for contacts.
 */
class ContactRepository(private val contactDao: ContactDao) {

    /**
     * A direct, live feed of all contacts, complete with their sigils, from the database.
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

    suspend fun getContactWithBarcodesByLookupKeySync(contactLookupKey: String): ContactWithBarcodes? {
        return contactDao.getContactWithBarcodesByLookupKeySync(contactLookupKey)
    }

    fun getAllContactsWithBarcodesSync(): List<ContactWithBarcodes> { // Added this function
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

    fun getContactByLookupKey(lookupKey: String): kotlinx.coroutines.flow.Flow<Contact?> {
        return contactDao.getContactByLookupKey(lookupKey)
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact)
    }
}