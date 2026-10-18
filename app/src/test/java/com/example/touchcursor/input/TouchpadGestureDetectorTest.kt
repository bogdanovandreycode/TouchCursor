package com.example.touchcursor.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchpadGestureDetectorTest {
    private var taps = 0
    private var holds = 0
    private var movedX = 0f
    private var movedY = 0f
    private val detector = TouchpadGestureDetector(8f, 80f, 300, 500,
        { dx, dy -> movedX += dx; movedY += dy }, { taps++ }, { holds++ })

    private fun tap(time: Long, x: Float = 300f, y: Float = 400f) {
        detector.down(x, y, time)
        detector.up(x, y, time + 60)
    }

    @Test fun upAndMoveWithoutDownDoNotInjectOrMove() {
        detector.up(20f, 30f, 1000)
        detector.move(100f, 100f)
        assertEquals(0, taps + holds)
        assertEquals(0f, movedX + movedY, 0f)
    }

    @Test fun longPressIsRecognizedExactlyAtTheTimeout() {
        detector.down(20f, 30f, 100)
        detector.up(20f, 30f, 600)
        assertEquals(1, holds)
        assertEquals(0, taps)
    }

    @Test fun releasingOneMillisecondBeforeLongPressDoesNotHold() {
        detector.down(20f, 30f, 100)
        detector.up(20f, 30f, 599)
        assertEquals(0, holds)
    }

    @Test fun exactDoubleTapTimeAndDistanceBoundariesAreAccepted() {
        tap(0, 100f, 100f)
        detector.down(180f, 100f, 360)
        detector.up(180f, 100f, 400)
        assertEquals(1, taps)
    }

    @Test fun doubleTapOutsideTimeBoundaryIsNotAccepted() {
        tap(0)
        tap(361)
        assertEquals(0, taps)
    }

    @Test fun doubleTapOutsideDistanceBoundaryIsNotAccepted() {
        tap(0, 100f, 100f)
        tap(100, 181f, 100f)
        assertEquals(0, taps)
    }

    @Test fun exactTouchSlopDoesNotTurnAHoldIntoMovement() {
        detector.down(100f, 100f, 0)
        detector.up(108f, 100f, 500)
        assertEquals(1, holds)
        assertEquals(0f, movedX, 0f)
    }

    @Test fun movementOnlyOnReleaseStillCancelsClickAndHold() {
        tap(0)
        detector.down(300f, 400f, 100)
        detector.up(320f, 400f, 1000)
        assertEquals(0, taps + holds)
        assertEquals(20f, movedX, 0f)
    }

    @Test fun duplicateUpDoesNotInjectTheSameGestureTwice() {
        tap(0)
        tap(100)
        detector.up(300f, 400f, 180)
        assertEquals(1, taps)
    }

    @Test fun cancelledHoldIsIgnoredButNextFreshHoldWorks() {
        detector.down(100f, 100f, 0)
        detector.cancel()
        detector.move(500f, 500f)
        detector.up(100f, 100f, 800)
        detector.down(100f, 100f, 1000)
        detector.up(100f, 100f, 1500)
        assertEquals(1, holds)
        assertEquals(0f, movedX + movedY, 0f)
    }

    @Test fun timeGoingBackwardsCannotCompleteADoubleTap() {
        tap(1000)
        tap(500)
        assertEquals(0, taps)
    }

    @Test fun singleTapDoesNotInjectAndDoubleTapInjectsOnlyOnSecondRelease() {
        tap(0)
        assertEquals(0, taps)
        detector.down(300f, 400f, 160)
        assertEquals(0, taps)
        detector.up(300f, 400f, 220)
        assertEquals(1, taps)
        assertEquals(0f, movedX, 0f)
    }

    @Test fun dragAccumulatesRelativeMotionWithoutClickOrLongPress() {
        detector.down(100f, 200f, 0)
        detector.move(120f, 210f)
        detector.move(140f, 230f)
        detector.up(150f, 235f, 1000)
        assertEquals(50f, movedX, 0f)
        assertEquals(35f, movedY, 0f)
        assertEquals(0, taps)
        assertEquals(0, holds)
    }

    @Test fun holdWaitsUntilReleaseAndToleratesSmallJitter() {
        detector.down(100f, 200f, 0)
        detector.move(102f, 202f)
        assertEquals(0, holds)
        detector.up(102f, 202f, 700)
        assertEquals(1, holds)
        assertEquals(0f, movedX, 0f)
    }

    @Test fun extraPointerOrCancellationNeverInjects() {
        tap(0)
        detector.down(100f, 200f, 100)
        detector.cancel()
        detector.up(100f, 200f, 1200)
        assertEquals(0, taps)
        assertEquals(0, holds)
        tap(1300)
        assertEquals(0, taps)
    }

    @Test fun tapsTooFarApartInTimeOrSpaceDoNotClick() {
        tap(0)
        tap(1000)
        tap(1160, 600f, 700f)
        assertEquals(0, taps)
    }

    @Test fun dragBetweenTapsAndModeResetClearPendingTap() {
        tap(0)
        detector.down(300f, 400f, 100)
        detector.move(350f, 400f)
        detector.up(350f, 400f, 200)
        tap(250)
        assertEquals(0, taps)
        detector.reset()
        tap(350)
        assertEquals(0, taps)
    }

    @Test fun holdAsSecondTapIsOnlyALongPress() {
        tap(0)
        detector.down(300f, 400f, 160)
        detector.up(300f, 400f, 860)
        assertEquals(0, taps)
        assertEquals(1, holds)
    }
}
