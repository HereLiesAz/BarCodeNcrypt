package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import android.util.Log // Added
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeDao
import com.hereliesaz.barcodencrypt.data.KeyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TryItViewModel(application: Application) : AndroidViewModel(application) {

    enum class Stage {
        INITIAL,
        MESSAGE_GENERATED,
        DECRYPTED
    }

    private val _encryptedMessage = MutableLiveData<String>()
    val encryptedMessage: LiveData<String> = _encryptedMessage

    private val _decryptedMessage = MutableLiveData<String?>()
    val decryptedMessage: LiveData<String?> = _decryptedMessage

    private val _stage = MutableLiveData(Stage.INITIAL)
    val stage: LiveData<Stage> = _stage


    private val barcodeDao: BarcodeDao = AppDatabase.getDatabase(application).barcodeDao()

    private val _demoKeyResult = MutableLiveData<BarcodeResult>()
    val demoKeyResult: LiveData<BarcodeResult> = _demoKeyResult

    val MOCK_PASSWORD = "password123"
    val DEMO_KEY_NAME = "DemoKey"

    init {
        checkDemoKey()
    }

    fun generateMessage() {
        viewModelScope.launch {
            val keyResult = demoKeyResult.value
            if (keyResult is BarcodeResult.Success) {
                val encrypted = encryptMessageForDemo("This is a test message.", keyResult.barcode)
                if (encrypted != null) {
                    _encryptedMessage.postValue(encrypted)
                    _stage.postValue(Stage.MESSAGE_GENERATED)
                } else {
                    // Handle encryption failure
                }
            } else {
                // Handle key not being ready
            }
        }
    }

    fun onMessageDecrypted(message: String) {
        _decryptedMessage.value = message
        _stage.value = Stage.DECRYPTED
    }

    fun reset() {
        _stage.value = Stage.INITIAL
        _encryptedMessage.value = ""
        _decryptedMessage.value = null
    }

    fun checkDemoKey() {
        _demoKeyResult.value = BarcodeResult.Loading
        viewModelScope.launch {
            val key = try {
                withContext(Dispatchers.IO) { // Explicitly use Dispatchers.IO for the DB call
                    barcodeDao.getBarcodeByName(DEMO_KEY_NAME)
                }
            } catch (e: Exception) {
                _demoKeyResult.postValue(BarcodeResult.Error("Error fetching key '$DEMO_KEY_NAME': ${e.message}"))
                null // Ensure key is null if an exception occurred
            }

            if (key != null) {
                if (key.keyType == KeyType.PASSWORD_PROTECTED_BARCODE || key.keyType == KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE) {
                    _demoKeyResult.postValue(BarcodeResult.Success(key))
                } else {
                    _demoKeyResult.postValue(BarcodeResult.Error("'$DEMO_KEY_NAME' found, but it's not password-protected. Please re-create it as a password-protected key with password '$MOCK_PASSWORD'."))
                }
            } else {
                // If not already set to an error by the catch block, set the "not found" error.
                if (_demoKeyResult.value !is BarcodeResult.Error) {
                    _demoKeyResult.postValue(BarcodeResult.Error("Key '$DEMO_KEY_NAME' not found. Please create it first."))
                }
            }
        }
    }

    suspend fun encryptMessageForDemo(plainText: String, barcode: Barcode): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Derive IKM using the barcode object and the known mock password
                val ikm = EncryptionManager.getIkm(barcode = barcode, password = MOCK_PASSWORD)
                // It's good practice to check if ikm is null or empty. getIkm should throw if password is required and null,
                // but MOCK_PASSWORD is hardcoded. This check is more for cases where barcode.value might be empty.
                if (ikm.isNullOrEmpty() && (barcode.keyType != KeyType.PASSWORD)) { // Password key type can have empty IKM if barcode value itself is empty string.
                    Log.e("TryItViewModel", "IKM derivation failed or resulted in empty IKM for key: ${barcode.name} of type ${barcode.keyType}")
                    return@withContext null
                }

                // Step 2: Call encrypt with the derived IKM and other necessary parameters
                EncryptionManager.encrypt(
                    plaintext = plainText,
                    ikm = ikm,
                    keyName = barcode.name,
                    counter = 0L, // Using 0L for the demo simulation
                    options = emptyList(),
                    maxAttempts = 0 // No attempt limit for this simulation
                )
            } catch (e: Exception) {
                Log.e("TryItViewModel", "Error in encryptMessageForDemo for key '${barcode.name}': ${e.message}", e)
                null
            }
        }
    }
}

sealed class BarcodeResult {
    object Loading : BarcodeResult()
    data class Success(val barcode: Barcode) : BarcodeResult()
    data class Error(val message: String) : BarcodeResult()
}
