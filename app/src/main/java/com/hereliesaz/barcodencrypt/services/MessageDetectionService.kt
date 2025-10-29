package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.util.MessageParser

class MessageDetectionService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_DELAY_MS = 250L

    private val scanRunnable = Runnable {
        val rootNode = rootInActiveWindow ?: return@Runnable
        findAndHighlightMessage(rootNode)
        rootNode.recycle()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handler.removeCallbacksAndMessages(null)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source ?: return
                handler.postDelayed(scanRunnable, DEBOUNCE_DELAY_MS)
                source.recycle()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                stopService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun findAndHighlightMessage(rootNode: AccessibilityNodeInfo) {
        val messages = MessageParser.findAllV3MessagesWithNodes(rootNode) + MessageParser.findAllV4MessagesWithNodes(rootNode)
        val focusedEditableNode = findFocusedEditableNode(rootNode)

        if (messages.isNotEmpty()) {
            val (message, node) = messages.first()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            startOverlayService(message = message, bounds = rect)
            node.recycle()
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
            val child = rootNode.getChild(i)
            val result = findFocusedEditableNode(child)
            if (result != null) {
                return result
            }
        }
        return null
    }

import com.hereliesaz.barcodencrypt.util.AccessibilityNodeHolder

// ...

    private fun startOverlayService(message: String? = null, bounds: Rect, showEncryptButton: Boolean = false, node: AccessibilityNodeInfo? = null) {
        stopService(Intent(this, OverlayService::class.java))
        AccessibilityNodeHolder.node = node
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_BOUNDS, bounds)
            putExtra(OverlayService.EXTRA_SHOW_ENCRYPT_BUTTON, showEncryptButton)
            message?.let { putExtra(OverlayService.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MessageDetectionService", "Service connected.")
    }
}
