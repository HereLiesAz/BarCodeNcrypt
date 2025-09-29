package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.BarcodeApplication
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ComposeViewModel(application: Application) : AndroidViewModel(application) {

    private val barcodeRepository: BarcodeRepository
    private val contactRepository: ContactRepository

    private val _barcodesForSelectedContact = MutableLiveData<List<Barcode>>()
    val barcodesForSelectedContact: LiveData<List<Barcode>> = _barcodesForSelectedContact

    private var selectedContact: Contact? = null

    private var currentContactLookupKey: String? = null
    private var barcodesLiveData: LiveData<List<Barcode>>? = null

    init {
        val database = (application as BarcodeApplication).database
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        contactRepository = ContactRepository(database.contactDao())
    }

    fun selectContact(contactLookupKey: String) {
        if (contactLookupKey == currentContactLookupKey) return

        // Remove the observer from the old LiveData object
        barcodesLiveData?.removeObserver(barcodeObserver)

        currentContactLookupKey = contactLookupKey
        viewModelScope.launch {
            selectedContact = contactRepository.getContactByLookupKey(contactLookupKey).firstOrNull()
        }

        barcodesLiveData = barcodeRepository.getBarcodesForContact(contactLookupKey)
        barcodesLiveData?.observeForever(barcodeObserver)
    }

    private val barcodeObserver: (List<Barcode>) -> Unit = { barcodes ->
        _barcodesForSelectedContact.postValue(barcodes)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up the observer when the ViewModel is destroyed
        barcodesLiveData?.removeObserver(barcodeObserver)
    }

    suspend fun encryptMessage(
        plaintext: String,
        barcode: Barcode,
        options: List<String>,
        password: String? = null,
        maxAttempts: Int = 0
    ): String? {
        val contact = selectedContact
        // If a secure v3 channel is established, use it.
        if (contact?.dhRemotePublicKey != null) {
            val (updatedContact, encryptedMessage) = EncryptionManager.encryptV3(
                plaintext = plaintext,
                contact = contact,
                options = options,
                keyName = barcode.name
            )
            // Save the updated contact state (new ratchet keys)
            contactRepository.updateContact(updatedContact)
            selectedContact = updatedContact // Update the local state
            return encryptedMessage
        }

        // Fallback to v4 encryption
        // Get the freshest barcode state from DB to ensure counter is correct
        val freshBarcode = barcodeRepository.getBarcode(barcode.id) ?: return null

        // Increment the counter and update the database
        val updatedBarcode = freshBarcode.copy(counter = freshBarcode.counter + 1)
        barcodeRepository.updateBarcode(updatedBarcode)

        val ikm = EncryptionManager.getIkm(updatedBarcode, password)

        // Encrypt with the new counter value
        return EncryptionManager.encrypt(
            plaintext = plaintext,
            ikm = ikm,
            keyName = updatedBarcode.name,
            counter = updatedBarcode.counter,
            options = options,
            maxAttempts = maxAttempts
        )
    }
}