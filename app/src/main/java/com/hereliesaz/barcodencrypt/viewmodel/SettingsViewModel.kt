package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.util.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    fun logout() {
        viewModelScope.launch { authManager.logout() }
    }

    fun setPassword(password: String) {
        viewModelScope.launch(Dispatchers.IO) { authManager.setPassword(password) }
    }
}
