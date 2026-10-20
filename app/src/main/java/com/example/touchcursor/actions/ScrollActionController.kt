package com.example.touchcursor.actions

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

/** Scroll actions do not inject touch, so the physical touchpad stream stays active. */
class ScrollActionController(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private var target: AccessibilityNodeInfo? = null
    private var targetWindowId = -1
    private var deltaX = 0f
    private var deltaY = 0f
    private var nextActionTime = 0L
    private val threshold = 32f * service.resources.displayMetrics.density
    private val pending = Runnable { scroll() }

    fun begin(x: Float, y: Float): Boolean {
        end()
        return try {
            val windows = service.windows
            try {
                val app = applicationWindow(windows)
                val root = app?.root ?: return false
                targetWindowId = app.id
                target = findTarget(root, x.toInt(), y.toInt(), intArrayOf(400), 0)
                target != null
            } finally {
                @Suppress("DEPRECATION")
                windows.forEach { it.recycle() }
            }
        } catch (_: RuntimeException) { end(); false }
    }

    fun move(dx: Float, dy: Float) {
        if (target == null) return
        deltaX += dx
        deltaY += dy
        if (maxOf(abs(deltaX), abs(deltaY)) < threshold) return
        handler.removeCallbacks(pending)
        val delay = nextActionTime - SystemClock.uptimeMillis()
        if (delay <= 0) scroll() else handler.postDelayed(pending, delay)
    }

    fun end() {
        handler.removeCallbacks(pending)
        @Suppress("DEPRECATION")
        target?.recycle()
        target = null
        targetWindowId = -1
        deltaX = 0f
        deltaY = 0f
        nextActionTime = 0
    }

    private fun scroll() {
        val node = target ?: return
        if (maxOf(abs(deltaX), abs(deltaY)) < threshold) return
        try {
            val windows = service.windows
            val stillForeground = try { applicationWindow(windows)?.id == targetWindowId } finally {
                @Suppress("DEPRECATION")
                windows.forEach { it.recycle() }
            }
            if (!stillForeground) { end(); return }
            if (!node.refresh()) { end(); return }
            val horizontal = abs(deltaX) > abs(deltaY)
            val delta = if (horizontal) deltaX else deltaY
            val forward = delta < 0
            val directional = if (horizontal) {
                if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
                else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            } else {
                if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
                else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            }
            val generic = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val actions = node.actionList.map { it.id }
            val action = when {
                directional in actions -> directional
                // Forward/backward has no axis; never use it for a horizontal swipe.
                !horizontal && generic in actions -> generic
                else -> null
            }
            if (action != null) {
                val args = if (Build.VERSION.SDK_INT >= 35) Bundle().apply {
                    val bounds = Rect().also(node::getBoundsInScreen)
                    val size = (if (horizontal) bounds.width() else bounds.height()).coerceAtLeast(1)
                    putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, (abs(delta) / size).coerceIn(0.1f, 1f))
                } else null
                node.performAction(action, args)
            }
            deltaX = 0f
            deltaY = 0f
            nextActionTime = SystemClock.uptimeMillis() + 180
        } catch (_: RuntimeException) { end() }
    }

    private fun applicationWindow(windows: List<AccessibilityWindowInfo>): AccessibilityWindowInfo? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused } ?: apps.maxByOrNull { it.layer }
    }

    /** Keep only the deepest scroll target under the hotspot; recycle every other node. */
    private fun findTarget(node: AccessibilityNodeInfo, x: Int, y: Int, budget: IntArray, depth: Int): AccessibilityNodeInfo? {
        var keep = false
        try {
            if (--budget[0] < 0 || depth > 40 || !node.isVisibleToUser) return null
            val bounds = Rect().also(node::getBoundsInScreen)
            if (!bounds.contains(x, y)) return null
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val found = findTarget(child, x, y, budget, depth + 1)
                if (found != null) return found
                if (budget[0] <= 0) break
            }
            keep = node.actionList.any { it.id in scrollActions }
            return if (keep) node else null
        } finally {
            @Suppress("DEPRECATION")
            if (!keep) node.recycle()
        }
    }

    companion object {
        private val scrollActions = setOf(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
        )
    }
}
