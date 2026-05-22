package com.hereliesaz.barcodencrypt.util

import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Token-keyed store for `AccessibilityNodeInfo` references that the overlay needs to
 * mutate (e.g. paste plaintext back into a focused text field).
 *
 * Replaces the previous global [AccessibilityNodeHolder] singleton: that pattern
 * leaked a recycled `AccessibilityNodeInfo` after the accessibility event finished,
 * which is a use-after-free for the overlay reader. Tokens scope each registration to
 * a single overlay invocation and are explicitly released by the consumer.
 */
object EditableTargetRegistry {

    private val store = ConcurrentHashMap<Long, AccessibilityNodeInfo>()
    private val nextId = AtomicLong(1)

    /** Register the node and return a token to hand off via Intent extras. */
    fun register(node: AccessibilityNodeInfo): Long {
        val token = nextId.getAndIncrement()
        store[token] = node
        return token
    }

    /** Resolve a previously-registered node, or null if released / unknown. */
    fun resolve(token: Long): AccessibilityNodeInfo? = store[token]

    /**
     * Remove the registration and recycle the underlying node.
     * Always call this from the overlay when the user dismisses or completes the flow.
     */
    fun release(token: Long) {
        val node = store.remove(token) ?: return
        @Suppress("DEPRECATION")
        node.recycle()
    }

    /** Used by tests; not part of the production API. */
    internal fun clearAllForTest() {
        store.values.forEach {
            @Suppress("DEPRECATION")
            it.recycle()
        }
        store.clear()
    }
}
