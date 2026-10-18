package com.example.touchcursor.input

import com.example.touchcursor.model.ScreenEdge
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeSwipeDetectorTest {
    private var reveals = 0
    private var hides = 0
    private var taps = 0
    private val detector = EdgeSwipeDetector(32f, 8f, { reveals++ }, { hides++ }, { taps++ }, outwardThreshold = 4f)

    @Test fun inwardSwipesWorkOnEachEdgeOnlyOnRelease() {
        for ((edge, end) in listOf(ScreenEdge.LEFT to (140f to 100f), ScreenEdge.RIGHT to (60f to 100f),
            ScreenEdge.TOP to (100f to 140f), ScreenEdge.BOTTOM to (100f to 60f))) {
            detector.edge = edge
            detector.down(100f, 100f)
            detector.move(end.first, end.second)
            val before = reveals
            assertEquals(edge.ordinal, before)
            detector.up(end.first, end.second)
            assertEquals(before + 1, reveals)
        }
    }

    @Test fun movementAlongTheEdgeAndShortInwardSwipesDoNotReveal() {
        detector.edge = ScreenEdge.RIGHT
        detector.down(100f, 100f)
        detector.up(90f, 160f)
        detector.down(100f, 100f)
        detector.up(85f, 100f)
        assertEquals(0, reveals)
        assertEquals(0, taps)
    }

    @Test fun shortOutwardSwipeCanCloseFromANarrowEdgeStrip() {
        detector.edge = ScreenEdge.LEFT
        detector.down(7f, 100f)
        detector.up(2f, 100f)
        assertEquals(1, hides)
        assertEquals(0, reveals)
    }

    @Test fun tapAndCancelledMultitouchAreDistinctFromSwipe() {
        detector.down(100f, 100f)
        detector.up(101f, 101f)
        assertEquals(1, taps)
        detector.down(100f, 100f)
        detector.cancel()
        detector.up(0f, 100f)
        assertEquals(0, reveals)
        assertEquals(0, hides)
    }

    @Test fun returningAfterALongDragDoesNotBecomeATap() {
        detector.down(100f, 100f)
        detector.move(100f, 150f)
        detector.up(100f, 100f)
        assertEquals(0, taps)
    }
}
