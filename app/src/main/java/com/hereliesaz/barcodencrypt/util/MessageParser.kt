package com.hereliesaz.barcodencrypt.util

import android.graphics.Rect
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.hereliesaz.barcodencrypt.crypto.model.TinkMessage
import java.nio.charset.StandardCharsets

object MessageParser {

    private const val HEADER_PREFIX_V3 = "~BCEv3~"
    private const val HEADER_PREFIX_V4 = "~BCEv4~"
    private val V3_REGEX = Regex("~BCEv3~([A-Za-z0-9+/=]+)")
    private val V4_REGEX = Regex("~BCEv4~([A-Za-z0-9+/=]+)")

    fun parseV3Message(message: String): com.hereliesaz.barcodencrypt.crypto.model.V3Message? {
        val encodedJson = message.removePrefix(HEADER_PREFIX_V3)
        return try {
            val json = String(Base64.decode(encodedJson, Base64.DEFAULT), StandardCharsets.UTF_8)
            Gson().fromJson(json, com.hereliesaz.barcodencrypt.crypto.model.V3Message::class.java)
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is JsonSyntaxException -> null
                else -> throw e
            }
        }
    }

    fun parseV4Message(message: String): TinkMessage? {
        val encodedJson = message.removePrefix(HEADER_PREFIX_V4)
        return try {
            val json = String(Base64.decode(encodedJson, Base64.DEFAULT), StandardCharsets.UTF_8)
            Gson().fromJson(json, TinkMessage::class.java)
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is JsonSyntaxException -> null
                else -> throw e
            }
        }
    }

    fun findAllV3MessagesWithNodes(rootNode: AccessibilityNodeInfo): List<Pair<String, AccessibilityNodeInfo>> {
        val messages = mutableListOf<Pair<String, AccessibilityNodeInfo>>()
        val nodesToSearch = ArrayDeque<AccessibilityNodeInfo>()
        nodesToSearch.add(rootNode)

        while (nodesToSearch.isNotEmpty()) {
            val currentNode = nodesToSearch.removeFirst()

            currentNode.text?.let { text ->
                V3_REGEX.findAll(text).forEach { matchResult ->
                    messages.add(Pair(matchResult.value, AccessibilityNodeInfo.obtain(currentNode)))
                }
            }

            for (i in 0 until currentNode.childCount) {
                val child = currentNode.getChild(i)
                if (child != null) {
                    nodesToSearch.add(child)
                }
            }
        }
        return messages
    }

    fun findAllV4MessagesWithNodes(rootNode: AccessibilityNodeInfo): List<Pair<String, AccessibilityNodeInfo>> {
        val messages = mutableListOf<Pair<String, AccessibilityNodeInfo>>()
        val nodesToSearch = ArrayDeque<AccessibilityNodeInfo>()
        nodesToSearch.add(rootNode)

        while (nodesToSearch.isNotEmpty()) {
            val currentNode = nodesToSearch.removeFirst()

            currentNode.text?.let { text ->
                V4_REGEX.findAll(text).forEach { matchResult ->
                    // Create a copy for the pair, as the original node will be recycled
                    messages.add(Pair(matchResult.value, AccessibilityNodeInfo.obtain(currentNode)))
                }
            }

            for (i in 0 until currentNode.childCount) {
                val child = currentNode.getChild(i)
                if (child != null) {
                    nodesToSearch.add(child)
                }
            }
        }
        return messages
    }
}