package com.hereliesaz.barcodencrypt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.hereliesaz.barcodencrypt.services.MessageDetectionService
import com.hereliesaz.barcodencrypt.ui.App
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.LogConfig
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        setContent {
            BarcodencryptTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onResume")
        viewModel.setServiceStatus(isAccessibilityServiceEnabled())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.setNotificationPermissionStatus(
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            viewModel.setNotificationPermissionStatus(true)
        }
        viewModel.setContactsPermissionStatus(ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED)
        viewModel.setOverlayPermissionStatus(Settings.canDrawOverlays(this))
    }

    override fun onStop() {
        super.onStop()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onDestroy")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${MessageDetectionService::class.java.canonicalName}"
        val setting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return setting?.contains(service) == true
    }
}