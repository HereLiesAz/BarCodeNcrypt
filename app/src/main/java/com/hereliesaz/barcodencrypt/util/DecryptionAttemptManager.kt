package com.hereliesaz.barcodencrypt.util

import android.content.Context
import android.content.SharedPreferences

class DecryptionAttemptManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("decryption_attempts", Context.MODE_PRIVATE)

    fun getRemainingAttempts(ciphertext: String, maxAttempts: Int): Int {
        if (maxAttempts == 0) {
            return Int.MAX_VALUE // Unlimited attempts
        }
        val key = getKey(ciphertext)
        return prefs.getInt(key, maxAttempts)
    }

    fun recordFailedAttempt(ciphertext: String, maxAttempts: Int) {
        if (maxAttempts == 0) {
            return // No need to record for unlimited attempts
        }
        val key = getKey(ciphertext)
        val remaining = getRemainingAttempts(ciphertext, maxAttempts)
        if (remaining > 0) {
            prefs.edit().putInt(key, remaining - 1).apply()
        }
    }

    fun resetAttempts(ciphertext: String) {
        val key = getKey(ciphertext)
        prefs.edit().remove(key).apply()
    }

    private fun getKey(ciphertext: String): String {
        return Hashing.sha256(ciphertext)
    }
}
