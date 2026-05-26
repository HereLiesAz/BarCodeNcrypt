package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.hereliesaz.barcodencrypt.util.EditableTargetRegistry
import com.hereliesaz.barcodencrypt.util.MessageParser
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MessageDetectionService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_DELAY_MS = 250L

    private val scanRunnable = Runnable {
        val rootNode = rootInActiveWindow ?: return@Runnable
        try {
            findAndHighlightMessage(rootNode)
        } finally {
            @Suppress("DEPRECATION")
            rootNode.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Foreground-promote so Android 14+ doesn't kill the service when it hands off
        // detected messages to the overlay.
        ForegroundNotifications.start(
            this,
            ForegroundNotifications.ID_MESSAGE_DETECTION,
            "Watching for encrypted messages",
        )
        Log.d(TAG, "Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handler.removeCallbacksAndMessages(null)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source ?: return
                @Suppress("DEPRECATION")
                source.recycle()
                handler.postDelayed(scanRunnable, DEBOUNCE_DELAY_MS)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> stopOverlay()
        }
    }

    private fun findAndHighlightMessage(rootNode: AccessibilityNodeInfo) {
        val messages = MessageParser.findEnvelopeTokens(rootNode)
        if (messages.isNotEmpty()) {
            val (message, node) = messages.first()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // The overlay only needs the message text and bounds for the decrypt
            // suggestion, so we don't register a node (which would leak a token).
            startOverlayService(message = message, bounds = rect, showEncryptButton = false, node = null)
            // Recycle the parser's node copies now that we've read their bounds.
            messages.forEach {
                @Suppress("DEPRECATION")
                it.second.recycle()
            }
            return
        }

        val focusedEditableNode = findFocusedEditableNode(rootNode)
        if (focusedEditableNode != null) {
            val rect = Rect()
            focusedEditableNode.getBoundsInScreen(rect)
            startOverlayService(bounds = rect, showEncryptButton = true, node = focusedEditableNode)
        } else {
            stopOverlay()
        }
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) {
                return result
            }
            // No match in this subtree — recycle it. (The result, if found, is owned by the
            // caller; the registry stores its own copy.)
            @Suppress("DEPRECATION")
            child.recycle()
        }
        return null
    }

    private fun startOverlayService(
        message: String? = null,
        bounds: Rect,
        showEncryptButton: Boolean = false,
        node: AccessibilityNodeInfo? = null,
    ) {
        val token = node?.let { EditableTargetRegistry.register(it) } ?: 0L
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_BOUNDS, bounds)
            putExtra(OverlayService.EXTRA_SHOW_ENCRYPT_BUTTON, showEncryptButton)
            putExtra(OverlayService.EXTRA_TARGET_TOKEN, token)
            message?.let { putExtra(OverlayService.EXTRA_MESSAGE, it) }
        }
        // OverlayService promotes itself to foreground in onCreate, so it must be started
        // as a foreground service from this background context.
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "MessageDetectionService"
    }
}
