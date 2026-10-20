package com.example.touchcursor

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.model.EdgeRevealConfig
import com.example.touchcursor.model.ScreenEdge
import com.example.touchcursor.model.CursorAppearance
import com.example.touchcursor.model.CursorShape
import com.example.touchcursor.service.CursorAccessibilityService
import com.example.touchcursor.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CursorIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val settings = SettingsRepository(instrumentation.targetContext)
    private var previousServices = ""
    private var previousEnabled = "0"
    private var previousFontScale = "1.0"
    private val component = "com.example.touchcursor/.service.CursorAccessibilityService"
    private val shortcutComponent = "com.example.touchcursor/com.example.touchcursor.service.CursorAccessibilityService"

    @Before fun setup() {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        previousServices = shell("settings get secure enabled_accessibility_services")
        previousEnabled = shell("settings get secure accessibility_enabled")
        previousFontScale = shell("settings get system font_scale")
        settings.reset()
        settings.sensitivity = 1f
        shell("settings put secure enabled_accessibility_services $component")
        shell("settings put secure accessibility_enabled 1")
        instrumentation.targetContext.startActivity(Intent().apply {
            component = ComponentName("com.example.touchcursor.test", TouchTargetActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        await { window("TouchCursor.MODE")?.width()?.let { it > 10 } == true && node("fixture-target") != null }
        automation.waitForIdle(200, 5000)
    }

    @After fun cleanup() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("settings put system font_scale ${if (previousFontScale == "null") "1.0" else previousFontScale}")
        shell("settings put system accelerometer_rotation 1")
        shell("settings put system user_rotation 0")
        await { instrumentation.targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        shell("settings put secure enabled_accessibility_services ${if (previousServices == "null" || previousServices.isBlank()) "null" else previousServices}")
        shell("settings put secure accessibility_enabled ${if (previousEnabled == "null") "0" else previousEnabled}")
        settings.reset()
    }

    @Test fun normalTouchRelativeMovementDoubleTapHoldAndExit() {
        val target = nodeBounds("fixture-target")
        tap(target.centerX().toFloat(), target.centerY().toFloat())
        await { label() == "Taps: 1; holds: 0" }
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        val cursor = window("TouchCursor.Cursor")!!
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val tipX = cursor.left + 6 * density
        val tipY = cursor.top + 6 * density
        val input = window("TouchCursor.Input")!!
        val fingerX = input.left + input.width() * 0.25f
        val fingerY = input.top + input.height() * 0.7f
        drag(fingerX, fingerY, fingerX + target.centerX() - tipX, fingerY + target.centerY() - tipY)
        await { window("TouchCursor.Cursor")!!.top != cursor.top }
        assertEquals("Taps: 1; holds: 0", label())
        tap(fingerX, fingerY)
        SystemClock.sleep(60)
        tap(fingerX, fingerY)
        await { label() == "Taps: 2; holds: 0" }
        val injectedPoint = receivedPoint()
        assertEquals(target.exactCenterX(), injectedPoint[0], 2f)
        assertEquals(target.exactCenterY(), injectedPoint[1], 2f)
        await { window("TouchCursor.Input") != null }
        hold(fingerX, fingerY)
        await { label() == "Taps: 2; holds: 1" }
        await { window("TouchCursor.MODE") != null }
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") == null }
        automation.waitForIdle(100, 5000)
        tap(target.centerX().toFloat(), target.centerY().toFloat())
        await { label() == "Taps: 3; holds: 1" }
    }

    @Test fun buttonConfigurationPersistsAndRemainsInsideRotatedBounds() {
        val config = settings.button(OverlayAction.MODE).copy(xNormalized = 0.1f, yNormalized = 0.7f, widthDp = 80, heightDp = 44, opacity = 0.55f)
        settings.save(config)
        OverlayAction.entries.filter { it != OverlayAction.MODE }.forEach {
            settings.save(settings.button(it).copy(enabled = true))
        }
        await { window("TouchCursor.KEYBOARD") != null }
        val inputBefore = window("TouchCursor.Input")!!
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val width = (80 * density + 0.5f).toInt()
        val height = (44 * density + 0.5f).toInt()
        val left = inputBefore.left + ((inputBefore.width() - width) * 0.1f).toInt()
        val top = inputBefore.top + ((inputBefore.height() - height) * 0.7f).toInt()
        await { window("TouchCursor.MODE") == Rect(left, top, left + width, top + height) }
        val before = window("TouchCursor.MODE")!!
        shell("settings put secure enabled_accessibility_services null")
        await { window("TouchCursor.MODE") == null }
        shell("settings put secure enabled_accessibility_services $component")
        await { window("TouchCursor.MODE") == before }
        val windowDump = shell("dumpsys window windows").substringAfter(" u0 TouchCursor.MODE}:")
            .substringBefore("\n  Window #")
        assertTrue("Saved opacity must be applied to the actual overlay window", windowDump.contains("alpha=0.55"))
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 1")
        await { window("TouchCursor.Input")?.let { it.width() > it.height() } == true }
        val input = window("TouchCursor.Input")!!
        for (action in OverlayAction.entries) {
            await { window("TouchCursor.${action.name}")?.let { input.contains(it) } == true }
        }
    }

    @Test fun cursorShapesKeepTheClickHotspotAndPersistAppearance() {
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val target = nodeBounds("fixture-target")
        var taps = 0
        for (shape in CursorShape.entries) {
            val config = CursorAppearance(shape, 64, 0.4f, 0xFFFF3300.toInt(), 0xFF00FFFF.toInt(), 6)
            settings.saveCursorAppearance(config)
            // Restart verifies every saved property, not just the in-process preview.
            shell("settings put secure enabled_accessibility_services null")
            await { !CursorAccessibilityService.running }
            assertEquals(config, SettingsRepository(instrumentation.targetContext).cursorAppearance())
            shell("settings put secure enabled_accessibility_services $component")
            await { window("TouchCursor.MODE") != null }
            tapWindow("TouchCursor.MODE")
            await { window("TouchCursor.Cursor")?.height() == (70 * density + 0.5f).toInt() }
            val cursor = window("TouchCursor.Cursor")!!
            val tipX = cursor.left + (3 + (if (shape == CursorShape.ARROW) 3f else 16f) * 64 / 38f) * density
            val tipY = cursor.top + (3 + (if (shape == CursorShape.ARROW) 3f else 19f) * 64 / 38f) * density
            val input = window("TouchCursor.Input")!!
            val fingerX = input.left + input.width() * 0.25f
            val fingerY = input.top + input.height() * 0.7f
            drag(fingerX, fingerY, fingerX + target.centerX() - tipX, fingerY + target.centerY() - tipY)
            val dump = shell("dumpsys window windows").substringAfter(" u0 TouchCursor.Cursor}:").substringBefore("\n  Window #")
            assertTrue(dump.contains("alpha=0.4"))
            tap(fingerX, fingerY)
            SystemClock.sleep(60)
            tap(fingerX, fingerY)
            taps++
            await { label() == "Taps: $taps; holds: 0" }
            val point = receivedPoint()
            assertEquals(target.exactCenterX(), point[0], 2f)
            assertEquals(target.exactCenterY(), point[1], 2f)
        }
    }

    @Test fun screenOffAndUnlockRestoreControlsWithoutReenablingService() {
        repeat(3) {
            tapWindow("TouchCursor.MODE")
            await { window("TouchCursor.Cursor") != null }
            shell("input keyevent KEYCODE_SLEEP")
            await { noOverlays() }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            await { window("TouchCursor.MODE") != null }
            assertTrue(CursorAccessibilityService.running)
            assertNull(window("TouchCursor.Cursor"))
        }
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val target = nodeBounds("fixture-target")
        val cursor = window("TouchCursor.Cursor")!!
        val input = window("TouchCursor.Input")!!
        val fingerX = input.left + input.width() * 0.25f
        val fingerY = input.top + input.height() * 0.7f
        drag(fingerX, fingerY, fingerX + target.centerX() - cursor.left - 6 * density,
            fingerY + target.centerY() - cursor.top - 6 * density)
        tap(fingerX, fingerY)
        SystemClock.sleep(60)
        tap(fingerX, fingerY)
        await { label() == "Taps: 1; holds: 0" }
        tapWindow("TouchCursor.MODE")
        settings.saveEdgeReveal(EdgeRevealConfig(enabled = true))
        await { window("TouchCursor.Edge") != null && window("TouchCursor.MODE") == null }
        shell("input keyevent KEYCODE_SLEEP")
        await { noOverlays() }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        await { window("TouchCursor.Edge") != null }
        assertNull(window("TouchCursor.MODE"))
    }

    @Test fun modeButtonCanBeHiddenAndRestored() {
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
        await { window("TouchCursor.MODE") == null }
        shell("settings put secure enabled_accessibility_services null")
        await { !CursorAccessibilityService.running }
        shell("settings put secure enabled_accessibility_services $component")
        await { CursorAccessibilityService.running && window("TouchCursor.Input") != null }
        assertNull(window("TouchCursor.MODE"))
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = true))
        await { window("TouchCursor.MODE") != null }
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun floatingSystemShortcutCanEnterAndExitCapturedInput() {
        val previousTargets = shell("settings get secure accessibility_button_targets")
        val previousMode = shell("settings get secure accessibility_button_mode")
        try {
            shell("settings put secure accessibility_button_targets $shortcutComponent")
            shell("settings put secure accessibility_button_mode 1")
            settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
            settings.saveEdgeReveal(EdgeRevealConfig(enabled = true, edge = ScreenEdge.LEFT))
            await { node("Touch Cursor Service") != null && window("TouchCursor.MODE") == null && window("TouchCursor.Edge") != null }
            val shortcut = nodeBounds("Touch Cursor Service")
            tap(shortcut.exactCenterX(), shortcut.exactCenterY())
            await { window("TouchCursor.Cursor") != null && window("TouchCursor.Input") != null }
            assertNull(window("TouchCursor.MODE"))
            clickAndHoldAtTarget()
            tap(shortcut.exactCenterX(), shortcut.exactCenterY())
            await { window("TouchCursor.Cursor") == null && window("TouchCursor.MODE") == null }
            val target = nodeBounds("fixture-target")
            tap(target.exactCenterX(), target.exactCenterY())
            await { label() == "Taps: 2; holds: 1" }
            tap(shortcut.exactCenterX(), shortcut.exactCenterY())
            await { window("TouchCursor.Cursor") != null }
            assertNull(window("TouchCursor.MODE"))
            settings.save(settings.button(OverlayAction.MODE).copy(enabled = true))
            await { window("TouchCursor.MODE") != null }
            tapWindow("TouchCursor.MODE")
            await { window("TouchCursor.Cursor") == null }
            settings.excludedPackages = setOf("com.example.touchcursor.test")
            await { noOverlays() }
            tap(shortcut.exactCenterX(), shortcut.exactCenterY())
            SystemClock.sleep(300)
            assertTrue(noOverlays())
        } finally {
            shell("settings put secure accessibility_button_targets $previousTargets")
            shell("settings put secure accessibility_button_mode $previousMode")
        }
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun removingSystemShortcutKeepsServiceAndAppControlsWorking() {
        val previousTargets = shell("settings get secure accessibility_button_targets")
        val previousMode = shell("settings get secure accessibility_button_mode")
        try {
            shell("settings put secure accessibility_button_targets $shortcutComponent")
            shell("settings put secure accessibility_button_mode 1")
            await { node("Touch Cursor Service") != null }
            shell("settings put secure accessibility_button_targets null")
            await { node("Touch Cursor Service") == null }
            assertTrue(CursorAccessibilityService.running)
            assertTrue(shell("settings get secure enabled_accessibility_services").contains("CursorAccessibilityService"))
            tapWindow("TouchCursor.MODE")
            await { window("TouchCursor.Cursor") != null }
            clickAndHoldAtTarget()
            tapWindow("TouchCursor.MODE")
            await { window("TouchCursor.Cursor") == null }
            val target = nodeBounds("fixture-target")
            tap(target.exactCenterX(), target.exactCenterY())
            await { label() == "Taps: 2; holds: 1" }
        } finally {
            shell("settings put secure accessibility_button_targets $previousTargets")
            shell("settings put secure accessibility_button_mode $previousMode")
        }
    }

    @Test fun unchangedLayoutDuringInjectionDoesNotDisableLaterClicks() {
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        val finger = moveCursorToTarget()
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, finger.first, finger.second, down)
        SystemClock.sleep(800)
        event(MotionEvent.ACTION_UP, finger.first, finger.second, down)
        await { window("TouchCursor.Input") == null }
        await { node("fixture-gesture")?.text?.startsWith("action=0;") == true }
        // Font scaling sends onConfigurationChanged without changing capture geometry.
        shell("settings put system font_scale 1.15")
        await { instrumentation.targetContext.resources.configuration.fontScale > 1.1f }
        await { window("TouchCursor.Input") != null }
        assertTrue(label()?.contains("holds: 1") == true)
        val updatedFinger = moveCursorToTarget()
        tap(updatedFinger.first, updatedFinger.second)
        SystemClock.sleep(60)
        tap(updatedFinger.first, updatedFinger.second)
        await { label() == "Taps: 1; holds: 1" }
    }

    @Test fun twoFingerExitRestoresDirectTouchWithoutInjecting() {
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
        val target = nodeBounds("fixture-target")
        val down = SystemClock.uptimeMillis()
        val pointers = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(2) { index -> MotionEvent.PointerCoords().apply {
            x = target.centerX() + index * 100f; y = target.centerY().toFloat(); pressure = 1f; size = 1f
        } }
        fun multi(action: Int, count: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, pointers, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        multi(MotionEvent.ACTION_DOWN, 1)
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
        multi(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
        assertNotNull(window("TouchCursor.Cursor"))
        multi(MotionEvent.ACTION_UP, 1)
        await { window("TouchCursor.Cursor") == null && window("TouchCursor.MODE") == null }
        assertEquals("Taps: 0; holds: 0", label())
        tap(target.centerX().toFloat(), target.centerY().toFloat())
        await { label() == "Taps: 1; holds: 0" }
    }

    private fun moveCursorToTarget(description: String = "fixture-target"): Pair<Float, Float> {
        val config = settings.cursorAppearance()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val cursor = window("TouchCursor.Cursor")!!
        val target = nodeBounds(description)
        val tipX = cursor.left + (3 + (if (config.shape == CursorShape.ARROW) 3f else 16f) * config.sizeDp / 38f) * density
        val tipY = cursor.top + (3 + (if (config.shape == CursorShape.ARROW) 3f else 19f) * config.sizeDp / 38f) * density
        val input = window("TouchCursor.Input")!!
        val fingerX = input.left + input.width() * 0.25f
        val fingerY = input.top + input.height() * 0.7f
        val dx = target.centerX() - tipX
        val dy = target.centerY() - tipY
        // A zero-distance "drag" is a tap and would arm the double-tap recognizer.
        if (kotlin.math.hypot(dx, dy) > ViewConfiguration.get(instrumentation.targetContext).scaledTouchSlop) {
            drag(fingerX, fingerY, fingerX + dx, fingerY + dy)
        }
        return fingerX to fingerY
    }

    @Test @SdkSuppress(minSdkVersion = 26)
    fun holdMoveDropsAnItemAndSecondTouchSwipeScrolls() {
        instrumentation.targetContext.startActivity(Intent().apply {
            component = ComponentName("com.example.touchcursor.test", DragScrollTargetActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await { node("fixture-drop") != null }
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        val finger = moveCursorToTarget()
        val source = nodeBounds("fixture-target")
        val destination = nodeBounds("fixture-drop")
        val dx = destination.exactCenterX() - source.exactCenterX()
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, finger.first, finger.second, down)
        SystemClock.sleep(800)
        for (step in 1..12) {
            event(MotionEvent.ACTION_MOVE, finger.first + dx * step / 12, finger.second, down)
            SystemClock.sleep(30)
        }
        assertEquals("holds=0; drops=0", node("fixture-drag-status")?.text)
        event(MotionEvent.ACTION_UP, finger.first + dx, finger.second, down)
        await { node("fixture-drag-status")?.text == "holds=1; drops=1" }
        await { window("TouchCursor.Input") != null }
        assertNotNull(window("TouchCursor.Cursor"))
        scrollAtCursor()
    }

    @Test fun secondTouchSwipeScrollsOnEverySupportedAndroidVersion() {
        instrumentation.targetContext.startActivity(Intent().apply {
            component = ComponentName("com.example.touchcursor.test", DragScrollTargetActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await { node("fixture-scroll") != null }
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        scrollAtCursor()
    }

    private fun scrollAtCursor() {
        val scrollFinger = moveCursorToTarget("fixture-scroll")
        tap(scrollFinger.first, scrollFinger.second)
        SystemClock.sleep(60)
        val scrollDown = SystemClock.uptimeMillis()
        val distance = nodeBounds("fixture-scroll").height() * 0.3f
        event(MotionEvent.ACTION_DOWN, scrollFinger.first, scrollFinger.second, scrollDown)
        for (step in 1..10) {
            event(MotionEvent.ACTION_MOVE, scrollFinger.first, scrollFinger.second - distance * step / 10, scrollDown)
            SystemClock.sleep(25)
        }
        event(MotionEvent.ACTION_UP, scrollFinger.first, scrollFinger.second - distance, scrollDown)
        await { (node("fixture-scroll-offset")?.text?.toString()?.toIntOrNull() ?: 0) > 0 }
        await { window("TouchCursor.Input") != null }
        assertNotNull(window("TouchCursor.Cursor"))
    }

    @Test fun heldMotionScrollsNativeViewBeforeFingerRelease() {
        instrumentation.targetContext.startActivity(Intent().apply {
            component = ComponentName("com.example.touchcursor.test", DragScrollTargetActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await { node("fixture-scroll") != null }
        assertLiveScroll("fixture-scroll", "fixture-scroll-offset")
    }

    @Test fun heldMotionScrollsBrowserPageBeforeFingerRelease() {
        instrumentation.targetContext.startActivity(Intent().apply {
            component = ComponentName("com.example.touchcursor.test", BrowserScrollTargetActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await { node("fixture-browser-ready")?.text == "ready" }
        assertLiveScroll("fixture-browser-area", "fixture-browser-offset")
    }

    private fun assertLiveScroll(area: String, offset: String) {
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        val finger = moveCursorToTarget(area)
        val cursor = window("TouchCursor.Cursor")
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, finger.first, finger.second, down)
        SystemClock.sleep(800)
        val distance = nodeBounds(area).height() * 0.25f
        event(MotionEvent.ACTION_MOVE, finger.first, finger.second - distance, down)
        // These assertions run before ACTION_UP: replay after release cannot pass this test.
        await { (node(offset)?.text?.toString()?.toIntOrNull() ?: 0) > 0 }
        assertNotNull(window("TouchCursor.Input"))
        assertEquals(cursor, window("TouchCursor.Cursor"))
        event(MotionEvent.ACTION_UP, finger.first, finger.second - distance, down)
        assertNotNull(window("TouchCursor.Input"))
        assertNotNull(window("TouchCursor.Cursor"))
    }

    private fun clickAndHoldAtTarget() {
        val finger = moveCursorToTarget()
        tap(finger.first, finger.second)
        SystemClock.sleep(60)
        tap(finger.first, finger.second)
        await { label() == "Taps: 1; holds: 0" }
        await { window("TouchCursor.Input") != null }
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, finger.first, finger.second, down)
        SystemClock.sleep(800)
        event(MotionEvent.ACTION_UP, finger.first, finger.second, down)
        await { label() == "Taps: 1; holds: 1" }
        await { window("TouchCursor.Input") != null }
    }

    @Test fun copyPasteAndUnsupportedFocusAreSafe() {
        for (action in listOf(OverlayAction.COPY, OverlayAction.PASTE, OverlayAction.KEYBOARD)) {
            settings.save(settings.button(action).copy(enabled = true))
        }
        await { window("TouchCursor.COPY") != null }
        // No selected/focused text is allowed to fail without terminating the service.
        tapWindow("TouchCursor.COPY")
        assertNotNull(window("TouchCursor.MODE"))
        var field = requiredNode("fixture-edit")
        if (!field.isFocused) assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 5)
        }))
        await { node("fixture-edit")?.let { it.textSelectionStart == 0 && it.textSelectionEnd == 5 } == true }
        tapWindow("TouchCursor.COPY")
        automation.waitForIdle(100, 5000)
        field = requiredNode("fixture-edit")
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }))
        await { node("fixture-edit")?.let { it.text.isNullOrEmpty() } == true }
        tapWindow("TouchCursor.PASTE")
        await { node("fixture-edit")?.text?.toString() == "alpha" }
        requiredNode("fixture-edit").performAction(AccessibilityNodeInfo.ACTION_CLICK)
        await { automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        tapWindow("TouchCursor.KEYBOARD")
        await { automation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        tapWindow("TouchCursor.KEYBOARD")
        assertNotNull(window("TouchCursor.MODE"))
    }

    @Test fun disconnectDuringInjectionDoesNotRestoreWindows() {
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        val input = window("TouchCursor.Input")!!
        val x = input.left + input.width() * 0.25f
        val y = input.top + input.height() * 0.7f
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, down)
        SystemClock.sleep(800)
        event(MotionEvent.ACTION_UP, x, y, down)
        shell("settings put secure enabled_accessibility_services null")
        await { window("TouchCursor.MODE") == null && window("TouchCursor.Input") == null }
        SystemClock.sleep(1200)
        assertNull(window("TouchCursor.MODE"))
        assertNull(window("TouchCursor.Input"))
        assertNull(window("TouchCursor.Cursor"))
    }

    @Test fun edgeSwipesRevealCollapsePersistAndRotate() {
        val density = instrumentation.targetContext.resources.displayMetrics.density
        var taps = 0
        for (edge in ScreenEdge.entries) {
            settings.saveEdgeReveal(EdgeRevealConfig(true, edge, 0.15f, 80, 18))
            await { window("TouchCursor.MODE") == null && edgeMatches(edge, 0.15f, 80, 18) }
            val handle = window("TouchCursor.Edge")!!
            val x = handle.exactCenterX()
            val y = handle.exactCenterY()
            val distance = 48 * density
            val dx = when (edge) { ScreenEdge.LEFT -> distance; ScreenEdge.RIGHT -> -distance; else -> 0f }
            val dy = when (edge) { ScreenEdge.TOP -> distance; ScreenEdge.BOTTOM -> -distance; else -> 0f }
            tap(x, y)
            assertNull(window("TouchCursor.MODE"))
            drag(x, y, x + dx, y + dy)
            await { window("TouchCursor.MODE") != null }
            tapWindow("TouchCursor.MODE")
            await { window("TouchCursor.Cursor") != null }
            tapWindow("TouchCursor.Edge")
            await { window("TouchCursor.MODE") == null && window("TouchCursor.Cursor") == null }
            automation.waitForIdle(100, 5000)
            val target = nodeBounds("fixture-target")
            tap(target.exactCenterX(), target.exactCenterY())
            taps++
            await { label() == "Taps: $taps; holds: 0" }
        }
        settings.saveEdgeReveal(EdgeRevealConfig(true, ScreenEdge.RIGHT, 0.75f, 120, 20))
        await { edgeMatches(ScreenEdge.RIGHT, 0.75f, 120, 20) }
        val before = window("TouchCursor.Edge")
        shell("settings put secure enabled_accessibility_services null")
        await { !CursorAccessibilityService.running }
        shell("settings put secure enabled_accessibility_services $component")
        await { window("TouchCursor.Edge") == before }
        assertNull(window("TouchCursor.MODE"))
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 1")
        await { window("TouchCursor.Input")?.let { it.width() > it.height() } == true }
        await { edgeMatches(ScreenEdge.RIGHT, 0.75f, 120, 20) }
    }

    @Test fun excludedAppHasNoOverlaysAndLeavingItRestoresNormalMode() {
        tapWindow("TouchCursor.MODE")
        await { window("TouchCursor.Cursor") != null }
        settings.excludedPackages = setOf("com.example.touchcursor.test", "com.example.touchcursor")
        await { noOverlays() }
        val target = nodeBounds("fixture-target")
        tap(target.exactCenterX(), target.exactCenterY())
        await { label() == "Taps: 1; holds: 0" }
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await { window("TouchCursor.MODE") != null }
        assertNull(window("TouchCursor.Cursor"))
        instrumentation.targetContext.startActivity(Intent().apply {
            component = ComponentName("com.example.touchcursor.test", TouchTargetActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        await { noOverlays() }
        shell("settings put secure enabled_accessibility_services null")
        await { !CursorAccessibilityService.running }
        shell("settings put secure enabled_accessibility_services $component")
        await { CursorAccessibilityService.running }
        assertTrue(noOverlays())
        settings.saveEdgeReveal(EdgeRevealConfig(enabled = true))
        assertTrue(noOverlays())
        settings.excludedPackages = emptySet()
        await { window("TouchCursor.Edge") != null }
        assertNull(window("TouchCursor.MODE"))
        assertNull(window("TouchCursor.Cursor"))
        settings.excludedPackages = setOf("com.example.touchcursor.test")
        await { noOverlays() }
    }

    private fun noOverlays() = listOf("Input", "Cursor", "MODE", "COPY", "PASTE", "KEYBOARD", "Edge")
        .all { window("TouchCursor.$it") == null }

    private fun edgeMatches(edge: ScreenEdge, position: Float, lengthDp: Int, thicknessDp: Int): Boolean {
        val input = window("TouchCursor.Input") ?: return false
        val actual = window("TouchCursor.Edge") ?: return false
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val width = ((if (edge.isVertical) thicknessDp else lengthDp) * density + 0.5f).toInt().coerceAtMost(input.width())
        val height = ((if (edge.isVertical) lengthDp else thicknessDp) * density + 0.5f).toInt().coerceAtMost(input.height())
        val x = when (edge) { ScreenEdge.LEFT -> input.left; ScreenEdge.RIGHT -> input.right - width
            else -> input.left + ((input.width() - width) * position).toInt() }
        val y = when (edge) { ScreenEdge.TOP -> input.top; ScreenEdge.BOTTOM -> input.bottom - height
            else -> input.top + ((input.height() - height) * position).toInt() }
        return actual == Rect(x, y, x + width, y + height)
    }

    private fun label() = node("fixture-target")?.text?.toString()
    private fun receivedPoint(): List<Float> {
        var point: List<Float>? = null
        await {
            point = node("fixture-touch")?.text?.toString()?.split(',')?.mapNotNull(String::toFloatOrNull)
            point?.size == 2
        }
        return point!!
    }
    private fun nodeBounds(description: String) = Rect().also { requiredNode(description).getBoundsInScreen(it) }

    private fun requiredNode(description: String): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await { result = node(description); result != null }
        return result!!
    }

    private fun node(description: String): AccessibilityNodeInfo? {
        fun search(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (root.contentDescription?.toString() == description) return root
            for (index in 0 until root.childCount) root.getChild(index)?.let { child -> search(child)?.let { return it } }
            return null
        }
        for (window in automation.windows) window.root?.let { search(it)?.let { node -> return node } }
        return null
    }

    private fun window(title: String): Rect? {
        // API 24 omits non-touchable overlays and their titles from accessibility windows.
        val dump = shell("dumpsys window windows")
        val header = Regex("Window #\\d+ Window\\{[^\\n]* ${Regex.escape(title)}\\}:").find(dump) ?: return null
        val block = dump.substring(header.range.last + 1).substringBefore("\n  Window #")
        // A registered window can still be DRAW_PENDING, with an inactive input channel.
        if (!block.contains("mDrawState=HAS_DRAWN") || !block.contains("Surface: shown=true")) return null
        if (Build.VERSION.SDK_INT >= 31 && title != "TouchCursor.Cursor") {
            // On BLAST devices HAS_DRAWN can precede the input-window transaction. Wait for
            // this exact channel, not a dying window with the same title, before physical input.
            val channelName = header.value.substringAfter("Window{").substringBefore(' ') + " " + title
            val input = shell("dumpsys input").lineSequence().firstOrNull { it.contains("name='$channelName'") }
                ?: return null
            if (input.contains("NOT_VISIBLE")) return null
            val notTouchable = block.substringBefore("Requested w=").contains("NOT_TOUCHABLE")
            if (input.contains("NOT_TOUCHABLE") != notTouchable) return null
        }
        val frame = Regex("(?:mFrame=|\\bframe=)\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
            .find(block) ?: return null
        return frame.groupValues.drop(1).map(String::toInt).let { Rect(it[0], it[1], it[2], it[3]) }
    }

    private fun tapWindow(title: String) {
        var area: Rect? = null
        await { area = window(title); area != null }
        area!!.let { tap(it.exactCenterX(), it.exactCenterY()) }
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, down)
        SystemClock.sleep(35)
        event(MotionEvent.ACTION_UP, x, y, down)
    }

    private fun hold(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, down)
        SystemClock.sleep(800)
        assertEquals("Taps: 2; holds: 0", label())
        event(MotionEvent.ACTION_UP, x, y, down)
    }

    private fun drag(x: Float, y: Float, endX: Float, endY: Float) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, down)
        SystemClock.sleep(30)
        event(MotionEvent.ACTION_MOVE, endX, endY, down)
        event(MotionEvent.ACTION_UP, endX, endY, down)
    }

    private fun event(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Condition timed out. Mode: ${window("TouchCursor.MODE")}; cursor: ${window("TouchCursor.Cursor")}; input: ${window("TouchCursor.Input")}; config: ${settings.button(OverlayAction.MODE)}; target: ${label()}; gesture: ${node("fixture-gesture")?.text}; drag: ${node("fixture-drag-status")?.text}")
    }
}
