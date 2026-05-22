package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val contactRepository: ContactRepository,
    private val encryptionManager: EncryptionManager,
) : ViewModel() {

    private val _decryptionResult = MutableLiveData<DecryptionResult>()
    val decryptionResult: LiveData<DecryptionResult> = _decryptionResult

    fun decryptMessage(scannedValue: String, encryptedMessage: String) {
        // TODO("v5 — Plan 2"): replace this entire flow with the v5 envelope path.
        // The new call is encryptionManager.decrypt(envelope, contactLookupKey,
        // barcode, password). Until then, surface a clear error to the user.
        viewModelScope.launch {
            _decryptionResult.postValue(
                DecryptionResult.Error("Decryption is unavailable until v5 crypto ships.")
            )
        }
    }

    fun resetDecryptionResult() {
        _decryptionResult.value = null
    }
}

sealed class DecryptionResult {
    data class Success(val plaintext: String) : DecryptionResult()
    data class Error(val message: String) : DecryptionResult()
}
