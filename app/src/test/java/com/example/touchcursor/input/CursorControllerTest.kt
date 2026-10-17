package com.example.touchcursor.input

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorControllerTest {
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
