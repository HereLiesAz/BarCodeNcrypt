package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64

/**
 * Wire-format wrapper around a binary v5 payload (header || ciphertext).
 *
 * Encodes as `~BCEv5~<base64>`. The base64 is URL-safe + no-wrap so the token survives
 * chat clients that line-wrap or smart-quote pasted text.
 */
object MessageEnvelope {
    const val PREFIX = "~BCEv5~"
    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING

    fun encode(payload: ByteArray): String =
        PREFIX + Base64.encodeToString(payload, BASE64_FLAGS)

    fun decode(token: String): ByteArray? {
        if (!token.startsWith(PREFIX)) return null
        return runCatching { Base64.decode(token.removePrefix(PREFIX), BASE64_FLAGS) }.getOrNull()
    }
}
