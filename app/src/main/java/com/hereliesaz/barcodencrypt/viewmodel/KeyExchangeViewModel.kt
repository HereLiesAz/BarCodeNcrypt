package com.hereliesaz.barcodencrypt.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyExchangeViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val barcodeRepository: BarcodeRepository,
    private val encryptionManager: EncryptionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contactLookupKey: String =
        checkNotNull(savedStateHandle["contactLookupKey"]) { "contactLookupKey nav arg required" }

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    init {
        viewModelScope.launch {
            contactRepository.getContactByLookupKey(contactLookupKey).collect { contact ->
                _contact.value = contact
                // TODO("v5 — Plan 2"): when Plan 2's X25519 DH ratchet lands, generate
                // an X25519 keypair via RatchetEngine.startSession() and produce the QR
                // payload from the public key.
            }
        }
    }

    fun saveRemotePublicKey(remotePublicKey: ByteArray) {
        // TODO("v5 — Plan 2"): perform DH(self, remotePublicKey), seed the ratchet
        // root key via KDF_RK, and persist the new RatchetState via
        // RatchetStateRepository.
    }
}
