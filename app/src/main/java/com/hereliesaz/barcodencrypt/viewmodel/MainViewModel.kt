package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeDao
import com.hereliesaz.barcodencrypt.data.KeyType
import com.hereliesaz.barcodencrypt.util.AuthManager
import com.hereliesaz.barcodencrypt.util.LogConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val authManager: AuthManager, application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val _serviceStatus = MutableStateFlow(false)
    val serviceStatus = _serviceStatus.asStateFlow()

    private val _notificationPermissionStatus = MutableStateFlow(false)
    val notificationPermissionStatus = _notificationPermissionStatus.asStateFlow()

    private val _contactsPermissionStatus = MutableStateFlow(false)
    val contactsPermissionStatus = _contactsPermissionStatus.asStateFlow()

    private val _overlayPermissionStatus = MutableStateFlow(false)
    val overlayPermissionStatus = _overlayPermissionStatus.asStateFlow()

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _passwordCorrect = MutableStateFlow(false)
    val passwordCorrect = _passwordCorrect.asStateFlow()

    private val _tryItState = MutableStateFlow<TryItState>(TryItState.Idle)
    val tryItState = _tryItState.asStateFlow()

    private val MOCK_PASSWORD = "password123"
    private val barcodeDao: BarcodeDao = AppDatabase.getDatabase(getApplication(), MOCK_PASSWORD).barcodeDao()

    init {
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "init: MainViewModel created.")
        viewModelScope.launch {
            authManager.user.collect { firebaseUser: FirebaseUser? ->
                val loggedIn = firebaseUser != null || authManager.hasLocalPassword()
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Auth state collected. User: ${firebaseUser?.uid}. Has local pass: ${authManager.hasLocalPassword()}. Setting isLoggedIn to: $loggedIn")
                if (_isLoggedIn.value != loggedIn) {
                    _isLoggedIn.value = loggedIn
                }
            }
        }
    }

    fun setServiceStatus(isEnabled: Boolean) {
        _serviceStatus.value = isEnabled
    }

    fun setNotificationPermissionStatus(isGranted: Boolean) {
        _notificationPermissionStatus.value = isGranted
    }

    fun setContactsPermissionStatus(isGranted: Boolean) {
        _contactsPermissionStatus.value = isGranted
    }

    fun setOverlayPermissionStatus(isGranted: Boolean) {
        _overlayPermissionStatus.value = isGranted
    }

    fun checkAuthMethod() {
        if (authManager.isGoogleUserSignedIn()) {
            if (LogConfig.AUTH_FLOW) Log.d(TAG, "User is signed in with Google. Bypassing password check.")
            _passwordCorrect.value = true
        } else {
            if (LogConfig.AUTH_FLOW) Log.d(TAG, "User is not signed in with Google. Password check is required.")
        }
    }

    fun checkPassword(password: String) {
        _passwordCorrect.value = authManager.checkPassword(password)
    }

    fun startTryItMode() {
        _tryItState.value = TryItState.MessageGenerated("Tap 'Generate' to create a test message.")
    }

    fun generateTryItMessage() {
        viewModelScope.launch {
            val demoKey = withContext(Dispatchers.IO) {
                barcodeDao.getBarcodeByName("DemoKey")
            }
            if (demoKey != null) {
                val encrypted = encryptMessageForDemo("This is a secret message!", demoKey)
                if (encrypted != null) {
                    _tryItState.value = TryItState.MessageGenerated(encrypted)
                } else {
                    _tryItState.value = TryItState.Error("Encryption failed.")
                }
            } else {
                _tryItState.value = TryItState.Error("Demo key not found.")
            }
        }
    }

    fun decryptTryItMessage(message: String) {
        _tryItState.value = TryItState.AwaitingPassword(message)
    }

    fun decryptTryItMessage(message: String, password: String) {
        viewModelScope.launch {
            val decrypted = EncryptionManager.decrypt(message, password)
            if (decrypted != null) {
                _tryItState.value = TryItState.Decrypted(decrypted.plaintext)
            } else {
                _tryItState.value = TryItState.Error("Decryption failed. Please check your password and try again.")
            }
        }
    }

    fun resetTryItMode() {
        _tryItState.value = TryItState.Idle
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

    override fun onCleared() {
        super.onCleared()
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "onCleared: MainViewModel destroyed.")
    }
}

sealed class TryItState {
    object Idle : TryItState()
    data class MessageGenerated(val encryptedMessage: String) : TryItState()
    data class AwaitingPassword(val encryptedMessage: String) : TryItState()
    data class Decrypted(val decryptedMessage: String) : TryItState()
    data class Error(val message: String) : TryItState()
}