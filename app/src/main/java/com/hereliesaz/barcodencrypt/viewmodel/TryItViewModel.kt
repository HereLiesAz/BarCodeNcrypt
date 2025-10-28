package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import android.util.Log
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

    private val MOCK_PASSWORD = "password123"
    private val barcodeDao: BarcodeDao = AppDatabase.getDatabase(application, MOCK_PASSWORD).barcodeDao()

    private val _encryptedMessage = MutableLiveData<String>()
    val encryptedMessage: LiveData<String> = _encryptedMessage

    private val _decryptedMessage = MutableLiveData<String?>()
    val decryptedMessage: LiveData<String?> = _decryptedMessage

    private val _stage = MutableLiveData(Stage.INITIAL)
    val stage: LiveData<Stage> = _stage

    private val originalMessage = "This is a secret message!"
    private val DEMO_KEY_NAME = "DemoKey"

    fun generateMessage() {
        viewModelScope.launch {
            val demoKey = withContext(Dispatchers.IO) {
                barcodeDao.getBarcodeByName(DEMO_KEY_NAME)
            }
            if (demoKey != null) {
                val encrypted = encryptMessageForDemo(originalMessage, demoKey)
                if (encrypted != null) {
                    _encryptedMessage.postValue(encrypted)
                    _stage.postValue(Stage.MESSAGE_GENERATED)
                } else {
                    // TODO: Handle encryption error
                }
            } else {
                // TODO: Handle demo key not found
            }
        }
    }

    fun onMessageDecrypted(decryptedText: String) {
        _decryptedMessage.value = decryptedText
        _stage.value = Stage.DECRYPTED
    }

    fun reset() {
        _stage.value = Stage.INITIAL
        _encryptedMessage.value = ""
        _decryptedMessage.value = null
    }

    private suspend fun encryptMessageForDemo(plainText: String, barcode: Barcode): String? {
        return withContext(Dispatchers.IO) {
            try {
                val ikm = EncryptionManager.getIkm(barcode = barcode, password = MOCK_PASSWORD)
                if (ikm.isNullOrEmpty() && (barcode.keyType != KeyType.PASSWORD)) {
                    Log.e("TryItViewModel", "IKM derivation failed or resulted in empty IKM for key: ${barcode.name} of type ${barcode.keyType}")
                    return@withContext null
                }
                EncryptionManager.encrypt(
                    plaintext = plainText,
                    ikm = ikm,
                    keyName = barcode.name,
                    counter = 0L,
                    options = emptyList(),
                    maxAttempts = 0
                )
            } catch (e: Exception) {
                Log.e("TryItViewModel", "Error in encryptMessageForDemo for key '${barcode.name}': ${e.message}", e)
                null
            }
        }
    }
}
