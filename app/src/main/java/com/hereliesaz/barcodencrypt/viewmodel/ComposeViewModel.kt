package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val contactRepository: ContactRepository,
    private val encryptionManager: EncryptionManager,
) : ViewModel() {

    private var contactBarcodeJob: Job? = null

    private val _barcodesForSelectedContact = MutableStateFlow<List<Barcode>>(emptyList())
    val barcodesForSelectedContact = _barcodesForSelectedContact.asStateFlow()

    private var selectedContact: Contact? = null

    fun selectContact(contactLookupKey: String) {
        contactBarcodeJob?.cancel()
        contactBarcodeJob = viewModelScope.launch {
            selectedContact = contactRepository.getContactByLookupKey(contactLookupKey).firstOrNull()
            barcodeRepository.getBarcodesForContactFlow(contactLookupKey).collect { barcodes ->
                _barcodesForSelectedContact.value = barcodes
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        contactBarcodeJob?.cancel()
    }

    suspend fun encryptMessage(
        plaintext: String,
        barcode: Barcode,
        options: List<String>,
        password: String? = null,
        maxAttempts: Int = 0,
    ): String? {
        // TODO("v5 — Plan 2"): rebuild this against RatchetEngine. The new call site
        // will be encryptionManager.encrypt(plaintext, contactLookupKey, barcode,
        // password, ttlMs, openMax).
        val contact = selectedContact ?: return null
        return encryptionManager.encrypt(
            plaintext = plaintext,
            barcode = barcode,
            password = password,
            ttl = null,
            openCount = if (maxAttempts > 0) maxAttempts else null,
        )
    }
}
