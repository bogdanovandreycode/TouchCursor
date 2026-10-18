package com.example.touchcursor.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.touchcursor.R

/** Finds only this service's target in System UI, using public accessibility node bounds. */
class SystemShortcutTracker(private val service: AccessibilityService) {
    var windowId = -1
        private set

    fun refresh(): Rect? {
        windowId = -1
        return try {
            val windows = service.windows
            try {
                for (window in windows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM &&
                        window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                    val root = window.root ?: continue
                    if (root.packageName?.toString() != "com.android.systemui") {
                        recycle(root)
                        continue
                    }
                    val found = findTarget(root, intArrayOf(120))
                    if (found != null) {
                        windowId = window.id
                        return found
                    }
                }
                null
            } finally {
                @Suppress("DEPRECATION")
                windows.forEach { it.recycle() }
            }
        } catch (_: RuntimeException) { null }
    }

    private fun findTarget(node: AccessibilityNodeInfo, budget: IntArray): Rect? = try {
        if (--budget[0] < 0) null else {
            val label = service.getString(R.string.accessibility_service_label)
            if (node.isVisibleToUser && node.isClickable &&
                (node.contentDescription?.toString() == label || node.text?.toString() == label)) {
                Rect().also { node.getBoundsInScreen(it) }.takeUnless { it.isEmpty }
            } else {
                var found: Rect? = null
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    found = findTarget(child, budget)
                    if (found != null || budget[0] <= 0) break
                }
                found
            }
        }
    } finally { recycle(node) }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { node.recycle() }
}
