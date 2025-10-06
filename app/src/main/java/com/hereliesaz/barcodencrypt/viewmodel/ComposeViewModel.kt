package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ComposeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BarcodeRepository
    private var contactBarcodeJob: Job? = null

    private val _barcodesForSelectedContact = MutableStateFlow<List<Barcode>>(emptyList())
    val barcodesForSelectedContact = _barcodesForSelectedContact.asStateFlow()

    init {
        val barcodeDao = AppDatabase.getDatabase(application).barcodeDao()
        repository = BarcodeRepository(barcodeDao)
    }

    fun selectContact(contactLookupKey: String) {
        contactBarcodeJob?.cancel()
        contactBarcodeJob = viewModelScope.launch {
            repository.getBarcodesForContactFlow(contactLookupKey).collect { barcodes ->
                _barcodesForSelectedContact.value = barcodes
            }
        }
    }

    suspend fun encryptMessage(
        plaintext: String,
        barcode: Barcode,
        options: List<String>,
        password: String? = null,
        maxAttempts: Int = 0
    ): String? {
        // Get the freshest barcode state from DB to ensure counter is correct
        val freshBarcode = repository.getBarcode(barcode.id) ?: return null

        // Increment the counter and update the database
        val updatedBarcode = freshBarcode.copy(counter = freshBarcode.counter + 1)
        repository.updateBarcode(updatedBarcode)

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