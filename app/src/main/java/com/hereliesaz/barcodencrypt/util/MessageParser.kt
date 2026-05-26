package com.hereliesaz.barcodencrypt.util

import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.crypto.MessageEnvelope

/**
 * Detects v5 message envelopes inside arbitrary on-screen text.
 *
 * Token shape: `~BCEv6~<base64>` where the base64 is URL-safe + no-wrap + no-padding
 * (matches [MessageEnvelope]). The regex matches exactly the URL-safe alphabet the
 * envelope emits, so detected tokens always decode.
 */
object MessageParser {

    private val V5_REGEX = Regex("${Regex.escape(MessageEnvelope.PREFIX)}[A-Za-z0-9_-]+")

    fun findAllV5Tokens(
        rootNode: AccessibilityNodeInfo,
    ): List<Pair<String, AccessibilityNodeInfo>> {
        val messages = mutableListOf<Pair<String, AccessibilityNodeInfo>>()
        val nodesToSearch = ArrayDeque<AccessibilityNodeInfo>()
        nodesToSearch.add(rootNode)

        while (nodesToSearch.isNotEmpty()) {
            val currentNode = nodesToSearch.removeFirst()
            currentNode.text?.let { text ->
                V5_REGEX.findAll(text).forEach { match ->
                    @Suppress("DEPRECATION")
                    val nodeCopy = AccessibilityNodeInfo.obtain(currentNode)
                    messages.add(match.value to nodeCopy)
                }
            }
            for (i in 0 until currentNode.childCount) {
                currentNode.getChild(i)?.let { nodesToSearch.add(it) }
            }
        }
        return messages
    }
}
