package com.example.touchcursor.input

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorControllerTest {
    @Test fun resizingDoesNotRecenterAnAlreadyValidCursor() {
        val cursor = CursorController()
        cursor.setBounds(0f, 0f, 100f, 100f)
        cursor.move(10f, -5f)
        val x = cursor.x
        val y = cursor.y
        cursor.setBounds(0f, 0f, 500f, 500f)
        assertEquals(x, cursor.x, 0f)
        assertEquals(y, cursor.y, 0f)
    }

    @Test fun singlePixelAndInvertedBoundsNeverProduceAnOutOfBoundsCursor() {
        val cursor = CursorController()
        cursor.setBounds(20f, 30f, 21f, 31f)
        cursor.move(100f, -100f)
        assertEquals(20f, cursor.x, 0f)
        assertEquals(30f, cursor.y, 0f)
        cursor.setBounds(50f, 60f, 40f, 30f)
        assertEquals(50f, cursor.x, 0f)
        assertEquals(60f, cursor.y, 0f)
    }

    @Test fun movingBeforeBoundsAreKnownIsSafe() {
        val cursor = CursorController()
        cursor.move(100f, -100f)
        assertEquals(0f, cursor.x, 0f)
        assertEquals(0f, cursor.y, 0f)
        cursor.setBounds(10f, 20f, 110f, 220f)
        assertEquals(59.5f, cursor.x, 0f)
        assertEquals(119.5f, cursor.y, 0f)
    }

    @Test fun fractionalSensitivityAccumulatesWithoutRoundingEachMove() {
        val cursor = CursorController()
        cursor.setBounds(0f, 0f, 100f, 100f)
        cursor.sensitivity = 0.25f
        repeat(4) { cursor.move(1f, -1f) }
        assertEquals(50.5f, cursor.x, 0f)
        assertEquals(48.5f, cursor.y, 0f)
    }

    @Test fun startsCenteredInUsableBoundsAndMovesRelatively() {
        val cursor = CursorController()
        cursor.setBounds(10f, 40f, 210f, 440f)
        assertEquals(109.5f, cursor.x, 0.01f)
        assertEquals(239.5f, cursor.y, 0.01f)
        cursor.sensitivity = 2f
        cursor.move(10f, -5f)
        assertEquals(129.5f, cursor.x, 0.01f)
        assertEquals(229.5f, cursor.y, 0.01f)
    }

    @Test fun clampsAndPreservesAValidPositionAfterRotation() {
        val cursor = CursorController()
        cursor.setBounds(0f, 24f, 400f, 800f)
        cursor.move(10000f, 10000f)
        assertEquals(399f, cursor.x, 0f)
        assertEquals(799f, cursor.y, 0f)
        cursor.setBounds(0f, 24f, 800f, 400f)
        assertEquals(399f, cursor.x, 0f)
        assertEquals(399f, cursor.y, 0f)
        cursor.move(-10000f, -10000f)
        assertEquals(0f, cursor.x, 0f)
        assertEquals(24f, cursor.y, 0f)
    }
}
