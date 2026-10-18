package com.example.touchcursor.overlay

import android.view.MotionEvent
import com.example.touchcursor.model.ScreenEdge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EdgeHandleViewTest {
    private var revealed = 0
    private var hidden = 0
    private val view = EdgeHandleView(RuntimeEnvironment.getApplication(), { revealed++ }, { hidden++ })
    private fun event(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
    }

    @Test fun closedHandleRequiresSwipeButExpandedHandleTapHidesControls() {
        view.configure(ScreenEdge.RIGHT, false)
        event(MotionEvent.ACTION_DOWN, 50f, 50f); event(MotionEvent.ACTION_UP, 50f, 50f)
        assertEquals(0, revealed); assertEquals(0, hidden)
        assertEquals("Swipe inward to reveal Touch Cursor controls", view.contentDescription)
        view.configure(ScreenEdge.RIGHT, true)
        event(MotionEvent.ACTION_DOWN, 50f, 50f); event(MotionEvent.ACTION_UP, 50f, 50f)
        assertEquals(1, hidden)
        assertEquals("Hide Touch Cursor controls", view.contentDescription)
    }

    @Test fun inwardSwipeRevealsFromEveryScreenEdge() {
        val distance = 50 * view.resources.displayMetrics.density
        mapOf(ScreenEdge.LEFT to (distance to 0f), ScreenEdge.RIGHT to (-distance to 0f),
            ScreenEdge.TOP to (0f to distance), ScreenEdge.BOTTOM to (0f to -distance)).forEach { (edge, delta) ->
            view.configure(edge, false)
            event(MotionEvent.ACTION_DOWN, 100f, 100f)
            event(MotionEvent.ACTION_MOVE, 100f + delta.first, 100f + delta.second)
            event(MotionEvent.ACTION_UP, 100f + delta.first, 100f + delta.second)
        }
        assertEquals(4, revealed); assertEquals(0, hidden)
    }

    @Test fun changingEdgeOrExpansionCancelsAnInProgressSwipe() {
        view.configure(ScreenEdge.RIGHT, false)
        event(MotionEvent.ACTION_DOWN, 100f, 100f)
        event(MotionEvent.ACTION_MOVE, 50f, 100f)
        view.configure(ScreenEdge.LEFT, false)
        event(MotionEvent.ACTION_UP, 50f, 100f)
        view.configure(ScreenEdge.RIGHT, false)
        event(MotionEvent.ACTION_DOWN, 100f, 100f)
        view.configure(ScreenEdge.RIGHT, true)
        event(MotionEvent.ACTION_UP, 50f, 100f)
        assertEquals(0, revealed); assertEquals(0, hidden)
    }

    @Test fun cancelledOrMultiTouchGestureDoesNotRevealControls() {
        listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN).forEach { cancel ->
            view.configure(ScreenEdge.RIGHT, false)
            event(MotionEvent.ACTION_DOWN, 100f, 100f)
            event(MotionEvent.ACTION_MOVE, 50f, 100f)
            event(cancel, 50f, 100f)
            event(MotionEvent.ACTION_UP, 50f, 100f)
        }
        assertEquals(0, revealed); assertEquals(0, hidden)
    }

    @Test fun accessibilityClickCanRevealAndHideControls() {
        view.configure(ScreenEdge.BOTTOM, false); assertTrue(view.performClick())
        view.configure(ScreenEdge.BOTTOM, true); assertTrue(view.performClick())
        assertEquals(1, revealed); assertEquals(1, hidden)
    }
}
