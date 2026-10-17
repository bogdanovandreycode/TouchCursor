package com.example.touchcursor.actions

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class KeyboardController(private val service: AccessibilityService) {
    private var hidKeyboard = false

    /** No BACK fallback: that could navigate away when the IME is already hidden. */
    fun toggle(): String = try {
        val controller = service.softKeyboardController
        val visible = service.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (visible && controller.showMode != AccessibilityService.SHOW_MODE_HIDDEN) {
            if (controller.setShowMode(AccessibilityService.SHOW_MODE_HIDDEN)) {
                hidKeyboard = true
                "Keyboard hidden. Tap KEYS again to allow it."
            } else "Keyboard control is unavailable"
        } else {
            if (!controller.setShowMode(AccessibilityService.SHOW_MODE_AUTO)) {
                "Keyboard control is unavailable"
            } else {
                hidKeyboard = false
                val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                try {
                    if (node != null && node.isEditable) {
                        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_FOCUS })
                            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        "Keyboard allowed; the focused app controls whether it opens"
                    } else "Keyboard allowed. Focus a text field to open it."
                } finally {
                    @Suppress("DEPRECATION")
                    node?.recycle()
                }
            }
        }
    } catch (_: RuntimeException) { "Keyboard control is unavailable" }

    fun close() {
        if (hidKeyboard) {
            try { service.softKeyboardController.setShowMode(AccessibilityService.SHOW_MODE_AUTO) }
            catch (_: RuntimeException) { /* Service may already be disconnected. */ }
        }
        hidKeyboard = false
    }
}
