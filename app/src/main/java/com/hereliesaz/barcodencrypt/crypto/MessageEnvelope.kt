package com.hereliesaz.barcodencrypt.crypto

import java.util.Base64

/**
 * Wire-format wrapper around a binary v6 payload (header || ciphertext).
 *
 * Encodes as `~BCEv6~<base64>`. The base64 uses the URL-safe alphabet with no padding so
 * the token survives chat clients that line-wrap or smart-quote pasted text. Uses
 * [java.util.Base64] (available since API 26 = our minSdk) rather than `android.util`, so
 * the crypto path is exercised by JVM unit tests.
 */
object MessageEnvelope {
    const val PREFIX = "~BCEv6~"

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(payload: ByteArray): String =
        PREFIX + encoder.encodeToString(payload)

    fun decode(token: String): ByteArray? {
        if (!token.startsWith(PREFIX)) return null
        return runCatching { decoder.decode(token.removePrefix(PREFIX)) }.getOrNull()
    }
}
