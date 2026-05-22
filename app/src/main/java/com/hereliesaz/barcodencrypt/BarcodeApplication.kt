package com.hereliesaz.barcodencrypt

import android.app.Application
import com.google.crypto.tink.aead.AeadConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.hereliesaz.barcodencrypt.util.LogConfig
import dagger.hilt.android.HiltAndroidApp
import android.util.Log

@HiltAndroidApp
class BarcodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (LogConfig.APPLICATION_START) Log.d(TAG, "onCreate")

        // One-time Tink registration. Idempotent; safe to call once per process.
        AeadConfig.register()

        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        if (BuildConfig.DEBUG) {
            appCheck.getAppCheckToken(true).addOnSuccessListener { token ->
                token?.token?.let { Log.d("AppCheckDebug", "App Check Debug Token: $it") }
            }
        }
    }

    companion object {
        private const val TAG = "BarcodeApplication"
    }
}
