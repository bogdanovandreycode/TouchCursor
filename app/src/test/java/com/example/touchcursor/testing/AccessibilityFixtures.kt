@file:Suppress("DEPRECATION")

package com.example.touchcursor.testing

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Handler
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService

class TestAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}

/** Controls platform replies only; application controllers remain the real objects under test. */
@Implements(AccessibilityService::class)
class ConfigurableAccessibilityServiceShadow : ShadowAccessibilityService() {
    var inputFocus: AccessibilityNodeInfo? = null
    var accessibilityFocus: AccessibilityNodeInfo? = null
    var failFocusRead = false
    var failWindowRead = false
    var failDispatch = false

    @Implementation
    fun findFocus(focus: Int): AccessibilityNodeInfo? {
        if (failFocusRead) throw IllegalStateException("Service disconnected")
        return if (focus == AccessibilityNodeInfo.FOCUS_INPUT) inputFocus else accessibilityFocus
    }

    @Implementation
    public override fun getWindows(): List<AccessibilityWindowInfo> {
        if (failWindowRead) throw IllegalStateException("Windows unavailable")
        return super.getWindows()
    }

    @Implementation(minSdk = 24)
    public override fun dispatchGesture(gesture: GestureDescription, callback: AccessibilityService.GestureResultCallback?, handler: Handler?): Boolean {
        if (failDispatch) throw IllegalStateException("Gesture unavailable")
        return super.dispatchGesture(gesture, callback, handler)
    }
}

fun accessibilityService(): TestAccessibilityService =
    Robolectric.buildService(TestAccessibilityService::class.java).create().get()

fun accessibilityNode(packageName: String = "target.app", vararg actions: Int): AccessibilityNodeInfo =
    AccessibilityNodeInfo.obtain().apply {
        this.packageName = packageName
        isVisibleToUser = true
        actions.forEach { addAction(AccessibilityNodeInfo.AccessibilityAction(it, "test")) }
    }

fun accessibilityWindow(id: Int, type: Int = AccessibilityWindowInfo.TYPE_APPLICATION,
    root: AccessibilityNodeInfo? = null, active: Boolean = false, focused: Boolean = false, layer: Int = 0,
): AccessibilityWindowInfo = AccessibilityWindowInfo.obtain().also { window ->
    shadowOf(window).apply {
        setId(id)
        setType(type)
        setRoot(root)
        setActive(active)
        setFocused(focused)
        setLayer(layer)
    }
}
