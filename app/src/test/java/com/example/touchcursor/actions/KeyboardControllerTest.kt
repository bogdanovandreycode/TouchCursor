package com.example.touchcursor.actions

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.touchcursor.testing.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ConfigurableAccessibilityServiceShadow::class])
class KeyboardControllerTest {
    private val service = accessibilityService()
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)
    private val controller = KeyboardController(service)
    private val keyboard get() = service.softKeyboardController

    @After fun neverNavigatesBack() { assertTrue(platform.globalActionsPerformed.isEmpty()); controller.close() }

    @Test fun visibleKeyboardIsHiddenAndSecondToggleAllowsItAgain() {
        platform.setWindows(listOf(accessibilityWindow(1, AccessibilityWindowInfo.TYPE_INPUT_METHOD)))
        assertTrue(controller.toggle().startsWith("Keyboard hidden"))
        assertEquals(AccessibilityService.SHOW_MODE_HIDDEN, keyboard.showMode)
        assertTrue(controller.toggle().startsWith("Keyboard allowed"))
        assertEquals(AccessibilityService.SHOW_MODE_AUTO, keyboard.showMode)
    }

    @Test fun hiddenKeyboardOnlyInvokesSupportedActionsOnEditableInput() {
        val node = accessibilityNode("editor", AccessibilityNodeInfo.ACTION_FOCUS, AccessibilityNodeInfo.ACTION_CLICK)
            .apply { isEditable = true }
        platform.inputFocus = node
        assertTrue(controller.toggle().contains("focused app controls"))
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_FOCUS, AccessibilityNodeInfo.ACTION_CLICK), shadowOf(node).performedActions)
    }

    @Test fun readOnlyFieldIsNotClicked() {
        val node = accessibilityNode("reader", AccessibilityNodeInfo.ACTION_CLICK)
        platform.inputFocus = node
        assertTrue(controller.toggle().contains("Focus a text field"))
        assertTrue(shadowOf(node).performedActions.isEmpty())
    }

    @Test fun editableFieldWithoutSupportedActionsIsNotClicked() {
        val node = accessibilityNode().apply { isEditable = true }
        platform.inputFocus = node
        assertTrue(controller.toggle().startsWith("Keyboard allowed"))
        assertTrue(shadowOf(node).performedActions.isEmpty())
    }

    @Test fun closeRestoresKeyboardHiddenByThisControllerAndIsIdempotent() {
        platform.setWindows(listOf(accessibilityWindow(1, AccessibilityWindowInfo.TYPE_INPUT_METHOD)))
        controller.toggle()
        controller.close(); controller.close()
        assertEquals(AccessibilityService.SHOW_MODE_AUTO, keyboard.showMode)
    }

    @Test fun closeDoesNotOverrideKeyboardModeItDidNotChange() {
        keyboard.setShowMode(AccessibilityService.SHOW_MODE_HIDDEN)
        controller.close()
        assertEquals(AccessibilityService.SHOW_MODE_HIDDEN, keyboard.showMode)
    }

    @Test fun disconnectedServiceReportsUnavailable() {
        platform.failWindowRead = true
        assertEquals("Keyboard control is unavailable", controller.toggle())
        platform.failWindowRead = false; platform.failFocusRead = true
        assertEquals("Keyboard control is unavailable", controller.toggle())
    }
}
