package com.hereliesaz.barcodencrypt.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.hereliesaz.barcodencrypt.util.Hashing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    @ApplicationContext context: Context
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

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "password_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init { checkPasswordState() }

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
                _passwordState.value = if (storedHash == Hashing.sha256(password)) {
                    PasswordState.Success
                } else {
                    PasswordState.Invalid
                }
            }
        }
    }

    fun createPassword(password: String) {
        viewModelScope.launch {
            sharedPreferences.edit().putString("db_password_hash", Hashing.sha256(password)).apply()
            _passwordState.value = PasswordState.Success
        }
    }
}
