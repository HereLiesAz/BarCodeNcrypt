package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager

class TryItViewModel : ViewModel() {

    enum class Stage {
        INITIAL,
        MESSAGE_GENERATED,
        DECRYPTED
    }

    private val _stage = MutableLiveData(Stage.INITIAL)
    val stage: LiveData<Stage> = _stage

    private val _encryptedMessage = MutableLiveData<String>()
    val encryptedMessage: LiveData<String> = _encryptedMessage

    private val _decryptedMessage = MutableLiveData<String?>()
    val decryptedMessage: LiveData<String?> = _decryptedMessage

    // A self-contained secret key for the demo, so it doesn't rely on the database.
    // This allows the tutorial to work without any prior setup.
    private val demoIkm = "DEMO_IKM_SECRET_KEY_FOR_TUTORIAL"

    fun generateMessage() {
        val plaintext = "This is a secret message for the demo!"
        // Using the v4 encryption for the tutorial as it's simpler and doesn't require a contact.
        val encrypted = EncryptionManager.encrypt(
            plaintext = plaintext,
            ikm = demoIkm,
            keyName = "tutorial_key",
            counter = 1L,
            options = emptyList()
        )
        _encryptedMessage.value = encrypted
        _stage.value = Stage.MESSAGE_GENERATED
    }

    fun performDecryption() {
        val messageToDecrypt = _encryptedMessage.value
        if (messageToDecrypt != null) {
            val decrypted = EncryptionManager.decrypt(messageToDecrypt, demoIkm)
            onMessageDecrypted(decrypted?.plaintext ?: "Decryption failed. Incorrect key used.")
        }
    }

    fun onMessageDecrypted(decryptedText: String) {
        // We need to verify that the decrypted text matches the original plaintext for this demo
        val originalPlaintext = "This is a secret message for the demo!"
        if (decryptedText == originalPlaintext) {
            _decryptedMessage.value = decryptedText
            _stage.value = Stage.DECRYPTED
        } else {
            _decryptedMessage.value = "Decryption failed. Incorrect key used."
            _stage.value = Stage.DECRYPTED
        }
    }

    fun reset() {
        _stage.value = Stage.INITIAL
        _encryptedMessage.value = ""
        _decryptedMessage.value = null
    }
}