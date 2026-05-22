package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.data.KeyType
import com.hereliesaz.barcodencrypt.util.Hashing
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

    /**
     * Attempts to decrypt a `~BCEv5~` envelope using the most recently-seen barcode
     * that matches [scannedValue].
     *
     * Until Plan 4 threads the originating contact through the scanner flow, we infer
     * the contact by looking up the first barcode whose stored hash matches the
     * scan. That is correct for `SINGLE_BARCODE` keys and sufficient as a v1.0 path.
     */
    fun decryptMessage(scannedValue: String, encryptedMessage: String) {
        viewModelScope.launch {
            val needle = Hashing.sha256(scannedValue)
            // TODO("Plan 4"): look up the right barcode via an indexed query instead
            // of iterating. For now the keys-per-contact count is small.
            val barcode = findBarcodeForScan(scannedValue, needle)
            if (barcode == null) {
                _decryptionResult.postValue(
                    DecryptionResult.Error("No key matches this barcode.")
                )
                return@launch
            }
            val contactLookupKey = barcode.contactLookupKey
            val password = if (barcode.keyType == KeyType.PASSWORD_PROTECTED_BARCODE) {
                // TODO("Plan 4"): prompt the user; for v1.0 the overlay supplies it.
                null
            } else {
                null
            }
            val plaintext = encryptionManager.decrypt(
                envelope = encryptedMessage,
                contactLookupKey = contactLookupKey,
                barcode = barcode,
                password = password,
            )
            if (plaintext != null) {
                _decryptionResult.postValue(DecryptionResult.Success(plaintext))
            } else {
                _decryptionResult.postValue(
                    DecryptionResult.Error("Decryption failed. Wrong key, expired, or already opened.")
                )
            }
        }
    }

    private suspend fun findBarcodeForScan(
        scannedValue: String,
        needleHash: String,
    ): com.hereliesaz.barcodencrypt.data.Barcode? {
        // Cheap heuristic: the legacy v4 path stored sha256(value).takeLast(6) as the
        // barcode's display name. So we look up by `getBarcodeByName("Key ending in...XXX")`.
        val tail = needleHash.takeLast(6)
        return barcodeRepository.getBarcodeByName("Key ending in...$tail")
    }

    fun resetDecryptionResult() {
        _decryptionResult.value = null
    }
}

sealed class DecryptionResult {
    data class Success(val plaintext: String) : DecryptionResult()
    data class Error(val message: String) : DecryptionResult()
}
