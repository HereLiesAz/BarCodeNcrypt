package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.data.ContactWithBarcodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The ViewModel for the Hierophant ([com.hereliesaz.barcodencrypt.MainActivity]).
 *
 * It acts as the interpreter between the UI and the data layer ([ContactRepository]).
 * It prepares and manages the data for the UI, surviving configuration changes and
 * ensuring the separation of concerns that keeps the machine's soul clean.
 *
 * @param application The application context, required to get a database instance.
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContactRepository

    /**
     * A [LiveData] list of all contacts and their barcodes, observed by the UI.
     */
    val allContacts: LiveData<List<ContactWithBarcodes>>

    /**
     * A LiveData to hold the state of the accessibility service.
     */
    val serviceStatus = MutableLiveData<Boolean>()

    /**
     * A LiveData to hold the state of the notification permission.
     */
    val notificationPermissionStatus = MutableLiveData<Boolean>()

    init {
        val database = (application as com.hereliesaz.barcodencrypt.BarcodeApplication).database
        val contactDao = database.contactDao()
        repository = ContactRepository(contactDao)
        allContacts = repository.allContacts
    }

    /**
     * Launches a coroutine in the ViewModel's scope to insert a new contact.
     *
     * @param contact The [Contact] object to be saved.
     */
    fun insertContact(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertContact(contact)
    }
}