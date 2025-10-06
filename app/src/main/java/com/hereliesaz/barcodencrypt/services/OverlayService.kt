package com.hereliesaz.barcodencrypt.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.hereliesaz.barcodencrypt.ui.composable.SuggestionOverlay
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.ScannerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_BOUNDS = "extra_bounds"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        removeOverlay()

        if (intent != null) {
            val message = intent.getStringExtra(EXTRA_MESSAGE)
            val messageBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_BOUNDS, Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_BOUNDS)
            }

            if (message != null && messageBounds != null) {
                showOverlay(message, messageBounds)
            }
        }

        return START_NOT_STICKY
    }

    private fun showOverlay(message: String, bounds: Rect) {
        overlayView = ComposeView(this).apply {
            setContent {
                BarcodencryptTheme {
                    SuggestionOverlay(
                        message = message,
                        onDecryptClick = {
                            coroutineScope.launch {
                                ScannerManager.requestScan { barcodeValue ->
                                    if (barcodeValue != null) {
                                        Log.d("OverlayService", "Barcode scanned: $barcodeValue")
                                    }
                                }
                            }
                            removeOverlay()
                        }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            bounds.width(),
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
            x = bounds.left
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
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}