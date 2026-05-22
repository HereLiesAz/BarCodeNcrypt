package com.hereliesaz.barcodencrypt.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object Hashing {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
