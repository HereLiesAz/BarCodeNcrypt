package com.hereliesaz.barcodencrypt.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.data.RatchetStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the QR-based DH key exchange screen.
 *
 * v1.0 ships the symmetric chain only; the X25519 ratchet code in [RatchetEngine] is in
 * place but the actual key-exchange UI plumbing — generating an ephemeral keypair,
 * rendering it as a QR code, ingesting the peer's QR, and seeding the ratchet — is
 * staged behind TODOs until Plan 4 redoes navigation and can drive the round-trip.
 */
@HiltViewModel
class KeyExchangeViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val barcodeRepository: BarcodeRepository,
    private val ratchetStateRepository: RatchetStateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contactLookupKey: String =
        checkNotNull(savedStateHandle["contactLookupKey"]) { "contactLookupKey nav arg required" }

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    private val _secureChannelReady = MutableStateFlow(false)
    val secureChannelReady: StateFlow<Boolean> = _secureChannelReady.asStateFlow()

    init {
        // The placeholder QR content is constant, so render it once rather than on every
        // contact emission (which would churn 512x512 bitmaps).
        _qrCodeBitmap.value = renderPlaceholderQr()
        viewModelScope.launch {
            contactRepository.getContactByLookupKey(contactLookupKey).collect { contact ->
                _contact.value = contact
                _secureChannelReady.value =
                    ratchetStateRepository.load(contactLookupKey)?.dhRemotePub != null
                // TODO("Plan 4"): generate an X25519 ephemeral keypair via
                // RatchetEngine.startSession(), persist its RatchetState, and emit the
                // public key as a QR.
            }
        }
    }

    fun saveRemotePublicKey(remotePublicKey: ByteArray) {
        // TODO("Plan 4"): pull the current RatchetState, call
        // RatchetEngine.startSession(state, remotePublicKey) (or dhRatchetReceive if
        // we already have an ephemeral), and persist the result.
        viewModelScope.launch {
            // No-op for v1.0; downstream code reads _secureChannelReady.
        }
    }

    private fun renderPlaceholderQr(): Bitmap? = runCatching {
        val writer = QRCodeWriter()
        val matrix = writer.encode("BCEv6-placeholder", BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).also { bmp ->
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
        }
    }.getOrNull()
}
