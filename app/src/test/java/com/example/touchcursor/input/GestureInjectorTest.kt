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
