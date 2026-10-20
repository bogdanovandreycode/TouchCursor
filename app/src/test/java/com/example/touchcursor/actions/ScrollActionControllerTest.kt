package com.example.touchcursor.actions

import android.graphics.Rect
import android.os.Looper
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ConfigurableAccessibilityServiceShadow::class])
class ScrollActionControllerTest {
    private val service = accessibilityService()
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)
    private val controller = ScrollActionController(service)
    private val forward = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
    private val backward = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
    private val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
    private val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
    private val left = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
    private val right = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
    private fun node(vararg actions: Int) = accessibilityNode("browser.app", *actions).apply {
        setBoundsInScreen(Rect(0, 0, 400, 800))
    }
    private fun show(root: AccessibilityNodeInfo) {
        platform.setWindows(listOf(accessibilityWindow(1, root = root, active = true)))
    }
    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200)) }
    @After fun cleanup() { controller.end() }

    @Test fun scrollsDuringPhysicalMovementWithoutGestureInjection() {
        val target = node(forward, backward)
        show(target)
        assertTrue(controller.begin(100f, 100f))
        controller.move(0f, -1000f)
        assertEquals(listOf(forward), shadowOf(target).performedActions)
        settle()
        controller.move(0f, 1000f)
        assertEquals(listOf(forward, backward), shadowOf(target).performedActions)
        assertTrue(platform.gesturesDispatched.isEmpty())
    }

    @Test fun prefersDirectionalActionsAndSupportsHorizontalScrolling() {
        val target = node(forward, backward, down, up, left, right)
        show(target); assertTrue(controller.begin(100f, 100f))
        for ((dx, dy) in listOf(0f to -1000f, 0f to 1000f, -1000f to 0f, 1000f to 0f)) {
            controller.move(dx, dy); settle()
        }
        assertEquals(listOf(down, up, right, left), shadowOf(target).performedActions)
    }

    @Test fun horizontalMotionDoesNotInvokeGenericVerticalScroll() {
        val target = node(forward)
        show(target); controller.begin(100f, 100f)
        controller.move(-1000f, 0f)
        assertTrue(shadowOf(target).performedActions.isEmpty())
    }

    @Test fun selectsDeepestScrollAreaUnderCursor() {
        val outer = node(forward)
        val inner = node(forward)
        shadowOf(outer).addChild(inner)
        show(outer); assertTrue(controller.begin(100f, 100f))
        controller.move(0f, -1000f)
        assertTrue(shadowOf(outer).performedActions.isEmpty())
        assertEquals(listOf(forward), shadowOf(inner).performedActions)
    }

    @Test fun ignoresOffCursorAndHiddenChildren() {
        val outer = node(forward)
        val offCursor = node(forward).apply { setBoundsInScreen(Rect(200, 200, 300, 300)) }
        val hidden = node(forward).apply { isVisibleToUser = false }
        shadowOf(outer).addChild(offCursor); shadowOf(outer).addChild(hidden)
        show(outer); assertTrue(controller.begin(100f, 100f))
        controller.move(0f, -1000f)
        assertEquals(listOf(forward), shadowOf(outer).performedActions)
        assertTrue(shadowOf(offCursor).performedActions.isEmpty())
        assertTrue(shadowOf(hidden).performedActions.isEmpty())
    }

    @Test fun throttledMotionIsCancelledOnRelease() {
        val target = node(forward)
        show(target); controller.begin(100f, 100f)
        controller.move(0f, -1000f)
        controller.move(0f, -1000f)
        assertEquals(listOf(forward), shadowOf(target).performedActions)
        controller.end(); settle()
        controller.move(0f, -1000f)
        assertEquals(listOf(forward), shadowOf(target).performedActions)
    }

    @Test fun throttledMotionRunsWhileHeldAndDirectionChangesReplacePendingMotion() {
        val target = node(forward, backward)
        show(target); controller.begin(100f, 100f)
        controller.move(0f, -1000f)
        controller.move(0f, -1000f)
        controller.move(0f, 2000f)
        settle()
        assertEquals(listOf(forward, backward), shadowOf(target).performedActions)
    }

    @Test fun jitterDoesNotScroll() {
        val target = node(forward)
        show(target); controller.begin(100f, 100f)
        controller.move(0f, -1f); controller.move(0f, 1f); settle()
        assertTrue(shadowOf(target).performedActions.isEmpty())
    }

    @Test fun missingUnsupportedAndOutsideTargetsDoNotStartScrolling() {
        assertFalse(controller.begin(100f, 100f))
        show(node()); assertFalse(controller.begin(100f, 100f))
        show(node(forward)); assertFalse(controller.begin(1000f, 1000f))
    }

    @Test fun overlaysCannotBeSelectedAndActiveApplicationWins() {
        val overlay = node(forward)
        val inactive = node(forward)
        val active = node(forward)
        platform.setWindows(listOf(
            accessibilityWindow(1, AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, overlay, active = true),
            accessibilityWindow(2, root = inactive, layer = 10),
            accessibilityWindow(3, root = active, active = true),
        ))
        assertTrue(controller.begin(100f, 100f)); controller.move(0f, -1000f)
        assertEquals(listOf(forward), shadowOf(active).performedActions)
        assertTrue(shadowOf(overlay).performedActions.isEmpty())
        assertTrue(shadowOf(inactive).performedActions.isEmpty())
    }

    @Test fun staleAndDisconnectedTargetsStopSafely() {
        val target = node(forward)
        shadowOf(target).setRefreshReturnValue(false)
        show(target); controller.begin(100f, 100f)
        controller.move(0f, -1000f)
        assertTrue(shadowOf(target).performedActions.isEmpty())
        platform.failWindowRead = true
        assertFalse(controller.begin(100f, 100f))
    }

    @Test fun actionFailureDoesNotFallBackToTouchInjection() {
        val target = node(forward)
        shadowOf(target).setOnPerformActionListener { _, _ -> throw IllegalStateException("Disconnected") }
        show(target); controller.begin(100f, 100f)
        controller.move(0f, -1000f)
        settle()
        assertTrue(platform.gesturesDispatched.isEmpty())
    }

    @Test fun changingForegroundWindowStopsThePreviousScrollTarget() {
        val previous = node(forward)
        show(previous); controller.begin(100f, 100f)
        val current = node(forward)
        platform.setWindows(listOf(accessibilityWindow(2, root = current, active = true)))
        controller.move(0f, -1000f)
        assertTrue(shadowOf(previous).performedActions.isEmpty())
        assertTrue(shadowOf(current).performedActions.isEmpty())
    }

    @Test fun traversalDepthIsBounded() {
        val root = node()
        var parent = root
        repeat(50) { val child = node(); shadowOf(parent).addChild(child); parent = child }
        shadowOf(parent).addChild(node(forward))
        show(root)
        assertFalse(controller.begin(100f, 100f))
    }
}
