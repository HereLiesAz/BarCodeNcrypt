package com.hereliesaz.barcodencrypt.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.hereliesaz.barcodencrypt.util.AuthManager
import com.hereliesaz.barcodencrypt.util.LogConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val authManager: AuthManager) : ViewModel() {
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

    override fun onCleared() {
        super.onCleared()
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "onCleared: MainViewModel destroyed.")
    }
}