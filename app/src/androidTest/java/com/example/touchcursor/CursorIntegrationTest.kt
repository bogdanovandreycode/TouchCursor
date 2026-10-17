package com.example.touchcursor

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.touchcursor.model.OverlayAction
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
    private val component = "com.example.touchcursor/.service.CursorAccessibilityService"

    @Before fun setup() {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        previousServices = shell("settings get secure enabled_accessibility_services")
        previousEnabled = shell("settings get secure accessibility_enabled")
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
        val tipX = cursor.left + 3 * density
        val tipY = cursor.top + 3 * density
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
        val injectedPoint = requiredNode("fixture-touch").text.toString().split(',').map(String::toFloat)
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
        tapWindow("TouchCursor.COPY")
        field = requiredNode("fixture-edit")
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }))
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

    private fun label() = node("fixture-target")?.text?.toString()
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
        val frame = Regex("(?:mFrame=|\\bframe=)\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
            .find(block) ?: return null
        return frame.groupValues.drop(1).map(String::toInt).let { Rect(it[0], it[1], it[2], it[3]) }
    }

    private fun tapWindow(title: String) {
        await { window(title) != null }
        window(title)!!.let { tap(it.exactCenterX(), it.exactCenterY()) }
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
        fail("Condition timed out. Mode: ${window("TouchCursor.MODE")}; input: ${window("TouchCursor.Input")}; config: ${settings.button(OverlayAction.MODE)}; target: ${label()}")
    }
}
