package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.util.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the master-password lock screen. The password is a UI lock only (the SQLCipher
 * key is independent — see [AuthManager.getDatabasePassphrase]); it is stored in the one
 * [AuthManager] store rather than a second, divergent SharedPreferences.
 */
@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    sealed class PasswordState {
        object Idle : PasswordState()
        object Loading : PasswordState()
        object Unset : PasswordState()
        object Set : PasswordState()
        object Invalid : PasswordState()
        object Success : PasswordState()
    }

    private val _passwordState = MutableStateFlow<PasswordState>(PasswordState.Loading)
    val passwordState: StateFlow<PasswordState> = _passwordState.asStateFlow()

    init { checkPasswordState() }

    private fun checkPasswordState() {
        _passwordState.value = if (authManager.hasLocalPassword()) PasswordState.Set else PasswordState.Unset
    }

    fun submitPassword(password: String) {
        if (_passwordState.value != PasswordState.Set) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { authManager.checkPassword(password) }
            _passwordState.value = if (ok) PasswordState.Success else PasswordState.Invalid
        }
    }

    fun createPassword(password: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { authManager.setPassword(password) }
            _passwordState.value = PasswordState.Success
        }
    }
}
