package com.example.touchcursor.service

import android.view.accessibility.AccessibilityWindowInfo
import com.example.touchcursor.testing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ConfigurableAccessibilityServiceShadow::class])
class ForegroundAppTrackerTest {
    private val service = accessibilityService()
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)
    private val tracker = ForegroundAppTracker(service)
    private fun app(id: Int, name: String, active: Boolean = false, focused: Boolean = false, layer: Int = 0) =
        accessibilityWindow(id, root = accessibilityNode(name), active = active, focused = focused, layer = layer)

    @Test fun activeApplicationWinsOverFocusedAndHigherLayers() {
        platform.setWindows(listOf(app(1, "high", layer = 100), app(2, "focused", focused = true), app(3, "active", active = true)))
        assertEquals("active", tracker.refresh())
    }

    @Test fun focusedApplicationWinsWhenNoneIsActive() {
        platform.setWindows(listOf(app(1, "high", layer = 100), app(2, "focused", focused = true)))
        assertEquals("focused", tracker.refresh())
    }

    @Test fun highestApplicationLayerIsTheFallback() {
        platform.setWindows(listOf(app(1, "low", layer = 1), app(2, "high", layer = 2)))
        assertEquals("high", tracker.refresh())
    }

    @Test fun keyboardSystemUiAndAccessibilityOverlaysNeverReplaceTheApp() {
        platform.setWindows(listOf(app(1, "game")))
        assertEquals("game", tracker.refresh())
        platform.setWindows(listOf(AccessibilityWindowInfo.TYPE_INPUT_METHOD, AccessibilityWindowInfo.TYPE_SYSTEM,
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY).mapIndexed { index, type ->
            accessibilityWindow(index + 2, type, accessibilityNode("other"), active = true, layer = 100)
        })
        assertEquals("game", tracker.refresh("other", 2))
    }

    @Test fun eventHintMustMatchTheSelectedWindowAndRootHasPriority() {
        platform.setWindows(listOf(accessibilityWindow(7)))
        assertNull(tracker.refresh("wrong", 8))
        platform.setWindows(listOf(accessibilityWindow(7)))
        assertEquals("hint", tracker.refresh("hint", 7))
        platform.setWindows(listOf(app(7, "root")))
        assertEquals("root", tracker.refresh("hint", 7))
    }

    @Test fun missingRootRetainsPackageOnlyForTheSameWindow() {
        platform.setWindows(listOf(app(1, "game")))
        tracker.refresh()
        platform.setWindows(listOf(accessibilityWindow(1)))
        assertEquals("game", tracker.refresh())
        platform.setWindows(listOf(accessibilityWindow(2)))
        assertNull(tracker.refresh())
    }

    @Test fun transientEmptyOrUnavailableWindowsRetainLastApplication() {
        platform.setWindows(listOf(app(1, "game"))); tracker.refresh()
        platform.setWindows(emptyList())
        assertEquals("game", tracker.refresh())
        platform.failWindowRead = true
        assertEquals("game", tracker.refresh())
    }
}
