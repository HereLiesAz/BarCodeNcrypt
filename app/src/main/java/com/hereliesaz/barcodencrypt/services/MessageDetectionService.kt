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
    private val DEBOUNCE_DELAY_MS = 250L // 250ms debounce delay

    private val scanRunnable = Runnable {
        val rootNode = rootInActiveWindow ?: return@Runnable
        findAndHighlightMessage(rootNode)
        rootNode.recycle()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handler.removeCallbacksAndMessages(null)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source ?: return
                handler.postDelayed(scanRunnable, DEBOUNCE_DELAY_MS)
                source.recycle()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Stop any existing overlay when the window state changes
                stopService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun findAndHighlightMessage(rootNode: AccessibilityNodeInfo) {
        val v3Messages = MessageParser.findAllV3MessagesWithNodes(rootNode)
        val v4Messages = MessageParser.findAllV4MessagesWithNodes(rootNode)
        val allMessages = v3Messages + v4Messages

        Log.d("MessageDetectionService", "Found ${allMessages.size} messages.")
        if (allMessages.isNotEmpty()) {
            // For now, just highlight the first message found.
            val (message, node) = allMessages.first()
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Stop any existing overlay before starting a new one
            stopService(Intent(this, OverlayService::class.java))

            // Start the OverlayService to highlight the message
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_MESSAGE, message)
                putExtra(OverlayService.EXTRA_BOUNDS, rect)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startService(intent)
            node.recycle()
        } else {
            // If no messages are found, ensure the overlay is removed.
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onInterrupt() {
        // Handle interruptions
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MessageDetectionService", "Service connected.")
    }
}