package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.KeyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contactLookupKey: String =
        checkNotNull(savedStateHandle["contactLookupKey"]) { "contactLookupKey nav arg required" }

    val barcodes: LiveData<List<Barcode>> = barcodeRepository.getBarcodesForContact(contactLookupKey)

    fun createAndInsertBarcode(
        rawValue: String,
        password: String? = null,
        keyType: KeyType = KeyType.SINGLE_BARCODE,
    ) = viewModelScope.launch(Dispatchers.IO) {
        barcodeRepository.createAndInsertBarcode(contactLookupKey, rawValue, password, keyType)
    }

    fun createAndInsertBarcodeSequence(sequence: List<String>, password: String? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            barcodeRepository.createAndInsertBarcodeSequence(contactLookupKey, sequence, password)
        }

    fun deleteBarcode(barcode: Barcode) = viewModelScope.launch(Dispatchers.IO) {
        barcodeRepository.deleteBarcode(barcode)
    }
}
