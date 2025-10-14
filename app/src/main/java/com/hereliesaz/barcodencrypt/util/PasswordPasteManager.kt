package com.hereliesaz.barcodencrypt.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

object PasswordPasteManager {

    private const val TAG = "PasswordPasteManager"
    private var targetNode: AccessibilityNodeInfo? = null

    fun prepareForPaste(node: AccessibilityNodeInfo) {
        // MODIFIED: Directly assign the node, remove .obtain()
        targetNode = node
        Log.d(TAG, "Node prepared for paste: ${node.viewIdResourceName}")
    }

    fun pastePassword(text: String, context: Context) {
        targetNode?.let { node ->
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Paste action performed. Success: $success")
            Toast.makeText(context, "Password pasted!", Toast.LENGTH_SHORT).show()
        }
        // Clean up after pasting
        targetNode = null
    }

    fun clear() {
        // MODIFIED: Removed targetNode?.recycle()
        targetNode = null
        Log.d(TAG, "Paste target cleared.")
    }
}
