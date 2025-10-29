package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EncryptionOverlayViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val barcodeRepository: BarcodeRepository,
    private val encryptionManager: EncryptionManager
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _barcodes = MutableStateFlow<List<Barcode>>(emptyList())
    val barcodes: StateFlow<List<Barcode>> = _barcodes

    init {
        viewModelScope.launch {
            _contacts.value = contactRepository.getAllContactsWithBarcodesSync().map { it.contact }
        }
    }

    fun getBarcodesForContact(contact: Contact) {
        viewModelScope.launch {
            _barcodes.value = barcodeRepository.getBarcodesForContactFlow(contact.lookupKey).first()
        }
    }

    fun encrypt(
        text: String,
        barcode: Barcode,
        ttl: Long?,
        openCount: Int?,
        callback: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val encryptedText = encryptionManager.encrypt(
                plaintext = text,
                barcode = barcode,
                ttl = ttl,
                openCount = openCount
            )
            callback(encryptedText)
        }
    }
}
