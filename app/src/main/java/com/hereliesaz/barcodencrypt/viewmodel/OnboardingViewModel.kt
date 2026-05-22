package com.hereliesaz.barcodencrypt.viewmodel

import android.util.Log
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.util.AuthManager
import com.hereliesaz.barcodencrypt.util.LogConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    private val TAG = "OnboardingViewModel"

    private val _signInRequest = MutableSharedFlow<GetCredentialRequest>()
    val signInRequest: SharedFlow<GetCredentialRequest> = _signInRequest.asSharedFlow()

    private val _signInResult = MutableSharedFlow<Boolean>()
    val signInResult: SharedFlow<Boolean> = _signInResult.asSharedFlow()

    private val _signInError = MutableStateFlow(false)
    val signInError: StateFlow<Boolean> = _signInError.asStateFlow()

    private val _noCredentialsFound = MutableStateFlow(false)
    val noCredentialsFound: StateFlow<Boolean> = _noCredentialsFound.asStateFlow()

    init {
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "init: OnboardingViewModel created.")
    }

    fun onSignInWithGoogleClicked() {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "onSignInWithGoogleClicked: Emitting sign-in request.")
        _signInError.value = false
        _noCredentialsFound.value = false
        viewModelScope.launch {
            _signInRequest.emit(authManager.getGoogleSignInRequest())
        }
    }

    fun onSignInError() { _signInError.value = true }
    fun onNoCredentialsFound() { _noCredentialsFound.value = true }

    fun handleSignInResult(result: GetCredentialResponse) {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "handleSignInResult: Handling successful credential response.")
        viewModelScope.launch {
            val credential = authManager.handleSignInResult(result)
            _signInResult.emit(credential != null)
        }
    }

    fun onSetPasswordClicked(password: String) {
        authManager.setPassword(password)
        viewModelScope.launch { _signInResult.emit(true) }
    }

    override fun onCleared() {
        super.onCleared()
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "onCleared: OnboardingViewModel destroyed.")
    }
}
