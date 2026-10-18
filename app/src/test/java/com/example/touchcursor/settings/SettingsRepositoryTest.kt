package com.example.touchcursor.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.touchcursor.model.CursorAppearance
import com.example.touchcursor.model.CursorShape
import com.example.touchcursor.model.EdgeRevealConfig
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.model.ScreenEdge
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("touch_cursor", Context.MODE_PRIVATE)
    private val settings = SettingsRepository(context)

    @Before fun reset() { settings.reset() }

    @Test fun defaultsKeepOnlyTheModeButtonVisibleAndEdgeRevealDisabled() {
        assertEquals(1.5f, settings.sensitivity, 0f)
        assertEquals(CursorAppearance(), settings.cursorAppearance())
        assertEquals(EdgeRevealConfig(), settings.edgeReveal())
        assertTrue(settings.excludedPackages.isEmpty())
        for (action in OverlayAction.entries) {
            val config = settings.button(action)
            assertEquals(action == OverlayAction.MODE, config.enabled)
            assertEquals(action, config.action)
            assertEquals(64, config.widthDp)
            assertEquals(56, config.heightDp)
        }
    }

    @Test fun sensitivityIsBoundedOnWriteAndOnReadingOldPreferences() {
        settings.sensitivity = -1f
        assertEquals(0.25f, settings.sensitivity, 0f)
        settings.sensitivity = 100f
        assertEquals(4f, settings.sensitivity, 0f)
        preferences.edit().putFloat("sensitivity", -100f).commit()
        assertEquals(0.25f, settings.sensitivity, 0f)
    }

    @Test fun everyCursorShapeAndItsAppearanceSurviveRepositoryRecreation() {
        for (shape in CursorShape.entries) {
            val config = CursorAppearance(shape, 64, 0.45f, 0xFF112233.toInt(), 0xFF445566.toInt(), 5)
            settings.saveCursorAppearance(config)
            assertEquals(config, SettingsRepository(context).cursorAppearance())
        }
    }

    @Test fun cursorLimitsAreEnforcedAndColorsKeepRgbWithoutHiddenAlpha() {
        settings.saveCursorAppearance(CursorAppearance(sizeDp = 999, opacity = -5f,
            color = 0x00123456, outlineColor = 0x10765432, outlineWidthDp = 99))
        val config = settings.cursorAppearance()
        assertEquals(96, config.sizeDp)
        assertEquals(0.1f, config.opacity, 0f)
        assertEquals(6, config.outlineWidthDp)
        assertEquals(0xFF123456.toInt(), config.color)
        assertEquals(0xFF765432.toInt(), config.outlineColor)
        settings.saveCursorAppearance(config.copy(sizeDp = -1, opacity = 2f, outlineWidthDp = -1))
        assertEquals(16, settings.cursorAppearance().sizeDp)
        assertEquals(1f, settings.cursorAppearance().opacity, 0f)
        assertEquals(0, settings.cursorAppearance().outlineWidthDp)
    }

    @Test fun outOfRangeStoredCursorValuesAreSafeToRender() {
        preferences.edit().putInt("cursor.size", -30).putFloat("cursor.opacity", 3f)
            .putInt("cursor.outlineWidth", -10).commit()
        assertEquals(16, settings.cursorAppearance().sizeDp)
        assertEquals(1f, settings.cursorAppearance().opacity, 0f)
        assertEquals(0, settings.cursorAppearance().outlineWidthDp)
    }

    @Test fun unknownStoredEnumNamesFallBackWithoutCrashing() {
        preferences.edit().putString("cursor.shape", "REMOVED_SHAPE").putString("edge.side", "DIAGONAL").commit()
        assertEquals(CursorShape.ARROW, settings.cursorAppearance().shape)
        assertEquals(ScreenEdge.RIGHT, settings.edgeReveal().edge)
    }

    @Test fun hidingTheModeButtonPersistsWithoutChangingOptionalButtons() {
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
        settings.save(settings.button(OverlayAction.COPY).copy(enabled = true, widthDp = 90))
        val restored = SettingsRepository(context)
        assertFalse(restored.button(OverlayAction.MODE).enabled)
        assertTrue(restored.button(OverlayAction.COPY).enabled)
        assertEquals(90, restored.button(OverlayAction.COPY).widthDp)
        assertFalse(restored.button(OverlayAction.PASTE).enabled)
    }

    @Test fun everyButtonKeepsAnIndependentLayoutAndOpacity() {
        val configs = OverlayAction.entries.map { action -> settings.button(action).copy(
            enabled = true, xNormalized = action.ordinal / 4f, yNormalized = 0.8f,
            widthDp = 50 + action.ordinal * 10, heightDp = 90, opacity = 0.4f + action.ordinal * 0.1f,
        ) }
        configs.forEach(settings::save)
        configs.forEach { assertEquals(it, SettingsRepository(context).button(it.action)) }
    }

    @Test fun buttonLayoutIsClampedOnBothWriteAndRead() {
        settings.save(settings.button(OverlayAction.MODE).copy(xNormalized = -2f, yNormalized = 3f,
            widthDp = 1, heightDp = 999, opacity = 0f))
        val config = settings.button(OverlayAction.MODE)
        assertEquals(0f, config.xNormalized, 0f)
        assertEquals(1f, config.yNormalized, 0f)
        assertEquals(40, config.widthDp)
        assertEquals(160, config.heightDp)
        assertEquals(0.3f, config.opacity, 0f)
        preferences.edit().putFloat("COPY.x", 10f).putInt("COPY.width", -1).putFloat("COPY.opacity", 2f).commit()
        assertEquals(1f, settings.button(OverlayAction.COPY).xNormalized, 0f)
        assertEquals(40, settings.button(OverlayAction.COPY).widthDp)
        assertEquals(1f, settings.button(OverlayAction.COPY).opacity, 0f)
    }

    @Test fun allEdgesRoundTripAndInvalidDimensionsAreClamped() {
        for (edge in ScreenEdge.entries) {
            val config = EdgeRevealConfig(true, edge, 0.7f, 120, 20)
            settings.saveEdgeReveal(config)
            assertEquals(config, SettingsRepository(context).edgeReveal())
        }
        settings.saveEdgeReveal(EdgeRevealConfig(true, ScreenEdge.TOP, -1f, 1, 999))
        assertEquals(EdgeRevealConfig(true, ScreenEdge.TOP, 0f, 48, 32), settings.edgeReveal())
        preferences.edit().putFloat("edge.position", 2f).putInt("edge.length", 999).putInt("edge.thickness", -1).commit()
        assertEquals(EdgeRevealConfig(true, ScreenEdge.TOP, 1f, 240, 8), settings.edgeReveal())
    }

    @Test fun exclusionSetsAreDefensivelyCopied() {
        val original = mutableSetOf("app.one", "app.two")
        settings.excludedPackages = original
        original.clear()
        assertEquals(setOf("app.one", "app.two"), settings.excludedPackages)
        val returned = settings.excludedPackages as MutableSet<String>
        returned.clear()
        assertEquals(setOf("app.one", "app.two"), SettingsRepository(context).excludedPackages)
    }

    @Test fun savingOneFeatureDoesNotEraseAnother() {
        settings.sensitivity = 2f
        settings.excludedPackages = setOf("game.package")
        settings.saveEdgeReveal(EdgeRevealConfig(enabled = true))
        settings.saveCursorAppearance(CursorAppearance(shape = CursorShape.CROSSHAIR))
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
        assertEquals(2f, settings.sensitivity, 0f)
        assertEquals(setOf("game.package"), settings.excludedPackages)
        assertTrue(settings.edgeReveal().enabled)
        assertEquals(CursorShape.CROSSHAIR, settings.cursorAppearance().shape)
        assertFalse(settings.button(OverlayAction.MODE).enabled)
    }

    @Test fun resetRestoresAllFeatureDefaults() {
        settings.sensitivity = 4f
        settings.excludedPackages = setOf("game.package")
        settings.saveEdgeReveal(EdgeRevealConfig(enabled = true))
        settings.saveCursorAppearance(CursorAppearance(shape = CursorShape.CIRCLE, opacity = 0.2f))
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
        settings.reset()
        defaultsKeepOnlyTheModeButtonVisibleAndEdgeRevealDisabled()
    }

    @Test fun liveObserversStopReceivingChangesWhenDetached() {
        val changes = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> changes += key }
        settings.observe(listener)
        settings.sensitivity = 2f
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(changes.contains("sensitivity"))
        settings.stopObserving(listener)
        changes.clear()
        settings.sensitivity = 3f
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(changes.isEmpty())
    }
}
