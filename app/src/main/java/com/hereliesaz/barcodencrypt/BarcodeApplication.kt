package com.hereliesaz.barcodencrypt

import android.app.Application
import android.util.Log
import com.google.crypto.tink.aead.AeadConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.hereliesaz.barcodencrypt.util.LogConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BarcodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (LogConfig.APPLICATION_START) Log.d(TAG, "onCreate")

        try {
            AeadConfig.register()
            if (LogConfig.CRYPTO_INIT) Log.d(TAG, "Tink AeadConfig.register() OK")
        } catch (t: Throwable) {
            Log.e(TAG, "Tink AeadConfig.register() failed", t)
        }

        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            appCheck.getAppCheckToken(true).addOnSuccessListener { token ->
                token?.token?.let { Log.d("AppCheckDebug", "App Check Debug Token: $it") }
            }
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }

    companion object {
        private const val TAG = "BarcodeApplication"
    }
}
