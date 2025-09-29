package com.hereliesaz.barcodencrypt

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.crypto.tink.aead.AeadConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.util.AuthManager
import com.hereliesaz.barcodencrypt.util.LogConfig

class BarcodeApplication : Application() {
    private val TAG = "BarcodeApplication"

    lateinit var database: AppDatabase

    val authManager: AuthManager by lazy {
        if (LogConfig.AUTH_FLOW) Log.d(TAG, "AuthManager singleton instance created.")
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        AuthManager(this, sharedPreferences)
    }

    override fun onCreate() {
        super.onCreate()
        if (LogConfig.APPLICATION_START) Log.d(TAG, "onCreate: Application starting.")

        // Initialize Cryptography
        try {
            com.hereliesaz.barcodencrypt.crypto.EncryptionManager.init()
            if (LogConfig.CRYPTO_INIT) Log.d(TAG, "Tink initialization successful.")
        } catch (e: Exception) {
            Log.e(TAG, "Tink initialization failed", e)
        }

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize Firebase App Check with the debug provider
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // THIS IS THE CORRECTED LINE:
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        // Add a listener to get the debug token and log it.
        firebaseAppCheck.getAppCheckToken(true).addOnSuccessListener { token ->
            if (token != null) {
                Log.d("AppCheckDebug", "App Check Debug Token: ${token.token}")
            }
        }
    }
}