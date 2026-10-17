package com.example.touchcursor.actions

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class TextActionController(private val service: AccessibilityService) {
    fun copy(): Boolean = perform(AccessibilityNodeInfo.ACTION_COPY, editableOnly = false)
    fun paste(): Boolean = perform(AccessibilityNodeInfo.ACTION_PASTE, editableOnly = true)

    private fun perform(action: Int, editableOnly: Boolean): Boolean = try {
        val input = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        var accessibility: AccessibilityNodeInfo? = null
        try {
            if (!editableOnly) accessibility = service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            listOfNotNull(input, accessibility).any { node ->
                (!editableOnly || node.isEditable) &&
                    node.actionList.any { it.id == action } && node.performAction(action)
            }
        } finally {
            @Suppress("DEPRECATION")
            input?.recycle()
            @Suppress("DEPRECATION")
            if (accessibility !== input) accessibility?.recycle()
        }
    } catch (_: RuntimeException) { false }
}
