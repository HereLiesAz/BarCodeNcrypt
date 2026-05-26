package com.hereliesaz.barcodencrypt.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.ui.ScannerActivity
import com.hereliesaz.barcodencrypt.ui.composable.EncryptionOverlay
import com.hereliesaz.barcodencrypt.ui.composable.SuggestionOverlay
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.EditableTargetRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that hosts the in-place encrypt / decrypt overlay above any app the
 * [MessageDetectionService] flagged.
 *
 * The overlay window is `FLAG_SECURE` so plaintext can't be screenshotted. The hosting
 * [ComposeView] is given its own [LifecycleOwner] / [ViewModelStoreOwner] /
 * [SavedStateRegistryOwner] so Compose can run inside a bare Service window; the overlay
 * gets its data via injected repositories rather than `hiltViewModel()`.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var contactRepository: ContactRepository
    @Inject lateinit var barcodeRepository: BarcodeRepository
    @Inject lateinit var encryptionManager: EncryptionManager

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var activeToken: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val contactsState = mutableStateOf<List<Contact>>(emptyList())
    private val barcodesState = mutableStateOf<List<Barcode>>(emptyList())

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_BOUNDS = "extra_bounds"
        const val EXTRA_SHOW_ENCRYPT_BUTTON = "extra_show_encrypt_button"
        const val EXTRA_TARGET_TOKEN = "extra_target_token"
        private const val TAG = "OverlayService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ForegroundNotifications.start(
            this,
            ForegroundNotifications.ID_OVERLAY,
            "Encrypted-message overlay active",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        removeOverlay()
        val previousToken = activeToken

        intent?.let {
            val message = it.getStringExtra(EXTRA_MESSAGE)
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_BOUNDS, Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_BOUNDS)
            }
            val showEncryptButton = it.getBooleanExtra(EXTRA_SHOW_ENCRYPT_BUTTON, false)
            activeToken = it.getLongExtra(EXTRA_TARGET_TOKEN, 0L)

            if (bounds != null) {
                showOverlay(message, bounds, showEncryptButton)
            }
        }

        // Release the node from the prior invocation now that we've re-armed.
        if (previousToken != 0L && previousToken != activeToken) {
            EditableTargetRegistry.release(previousToken)
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(message: String?, bounds: Rect, showEncryptButton: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted; cannot show overlay.")
            stopSelf()
            return
        }

        if (showEncryptButton) loadContacts()

        val owner = OverlayLifecycleOwner().also {
            it.onCreate()
            lifecycleOwner = it
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                BarcodencryptTheme {
                    if (showEncryptButton) {
                        EncryptionOverlay(
                            initialText = message ?: "",
                            contacts = contactsState.value,
                            barcodes = barcodesState.value,
                            onContactSelected = ::loadBarcodes,
                            onEncryptClicked = ::onEncryptClicked,
                        )
                    } else if (message != null) {
                        SuggestionOverlay(
                            message = message,
                            onDecryptClick = { onDecryptClick(message) },
                        )
                    }
                }
            }
        }
        overlayView = view

        val params = WindowManager.LayoutParams(
            if (showEncryptButton) WindowManager.LayoutParams.WRAP_CONTENT else bounds.width().coerceAtLeast(1),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (showEncryptButton) bounds.right else bounds.left
            y = bounds.top
        }

        owner.onResume()
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            removeOverlay()
            stopSelf()
        }
    }

    private fun loadContacts() {
        serviceScope.launch {
            contactsState.value = withContext(Dispatchers.IO) {
                contactRepository.getAllContactsWithBarcodesSync().map { it.contact }
            }
        }
    }

    private fun loadBarcodes(contact: Contact) {
        serviceScope.launch {
            barcodesState.value = withContext(Dispatchers.IO) {
                barcodeRepository.getBarcodesForContactFlow(contact.lookupKey).first()
            }
        }
    }

    private fun onEncryptClicked(text: String, barcode: Barcode, ttlMs: Long?, openCount: Int?) {
        val token = activeToken
        serviceScope.launch {
            val node = EditableTargetRegistry.resolve(token)
            val encrypted = withContext(Dispatchers.Default) {
                encryptionManager.encrypt(
                    plaintext = text,
                    contactLookupKey = barcode.contactLookupKey,
                    barcode = barcode,
                    ttlMs = ttlMs,
                    openMax = openCount,
                )
            }
            if (encrypted != null && node != null) {
                runCatching {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            encrypted,
                        )
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
            EditableTargetRegistry.release(token)
            removeOverlay()
            stopSelf()
        }
    }

    private fun onDecryptClick(message: String) {
        val scannerIntent = Intent(this, ScannerActivity::class.java).apply {
            action = Constants.ACTION_DECRYPT_MESSAGE
            putExtra(EXTRA_MESSAGE, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(scannerIntent) }
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                runCatching { windowManager.removeView(it) }
            }
        }
        overlayView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeToken != 0L) {
            EditableTargetRegistry.release(activeToken)
            activeToken = 0L
        }
        removeOverlay()
        serviceScope.cancel()
    }

    /**
     * Minimal owner so a [ComposeView] can be hosted in a Service window. Compose requires
     * a lifecycle, a saved-state registry, and a view-model store from the view tree.
     */
    private class OverlayLifecycleOwner :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun onResume() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
