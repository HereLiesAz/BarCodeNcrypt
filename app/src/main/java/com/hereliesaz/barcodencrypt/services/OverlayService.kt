package com.hereliesaz.barcodencrypt.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.ComposeView
import com.hereliesaz.barcodencrypt.ui.ScannerActivity
import com.hereliesaz.barcodencrypt.ui.composable.EncryptionOverlay
import com.hereliesaz.barcodencrypt.ui.composable.SuggestionOverlay
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

import com.hereliesaz.barcodencrypt.util.AccessibilityNodeHolder

// ...

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_BOUNDS = "extra_bounds"
        const val EXTRA_SHOW_ENCRYPT_BUTTON = "extra_show_encrypt_button"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        removeOverlay()

        intent?.let {
            val message = it.getStringExtra(EXTRA_MESSAGE)
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_BOUNDS, Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_BOUNDS)
            }
            val showEncryptButton = it.getBooleanExtra(EXTRA_SHOW_ENCRYPT_BUTTON, false)

            if (bounds != null) {
                showOverlay(message, bounds, showEncryptButton)
            }
        }

        return START_NOT_STICKY
    }

    private fun showOverlay(message: String?, bounds: Rect, showEncryptButton: Boolean) {
        overlayView = ComposeView(this).apply {
            setContent {
                BarcodencryptTheme {
                    if (showEncryptButton && AccessibilityNodeHolder.node != null) {
                        EncryptionOverlay(node = AccessibilityNodeHolder.node!!, onEncrypt = { encryptedText ->
                            removeOverlay()
                        })
                    } else if (message != null) {
                        SuggestionOverlay(
                            message = message,
                            onDecryptClick = {
                                val scannerIntent = Intent(context, ScannerActivity::class.java).apply {
                                    action = Constants.ACTION_DECRYPT_MESSAGE
                                    putExtra(EXTRA_MESSAGE, message)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(scannerIntent)
                                removeOverlay()
                            }
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            if (showEncryptButton) WindowManager.LayoutParams.WRAP_CONTENT else bounds.width(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (showEncryptButton) bounds.right else bounds.left
            y = bounds.top
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        overlayView = null
        focusedNode = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
