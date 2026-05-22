package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.util.EditableTargetRegistry
import com.hereliesaz.barcodencrypt.util.MessageParser
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MessageDetectionService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_DELAY_MS = 250L

    private val scanRunnable = Runnable {
        val rootNode = rootInActiveWindow ?: return@Runnable
        findAndHighlightMessage(rootNode)
        @Suppress("DEPRECATION")
        rootNode.recycle()
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
                handler.postDelayed(scanRunnable, DEBOUNCE_DELAY_MS)
                @Suppress("DEPRECATION")
                source.recycle()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                stopService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun findAndHighlightMessage(rootNode: AccessibilityNodeInfo) {
        val messages = MessageParser.findAllV5Tokens(rootNode)
        val focusedEditableNode = findFocusedEditableNode(rootNode)

        if (messages.isNotEmpty()) {
            val (message, node) = messages.first()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            startOverlayService(message = message, bounds = rect, node = node, showEncryptButton = false)
        } else if (focusedEditableNode != null) {
            val rect = Rect()
            focusedEditableNode.getBoundsInScreen(rect)
            startOverlayService(bounds = rect, showEncryptButton = true, node = focusedEditableNode)
        } else {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    private fun findFocusedEditableNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (rootNode.isEditable && rootNode.isFocused) {
            return rootNode
        }
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun startOverlayService(
        message: String? = null,
        bounds: Rect,
        showEncryptButton: Boolean = false,
        node: AccessibilityNodeInfo? = null,
    ) {
        stopService(Intent(this, OverlayService::class.java))
        val token = node?.let { EditableTargetRegistry.register(it) } ?: 0L
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_BOUNDS, bounds)
            putExtra(OverlayService.EXTRA_SHOW_ENCRYPT_BUTTON, showEncryptButton)
            putExtra(OverlayService.EXTRA_TARGET_TOKEN, token)
            message?.let { putExtra(OverlayService.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "MessageDetectionService"
    }
}
