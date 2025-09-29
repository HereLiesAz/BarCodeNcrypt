package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.BarcodeApplication
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.*
import com.hereliesaz.barcodencrypt.util.MessageParser
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val barcodeRepository: BarcodeRepository
    private val contactRepository: ContactRepository

    private val _decryptionResult = MutableLiveData<DecryptionResult>()
    val decryptionResult: LiveData<DecryptionResult> = _decryptionResult

    init {
        val database = (application as BarcodeApplication).database
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        contactRepository = ContactRepository(database.contactDao())
    }

    fun decryptMessage(scannedValue: String, encryptedMessage: String) {
        viewModelScope.launch {
            val v3Message = MessageParser.parseV3Message(encryptedMessage)
            if (v3Message != null) {
                // Handle V3 Decryption
                val contact = contactRepository.getContactByLookupKey(v3Message.contactLookupKey).firstOrNull()
                if (contact == null) {
                    _decryptionResult.postValue(DecryptionResult.Error("Contact not found for this message."))
                    return@launch
                }
                // In a real scenario, you'd find the correct barcode IKM to bootstrap the session,
                // but for now, we assume the session is already initialized.
                val (updatedContact, decrypted) = EncryptionManager.decryptV3(encryptedMessage, contact)
                if (decrypted != null) {
                    contactRepository.updateContact(updatedContact)
                    _decryptionResult.postValue(DecryptionResult.Success(decrypted.plaintext))
                } else {
                    _decryptionResult.postValue(DecryptionResult.Error("Decryption failed. Incorrect key or corrupted message."))
                }
                return@launch
            }

            val v4Message = MessageParser.parseV4Message(encryptedMessage)
            if (v4Message != null) {
                // Handle V4 Decryption
                val barcode = barcodeRepository.getBarcodeByName(v4Message.keyName)
                if (barcode == null) {
                    _decryptionResult.postValue(DecryptionResult.Error("Key not found for this message."))
                    return@launch
                }
                // For V4, the scanned value is the IKM (or part of it if password-protected)
                val ikm = if (barcode.keyType == KeyType.PASSWORD_PROTECTED_BARCODE) {
                    EncryptionManager.sha256(scannedValue + (barcode.passwordHash ?: ""))
                } else {
                    scannedValue
                }

                val decrypted = EncryptionManager.decrypt(encryptedMessage, ikm)
                if (decrypted != null) {
                    _decryptionResult.postValue(DecryptionResult.Success(decrypted.plaintext))
                } else {
                    _decryptionResult.postValue(DecryptionResult.Error("Decryption failed. Incorrect key or corrupted message."))
                }
                return@launch
            }

            _decryptionResult.postValue(DecryptionResult.Error("Invalid or unsupported message format."))
        }
    }

    fun resetDecryptionResult() {
        _decryptionResult.value = null
    }
}

class ScannerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScannerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

sealed class DecryptionResult {
    data class Success(val plaintext: String) : DecryptionResult()
    data class Error(val message: String) : DecryptionResult()
}