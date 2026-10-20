package com.example.touchcursor.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.touchcursor.R
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
class SystemShortcutTrackerTest {
    private val service = accessibilityService()
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)
    private val tracker = SystemShortcutTracker(service)
    private val bounds = Rect(20, 80, 70, 140)
    private fun target() = accessibilityNode("com.android.systemui").apply {
        isClickable = true
        contentDescription = service.getString(R.string.accessibility_service_label)
        setBoundsInScreen(bounds)
    }
    private fun setRoot(root: AccessibilityNodeInfo, type: Int = AccessibilityWindowInfo.TYPE_SYSTEM) {
        platform.setWindows(listOf(accessibilityWindow(9, type, root)))
    }

    @Test fun findsLabeledVisibleClickableSystemTargetAndWindowId() {
        setRoot(target())
        assertEquals(bounds, tracker.refresh()); assertEquals(9, tracker.windowId)
    }

    @Test fun nestedTextLabelInAccessibilityOverlayIsSupported() {
        val root = accessibilityNode("com.android.systemui")
        val child = target().apply { text = contentDescription; contentDescription = null }
        shadowOf(root).addChild(child)
        setRoot(root, AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
        assertEquals(bounds, tracker.refresh())
    }

    @Test fun applicationWindowsAndOtherPackagesCannotImpersonateTheShortcut() {
        setRoot(target(), AccessibilityWindowInfo.TYPE_APPLICATION)
        assertNull(tracker.refresh())
        setRoot(target().apply { packageName = "other.app" })
        assertNull(tracker.refresh()); assertEquals(-1, tracker.windowId)
    }

    @Test fun hiddenUnclickableUnlabeledAndEmptyTargetsAreIgnored() {
        listOf<(AccessibilityNodeInfo) -> Unit>(
            { it.isVisibleToUser = false }, { it.isClickable = false },
            { it.contentDescription = "Other service" }, { it.setBoundsInScreen(Rect()) },
        ).forEach { modify -> setRoot(target().also(modify)); assertNull(tracker.refresh()) }
    }

    @Test fun missingOrUnavailableWindowsClearThePreviousTarget() {
        setRoot(target()); assertNotNull(tracker.refresh())
        platform.setWindows(emptyList())
        assertNull(tracker.refresh()); assertEquals(-1, tracker.windowId)
        setRoot(target()); tracker.refresh()
        platform.failWindowRead = true
        assertNull(tracker.refresh()); assertEquals(-1, tracker.windowId)
    }

    @Test fun traversalBudgetStopsBeforeDeepTargets() {
        val root = accessibilityNode("com.android.systemui")
        var parent = root
        repeat(120) {
            val child = accessibilityNode("com.android.systemui")
            shadowOf(parent).addChild(child); parent = child
        }
        shadowOf(parent).addChild(target())
        setRoot(root)
        assertNull(tracker.refresh())
    }
}
