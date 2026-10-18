package com.example.touchcursor.overlay

import android.graphics.Canvas
import android.view.View
import com.example.touchcursor.model.CursorAppearance
import com.example.touchcursor.model.CursorShape
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CursorOverlayViewTest {
    private val view = CursorOverlayView(RuntimeEnvironment.getApplication())

    @Test fun arrowHotspotTracksTheTipAtEverySupportedSize() {
        val density = view.resources.displayMetrics.density
        listOf(16, 38, 64, 96).forEach { size ->
            view.appearance = CursorAppearance(shape = CursorShape.ARROW, sizeDp = size)
            val tip = 3f * density + 3f * density * size / 38f
            assertEquals(tip, view.hotspotX, .001f)
            assertEquals(tip, view.hotspotY, .001f)
        }
    }

    @Test @Config(qualifiers = "xhdpi") fun circularCursorHotspotsTrackTheCenterAtDifferentDensity() {
        val density = view.resources.displayMetrics.density
        assertEquals(2f, density, .001f)
        listOf(CursorShape.CIRCLE, CursorShape.CROSSHAIR).forEach { shape ->
            listOf(16, 38, 96).forEach { size ->
                view.appearance = CursorAppearance(shape = shape, sizeDp = size)
                assertEquals(3f * density + 16f * density * size / 38f, view.hotspotX, .001f)
                assertEquals(3f * density + 19f * density * size / 38f, view.hotspotY, .001f)
            }
        }
    }

    @Test fun allShapesCanDrawWithAndWithoutOutlineAndNeverBecomeAccessibilityTargets() {
        val canvas = Canvas()
        CursorShape.entries.forEach { shape ->
            listOf(0, 6).forEach { outline ->
                view.appearance = CursorAppearance(shape = shape, sizeDp = 16, outlineWidthDp = outline)
                view.layout(0, 0, 80, 100)
                view.draw(canvas)
            }
        }
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.importantForAccessibility)
        assertFalse(view.isClickable)
    }
}
