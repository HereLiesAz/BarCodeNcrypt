package com.hereliesaz.barcodencrypt.crypto.model

data class V3Message(
    val dhPublicKey: ByteArray,
    val previousMessageCount: Int,
    val messageNumber: Int,
    val ciphertext: ByteArray,
    val options: List<String>,
    val keyName: String, // The barcode name/id
    val contactLookupKey: String // The unique identifier for the contact
) {
    // A V3 message is uniquely identified by the public key and message number
    val id: String
        get() = "${android.util.Base64.encodeToString(dhPublicKey, android.util.Base64.NO_WRAP)}:$messageNumber"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as V3Message

        if (!dhPublicKey.contentEquals(other.dhPublicKey)) return false
        if (previousMessageCount != other.previousMessageCount) return false
        if (messageNumber != other.messageNumber) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (options != other.options) return false
        if (keyName != other.keyName) return false
        if (contactLookupKey != other.contactLookupKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dhPublicKey.contentHashCode()
        result = 31 * result + previousMessageCount
        result = 31 * result + messageNumber
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + options.hashCode()
        result = 31 * result + keyName.hashCode()
        result = 31 * result + contactLookupKey.hashCode()
        return result
    }
}