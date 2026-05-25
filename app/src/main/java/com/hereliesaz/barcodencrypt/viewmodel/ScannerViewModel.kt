package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

    /**
     * Attempts to decrypt a `~BCEv6~` envelope with the freshly-scanned [scannedValue].
     *
     * We resolve every stored barcode whose decrypted value equals the scan (a barcode
     * shared across contacts can match more than one) and try each in turn. The first
     * candidate that decrypts wins; only the correct contact's ratchet state will, so a
     * mismatch simply moves on. Runs off the main thread because the underlying Argon2
     * bootstrap is CPU/memory heavy.
     */
    fun decryptMessage(scannedValue: String, encryptedMessage: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val candidates = barcodeRepository.findBarcodesByRawValue(scannedValue)
            if (candidates.isEmpty()) {
                _decryptionResult.postValue(
                    DecryptionResult.Error("No key matches this barcode.")
                )
                return@launch
            }
            for (barcode in candidates) {
                val plaintext = encryptionManager.decrypt(
                    envelope = encryptedMessage,
                    contactLookupKey = barcode.contactLookupKey,
                    barcode = barcode,
                    // TODO("Plan 4"): prompt for the password on password-protected keys;
                    // the overlay supplies it on the overlay flow.
                    password = null,
                )
                if (plaintext != null) {
                    _decryptionResult.postValue(DecryptionResult.Success(plaintext))
                    return@launch
                }
            }
            _decryptionResult.postValue(
                DecryptionResult.Error("Decryption failed. Wrong key, expired, or already opened.")
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
