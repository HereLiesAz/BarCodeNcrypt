package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PasswordViewModel(application: Application) : AndroidViewModel(application) {

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

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "password_prefs",
        masterKeyAlias,
        application,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        checkPasswordState()
    }

    private fun checkPasswordState() {
        viewModelScope.launch {
            val passwordHash = sharedPreferences.getString("db_password_hash", null)
            _passwordState.value = if (passwordHash == null) PasswordState.Unset else PasswordState.Set
        }
    }

    fun submitPassword(password: String) {
        viewModelScope.launch {
            if (_passwordState.value == PasswordState.Set) {
                val storedHash = sharedPreferences.getString("db_password_hash", null)
                if (storedHash == EncryptionManager.sha256(password)) {
                    _passwordState.value = PasswordState.Success
                } else {
                    _passwordState.value = PasswordState.Invalid
                }
            }
        }
    }

    fun createPassword(password: String) {
        viewModelScope.launch {
            val passwordHash = EncryptionManager.sha256(password)
            sharedPreferences.edit().putString("db_password_hash", passwordHash).apply()
            _passwordState.value = PasswordState.Success
        }
    }
}