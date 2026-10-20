package com.example.touchcursor.input

import android.os.Looper
import com.example.touchcursor.testing.ConfigurableAccessibilityServiceShadow
import com.example.touchcursor.testing.accessibilityService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ConfigurableAccessibilityServiceShadow::class])
class GestureInjectorTest {
    private val service = accessibilityService()
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)
    private val injector = GestureInjector(service)
    private val results = mutableListOf<Boolean>()

    @Before fun allowDispatch() { platform.setCanDispatchGestures(true) }

    private fun dragPath() = android.graphics.Path().apply { moveTo(100f, 200f); lineTo(250f, 350f) }

    @Test fun holdThenDragContinuesTheSamePointerBeforeReleasing() {
        injector.injectPath(dragPath(), 300, 800, results::add)
        val hold = platform.gesturesDispatched.single()
        assertTrue(hold.description().getStroke(0).willContinue())
        assertEquals(800L, hold.description().getStroke(0).duration)
        hold.callback().onCompleted(hold.description())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(799))
        assertEquals(1, platform.gesturesDispatched.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(2, platform.gesturesDispatched.size)
        val drag = platform.gesturesDispatched.last()
        assertFalse(drag.description().getStroke(0).willContinue())
        assertEquals(300L, drag.description().getStroke(0).duration)
        val bounds = android.graphics.RectF()
        drag.description().getStroke(0).path.computeBounds(bounds, true)
        assertEquals(android.graphics.RectF(100f, 200f, 250f, 350f), bounds)
        assertTrue(results.isEmpty())
        hold.callback().onCompleted(hold.description())
        hold.callback().onCancelled(hold.description())
        assertEquals(2, platform.gesturesDispatched.size)
        assertTrue(results.isEmpty())
        drag.callback().onCompleted(drag.description())
        assertEquals(listOf(true), results)
    }

    @Test fun scrollDispatchesMovementWithoutAnInitialHold() {
        injector.injectPath(dragPath(), 250, 0, results::add)
        val scroll = platform.gesturesDispatched.single()
        assertFalse(scroll.description().getStroke(0).willContinue())
        assertEquals(250L, scroll.description().getStroke(0).duration)
        scroll.callback().onCompleted(scroll.description())
        assertEquals(listOf(true), results)
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun dragClampedAtScreenEdgeKeepsTheRealHoldCoordinate() {
        injector.injectPath(android.graphics.Path().apply { moveTo(319f, 479f); lineTo(319f, 479f) }, 200, 800, results::add)
        val hold = platform.gesturesDispatched.single()
        val bounds = android.graphics.RectF()
        hold.description().getStroke(0).path.computeBounds(bounds, true)
        assertEquals(319f, bounds.left, 0f)
        assertEquals(479f, bounds.top, 0f)
        hold.callback().onCompleted(hold.description())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        val end = platform.gesturesDispatched.last()
        end.callback().onCompleted(end.description())
        assertEquals(listOf(true), results)
    }

    @Test fun closingOrLosingCallbackDuringHoldReleasesPointerWithoutMoving() {
        injector.injectPath(dragPath(), 300, 800, results::add)
        val hold = platform.gesturesDispatched.single()
        injector.close()
        assertEquals(2, platform.gesturesDispatched.size)
        val release = platform.gesturesDispatched.last().description().getStroke(0)
        assertFalse(release.willContinue())
        assertEquals(1L, release.duration)
        hold.callback().onCompleted(hold.description())
        assertEquals(2, platform.gesturesDispatched.size)
        assertTrue(results.isEmpty())
    }

    @Test fun holdWatchdogReleasesPointerAndReportsFailure() {
        injector.injectPath(dragPath(), 300, 800, results::add)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2800))
        assertEquals(listOf(false), results)
        assertEquals(2, platform.gesturesDispatched.size)
        assertEquals(1L, platform.gesturesDispatched.last().description().getStroke(0).duration)
    }

    @Test fun closeAfterEarlyHoldCallbackCancelsScheduledMovement() {
        injector.injectPath(dragPath(), 300, 800, results::add)
        val hold = platform.gesturesDispatched.single()
        hold.callback().onCompleted(hold.description())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        injector.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(2, platform.gesturesDispatched.size)
        assertEquals(1L, platform.gesturesDispatched.last().description().getStroke(0).duration)
        assertTrue(results.isEmpty())
    }

    @Test fun cancellingPendingHoldPreventsMovementAndAllowsTheNextGesture() {
        injector.injectPath(dragPath(), 300, 800, results::add)
        val hold = platform.gesturesDispatched.single()
        hold.callback().onCompleted(hold.description())
        injector.cancelPendingHold()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(listOf(false), results)
        assertEquals(2, platform.gesturesDispatched.size)
        injector.inject(10f, 20f, 60, results::add)
        assertEquals(3, platform.gesturesDispatched.size)
    }

    @Test @Config(sdk = [24]) fun android7SupportsScrollButRejectsContinuedHoldDrag() {
        injector.injectPath(dragPath(), 300, 800, results::add)
        assertEquals(listOf(false), results)
        assertTrue(platform.gesturesDispatched.isEmpty())
        injector.injectPath(dragPath(), 300, 0, results::add)
        assertEquals(1, platform.gesturesDispatched.size)
    }

    @Test fun dispatchesOneStrokeWithTheRequestedPositionAndDuration() {
        injector.inject(123f, 456f, 650, results::add)
        val gesture = platform.gesturesDispatched.single().description()
        assertEquals(1, gesture.strokeCount)
        val stroke = gesture.getStroke(0)
        assertEquals(650L, stroke.duration)
        assertEquals(0L, stroke.startTime)
        // A zero-length path is a stationary touch, verified by its computed bounds.
        val bounds = android.graphics.RectF()
        stroke.path.computeBounds(bounds, true)
        assertEquals(123f, bounds.left, 0f)
        assertEquals(456f, bounds.top, 0f)
        assertTrue(results.isEmpty())
    }

    @Test fun completionIsReportedOnceEvenIfThePlatformRepeatsItsCallback() {
        injector.inject(10f, 20f, 60, results::add)
        val sent = platform.gesturesDispatched.single()
        sent.callback().onCompleted(sent.description())
        sent.callback().onCompleted(sent.description())
        sent.callback().onCancelled(sent.description())
        assertEquals(listOf(true), results)
    }

    @Test fun concurrentRequestsAreRejectedWithoutReplacingThePendingGesture() {
        injector.inject(10f, 20f, 60, results::add)
        injector.inject(30f, 40f, 60, results::add)
        assertEquals(1, platform.gesturesDispatched.size)
        assertEquals(listOf(false), results)
        val sent = platform.gesturesDispatched.single()
        sent.callback().onCompleted(sent.description())
        assertEquals(listOf(false, true), results)
    }

    @Test fun platformRejectionReleasesTheBusyState() {
        platform.setCanDispatchGestures(false)
        injector.inject(10f, 20f, 60, results::add)
        platform.setCanDispatchGestures(true)
        injector.inject(10f, 20f, 60, results::add)
        assertEquals(listOf(false), results)
        assertEquals(1, platform.gesturesDispatched.size)
    }

    @Test fun platformExceptionIsReportedAsFailureAndAllowsARetry() {
        platform.failDispatch = true
        injector.inject(10f, 20f, 60, results::add)
        platform.failDispatch = false
        injector.inject(10f, 20f, 60, results::add)
        assertEquals(listOf(false), results)
        assertEquals(1, platform.gesturesDispatched.size)
    }

    @Test fun cancellationIsReportedAndAllowsANewGesture() {
        injector.inject(10f, 20f, 60, results::add)
        val sent = platform.gesturesDispatched.single()
        sent.callback().onCancelled(sent.description())
        injector.inject(30f, 40f, 60, results::add)
        assertEquals(listOf(false), results)
        assertEquals(2, platform.gesturesDispatched.size)
    }

    @Test fun missingCallbackTimesOutOnlyAfterDurationPlusRecoveryAllowance() {
        injector.inject(10f, 20f, 650, results::add)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2649))
        assertTrue(results.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf(false), results)
        injector.inject(10f, 20f, 60, results::add)
        assertEquals(2, platform.gesturesDispatched.size)
    }

    @Test fun oldCallbackCannotCompleteANewerGestureAfterTimeout() {
        injector.inject(10f, 20f, 60, results::add)
        val old = platform.gesturesDispatched.single()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2060))
        injector.inject(30f, 40f, 60, results::add)
        old.callback().onCompleted(old.description())
        assertEquals(listOf(false), results)
        val current = platform.gesturesDispatched.last()
        current.callback().onCompleted(current.description())
        assertEquals(listOf(false, true), results)
    }

    @Test fun successfulCompletionCancelsTheWatchdog() {
        injector.inject(10f, 20f, 60, results::add)
        val sent = platform.gesturesDispatched.single()
        sent.callback().onCompleted(sent.description())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(listOf(true), results)
    }

    @Test fun closingSuppressesPendingCallbacksAndWatchdogAndRejectsNewInput() {
        injector.inject(10f, 20f, 60, results::add)
        val sent = platform.gesturesDispatched.single()
        injector.close()
        injector.close()
        sent.callback().onCompleted(sent.description())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertTrue(results.isEmpty())
        injector.inject(10f, 20f, 60, results::add)
        assertEquals(listOf(false), results)
        assertEquals(1, platform.gesturesDispatched.size)
    }
}
