package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hereliesaz.barcodencrypt.BarcodeApplication
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class KeyExchangeViewModel(
    application: Application,
    private val contactLookupKey: String
) : AndroidViewModel(application) {

    private val contactRepository: ContactRepository
    private val barcodeRepository: BarcodeRepository

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    init {
        val database = (application as BarcodeApplication).database
        contactRepository = ContactRepository(database.contactDao())
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        viewModelScope.launch {
            contactRepository.getContactByLookupKey(contactLookupKey).collect { contact ->
                if (contact != null) {
                    if (contact.dhKeyPair == null) {
                        val newKeyPair = EncryptionManager.generateV3KeyPair()
                        val updatedContact = contact.copy(dhKeyPair = newKeyPair)
                        contactRepository.updateContact(updatedContact)
                        _contact.value = updatedContact
                        generateQrCode(newKeyPair)
                    } else {
                        _contact.value = contact
                        generateQrCode(contact.dhKeyPair)
                    }
                }
            }
        }
    }

    fun saveRemotePublicKey(remotePublicKey: ByteArray) {
        viewModelScope.launch {
            val currentContact = _contact.value ?: return@launch
            val ownKeyPair = currentContact.dhKeyPair ?: return@launch

            // Use the first barcode as the pre-shared key for the initial exchange
            val initialBarcode = barcodeRepository.getBarcodesForContact(contactLookupKey).value?.firstOrNull()
            if (initialBarcode == null) {
                // Handle error: No barcode to use as shared secret
                return@launch
            }
            initialBarcode.decryptValue()
            val sharedSecret = initialBarcode.value.toByteArray(Charsets.UTF_8)

            // Perform DH calculation and KDF to get the initial root and sending chain keys
            val dhOutput = EncryptionManager.getV3PublicKey(ownKeyPair).let {
                com.google.crypto.tink.subtle.X25519.computeSharedSecret(ownKeyPair, remotePublicKey)
            }
            val (rootKey, sendingChainKey) = kdfRk(sharedSecret, dhOutput)

            val updatedContact = currentContact.copy(
                dhRemotePublicKey = remotePublicKey,
                rootKey = rootKey,
                sendingChainKey = sendingChainKey
            )
            contactRepository.updateContact(updatedContact)
            _contact.value = updatedContact
        }
    }

    private fun generateQrCode(keyPair: ByteArray) {
        val publicKey = EncryptionManager.getV3PublicKey(keyPair)
        val writer = QRCodeWriter()
        try {
            val bitMatrix = writer.encode(publicKey.toString(Charsets.ISO_8859_1), BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            _qrCodeBitmap.value = bmp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // This needs to be defined within the ViewModel to access EncryptionManager
    private fun kdfRk(rk: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        return com.hereliesaz.barcodencrypt.crypto.EncryptionManager.kdfRk(rk, dhOutput)
    }
}

class KeyExchangeViewModelFactory(
    private val application: Application,
    private val contactLookupKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KeyExchangeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KeyExchangeViewModel(application, contactLookupKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}