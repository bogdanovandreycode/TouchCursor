package com.example.touchcursor.actions

import android.view.accessibility.AccessibilityNodeInfo
import com.example.touchcursor.testing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ConfigurableAccessibilityServiceShadow::class])
class TextActionControllerTest {
    private val service = accessibilityService()
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)
    private val controller = TextActionController(service)
    private val copy = AccessibilityNodeInfo.ACTION_COPY
    private val paste = AccessibilityNodeInfo.ACTION_PASTE

    @Test fun absentFocusReturnsFalse() { assertFalse(controller.copy()); assertFalse(controller.paste()) }

    @Test fun copyUsesInputSelectionBeforeAccessibilitySelection() {
        val input = accessibilityNode("input", copy)
        val other = accessibilityNode("other", copy)
        platform.inputFocus = input
        platform.accessibilityFocus = other
        assertTrue(controller.copy())
        assertEquals(listOf(copy), shadowOf(input).performedActions)
        assertTrue(shadowOf(other).performedActions.isEmpty())
    }

    @Test fun failedInputCopyFallsBackToAccessibilitySelection() {
        val input = accessibilityNode("input", copy)
        shadowOf(input).setOnPerformActionListener { _, _ -> false }
        val other = accessibilityNode("other", copy)
        platform.inputFocus = input; platform.accessibilityFocus = other
        assertTrue(controller.copy())
        assertEquals(listOf(copy), shadowOf(other).performedActions)
    }

    @Test fun staleAndUnsupportedNodesAreNeverInvoked() {
        val stale = accessibilityNode("input", copy)
        shadowOf(stale).setRefreshReturnValue(false)
        val unsupported = accessibilityNode()
        platform.inputFocus = stale; platform.accessibilityFocus = unsupported
        assertFalse(controller.copy())
        assertTrue(shadowOf(stale).performedActions.isEmpty())
        assertTrue(shadowOf(unsupported).performedActions.isEmpty())
    }

    @Test fun pasteRequiresEditableInputAndDoesNotUseAccessibilityFocus() {
        val input = accessibilityNode("input", paste)
        val other = accessibilityNode("other", paste).apply { isEditable = true }
        platform.inputFocus = input; platform.accessibilityFocus = other
        assertFalse(controller.paste())
        assertTrue(shadowOf(input).performedActions.isEmpty())
        assertTrue(shadowOf(other).performedActions.isEmpty())
        input.isEditable = true
        assertTrue(controller.paste())
        assertEquals(listOf(paste), shadowOf(input).performedActions)
    }

    @Test fun sameNodeInBothFocusSlotsCanCopy() {
        platform.inputFocus = accessibilityNode("input", copy)
        platform.accessibilityFocus = platform.inputFocus
        assertTrue(controller.copy())
    }

    @Test fun disconnectedServiceAndActionExceptionReturnFalse() {
        platform.failFocusRead = true
        assertFalse(controller.copy()); assertFalse(controller.paste())
        platform.failFocusRead = false
        platform.inputFocus = accessibilityNode("input", paste).apply { isEditable = true }
        shadowOf(platform.inputFocus!!).setOnPerformActionListener { _, _ -> throw IllegalStateException("Disconnected") }
        assertFalse(controller.paste())
    }
}
