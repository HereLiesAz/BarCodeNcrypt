package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactDetailViewModel(application: Application, private val contactLookupKey: String) : ViewModel() {

    private val barcodeRepository: BarcodeRepository
    // private val associationRepository: AppContactAssociationRepository // Removed
    val barcodes: LiveData<List<Barcode>>
    // val associations: LiveData<List<AppContactAssociation>> // Removed

    init {
        val database = (application as com.hereliesaz.barcodencrypt.BarcodeApplication).database
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        barcodes = barcodeRepository.getBarcodesForContact(contactLookupKey)
    }

    fun createAndInsertBarcode(rawValue: String, password: String? = null, keyType: KeyType = KeyType.SINGLE_BARCODE) = viewModelScope.launch(Dispatchers.IO) {
        barcodeRepository.createAndInsertBarcode(contactLookupKey, rawValue, password, keyType)
    }

    fun createAndInsertBarcodeSequence(sequence: List<String>, password: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        barcodeRepository.createAndInsertBarcodeSequence(contactLookupKey, sequence, password)
    }

    // fun addAssociation(packageName: String) = viewModelScope.launch(Dispatchers.IO) { // Removed
    //     val association = AppContactAssociation(packageName = packageName, contactLookupKey = contactLookupKey)
    //     associationRepository.insert(association)
    // }

    // fun deleteAssociation(associationId: Int) = viewModelScope.launch(Dispatchers.IO) { // Removed
    //     associationRepository.delete(associationId)
    // }

    fun deleteBarcode(barcode: Barcode) = viewModelScope.launch(Dispatchers.IO) { 
        barcodeRepository.deleteBarcode(barcode)
    }
}

/**
 * A factory is required to pass the contactLookupKey to the ViewModel.
 */
class ContactDetailViewModelFactory(
    private val application: Application,
    private val contactLookupKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactDetailViewModel(application, contactLookupKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}