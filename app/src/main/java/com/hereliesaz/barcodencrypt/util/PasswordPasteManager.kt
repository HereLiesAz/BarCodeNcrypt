package com.hereliesaz.barcodencrypt.util

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Holds the accessibility node that a scanned password should be pasted into.
 *
 * The node is stored as an owned copy (via `obtain`) and always recycled when consumed or
 * cleared, so we never read a node the originating accessibility event already recycled.
 * All access is synchronized because `prepareForPaste` runs on the accessibility thread
 * while `paste`/`clear` run on the UI thread.
 */
object PasswordPasteManager {

    private const val TAG = "PasswordPasteManager"
    private var targetNode: AccessibilityNodeInfo? = null

    @Synchronized
    fun prepareForPaste(node: AccessibilityNodeInfo) {
        recycleTarget()
        @Suppress("DEPRECATION")
        targetNode = AccessibilityNodeInfo.obtain(node)
        Log.d(TAG, "Node prepared for paste: ${node.viewIdResourceName}")
    }

    @Synchronized
    fun paste(text: String) {
        targetNode?.let { node ->
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }.getOrDefault(false)
            Log.d(TAG, "Paste action performed. Success: $success")
        }
        recycleTarget()
    }

    @Synchronized
    fun clear() {
        recycleTarget()
        Log.d(TAG, "Paste target cleared.")
    }

    private fun recycleTarget() {
        @Suppress("DEPRECATION")
        targetNode?.recycle()
        targetNode = null
    }
}
