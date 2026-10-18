package com.example.touchcursor.overlay

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.example.touchcursor.input.TouchpadGestureDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TouchCaptureOverlayViewTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private var taps = 0
    private var holds = 0
    private var exits = 0
    private var movement = 0f
    private val shortcut = Rect(200, 200, 260, 260)
    private val detector = TouchpadGestureDetector(8f, 80f, 300, 500,
        { dx, dy -> movement += kotlin.math.hypot(dx, dy) }, { taps++ }, { holds++ })
    private val view = TouchCaptureOverlayView(activity, detector,
        { x, y -> shortcut.contains(x.toInt(), y.toInt()) }, { exits++ })

    @Before fun attach() { activity.setContentView(view); shadowOf(Looper.getMainLooper()).idle() }

    private fun event(action: Int, time: Long, x: Float = 100f, y: Float = 100f) {
        MotionEvent.obtain(0, time, action, x, y, 0).also {
            try { assertTrue(view.onTouchEvent(it)) } finally { it.recycle() }
        }
    }

    private fun tap(time: Long, x: Float = 100f, y: Float = 100f) {
        event(MotionEvent.ACTION_DOWN, time, x, y)
        event(MotionEvent.ACTION_UP, time + 40, x, y)
    }

    @Test fun singleTapIsCapturedAndDoubleTapInjectsOnce() {
        tap(0)
        assertEquals(0, taps)
        tap(100)
        assertEquals(1, taps)
        assertEquals(0, exits + holds)
    }

    @Test fun holdIsRequestedOnlyOnRelease() {
        event(MotionEvent.ACTION_DOWN, 0)
        event(MotionEvent.ACTION_MOVE, 700)
        assertEquals(0, holds)
        event(MotionEvent.ACTION_UP, 800)
        assertEquals(1, holds)
        assertEquals(0, taps + exits)
    }

    @Test fun swipeMovesCursorAndDoesNotClick() {
        event(MotionEvent.ACTION_DOWN, 0)
        event(MotionEvent.ACTION_MOVE, 100, 140f, 100f)
        event(MotionEvent.ACTION_UP, 200, 140f, 100f)
        assertEquals(40f, movement, 0f)
        assertEquals(0, taps + holds + exits)
    }

    @Test fun nativeShortcutTapExitsAfterReleaseWithoutMovingOrInjecting() {
        event(MotionEvent.ACTION_DOWN, 0, 220f, 220f)
        assertEquals(0, exits)
        event(MotionEvent.ACTION_UP, 40, 220f, 220f)
        assertEquals(0, exits)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, exits)
        assertEquals(0, taps + holds)
        assertEquals(0f, movement, 0f)
    }

    @Test fun dragAwayFromTheNativeShortcutDoesNotExitOrInject() {
        val distance = ViewConfiguration.get(activity).scaledTouchSlop + 100f
        event(MotionEvent.ACTION_DOWN, 0, 220f, 220f)
        event(MotionEvent.ACTION_MOVE, 100, 220f + distance, 220f)
        event(MotionEvent.ACTION_UP, 800, 220f + distance, 220f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, exits + taps + holds)
    }

    @Test fun liftingOutsideShortcutBoundsDoesNotActivateIt() {
        event(MotionEvent.ACTION_DOWN, 0, 259f, 220f)
        event(MotionEvent.ACTION_UP, 40, 261f, 220f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, exits + taps + holds)
    }

    @Test fun cancellationClearsPendingShortcutExit() {
        event(MotionEvent.ACTION_DOWN, 0, 220f, 220f)
        event(MotionEvent.ACTION_CANCEL, 30, 220f, 220f)
        event(MotionEvent.ACTION_UP, 40, 220f, 220f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, exits + taps + holds)
    }

    private fun multi(action: Int, count: Int, time: Long) {
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(2) { index -> MotionEvent.PointerCoords().apply { x = 100f + index * 40; y = 100f; pressure = 1f; size = 1f } }
        val event = MotionEvent.obtain(0, time, action, count, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun twoFingerExitWaitsForTheLastPointerAndNeverClicksOrMoves() {
        multi(MotionEvent.ACTION_DOWN, 1, 0)
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 20)
        multi(MotionEvent.ACTION_MOVE, 2, 30)
        multi(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 40)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, exits)
        multi(MotionEvent.ACTION_UP, 1, 60)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, exits)
        assertEquals(0, taps + holds)
        assertEquals(0f, movement, 0f)
    }

    @Test fun cancellingMultiTouchDoesNotLeaveAnExitArmedForTheNextGesture() {
        multi(MotionEvent.ACTION_DOWN, 1, 0)
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 20)
        multi(MotionEvent.ACTION_CANCEL, 2, 40)
        tap(100)
        tap(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, exits)
        assertEquals(1, taps)
    }

    @Test fun detachingClearsPendingTapRecognition() {
        tap(0)
        activity.setContentView(android.view.View(activity))
        activity.setContentView(view)
        tap(100)
        assertEquals(0, taps + holds + exits)
    }
}
