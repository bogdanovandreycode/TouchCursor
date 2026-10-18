package com.example.touchcursor.overlay

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.example.touchcursor.model.CursorAppearance
import com.example.touchcursor.model.CursorShape
import com.example.touchcursor.model.EdgeRevealConfig
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.settings.SettingsRepository
import com.example.touchcursor.testing.accessibilityService
import com.example.touchcursor.testing.ConfigurableAccessibilityServiceShadow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadow.api.Shadow
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ConfigurableAccessibilityServiceShadow::class])
class OverlayManagerTest {
    private val service = accessibilityService()
    private val settings = SettingsRepository(service)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val actions = mutableListOf<OverlayAction>()
    private lateinit var manager: OverlayManager
    private val platform = Shadow.extract<ConfigurableAccessibilityServiceShadow>(service)

    @Before fun setup() {
        settings.reset()
        platform.setCanDispatchGestures(true)
        manager = OverlayManager(service, actions::add)
        manager.show()
        settle()
    }

    @After fun cleanup() { manager.close() }

    private fun settle(ms: Long = 40) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun windows() = Shadow.extract<ShadowWindowManagerImpl>(wm).views.filter { (it.layoutParams as? WindowManager.LayoutParams)?.title?.startsWith("TouchCursor.") == true }
    private fun view(name: String) = windows().firstOrNull { (it.layoutParams as WindowManager.LayoutParams).title == "TouchCursor.$name" }
    private fun params(name: String) = view(name)!!.layoutParams as WindowManager.LayoutParams
    private fun flag(name: String, flag: Int) = params(name).flags and flag != 0
    private fun enter() { manager.toggleCursorMode(); settle() }

    @Test fun normalModeLeavesInputUntouchableAndCursorAbsent() {
        assertNotNull(view("MODE"))
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params("Input").type)
    }

    @Test fun cursorModeCapturesInputButTheCursorNeverCapturesOrFocuses() {
        enter()
        assertFalse(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        assertTrue(flag("Cursor", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        assertTrue(flag("Cursor", WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        manager.toggleCursorMode()
        settle()
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun hiddenActivationButtonStaysHiddenInCursorModeAndAfterRecovery() {
        settings.save(settings.button(OverlayAction.MODE).copy(enabled = false))
        settle()
        assertNull(view("MODE"))
        enter()
        assertNull(view("MODE"))
        assertNotNull(view("Cursor"))
        manager.recoverWindows()
        settle(2100)
        assertNull(view("MODE"))
        manager.toggleCursorMode()
        settle()
        assertNull(view("MODE"))
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun screenSuspensionRemovesEveryWindowAndResumeStartsInNormalMode() {
        enter()
        manager.suspend()
        manager.suspend()
        assertTrue(windows().isEmpty())
        manager.toggleCursorMode()
        assertTrue(windows().isEmpty())
        manager.resume()
        settle()
        assertNotNull(view("MODE"))
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun excludedAppsCannotShowOrActivateCursorWindows() {
        enter()
        manager.setExcluded(true)
        manager.toggleCursorMode()
        manager.recoverWindows()
        assertTrue(windows().isEmpty())
        manager.setExcluded(false)
        settle()
        assertNotNull(view("MODE"))
        assertNull(view("Cursor"))
    }

    @Test fun systemShortcutCanEnterFromACollapsedEdgePanelAndWakeCollapsesItAgain() {
        settings.saveEdgeReveal(EdgeRevealConfig(enabled = true))
        settle()
        assertNotNull(view("Edge"))
        assertNull(view("MODE"))
        manager.setCursorMode(true)
        assertNull(view("Cursor"))
        enter()
        assertNotNull(view("Cursor"))
        assertNotNull(view("MODE"))
        manager.suspend()
        manager.resume()
        settle()
        assertNotNull(view("Edge"))
        assertNull(view("MODE"))
        assertNull(view("Cursor"))
    }

    @Test fun optionalControlsDispatchTheirOwnActionAndSettingsApplyLive() {
        settings.save(settings.button(OverlayAction.COPY).copy(enabled = true, widthDp = 80, opacity = 0.4f))
        settle()
        view("COPY")!!.performClick()
        assertEquals(listOf(OverlayAction.COPY), actions)
        assertEquals(0.4f, params("COPY").alpha, 0f)
        settings.save(settings.button(OverlayAction.COPY).copy(enabled = false))
        settle()
        assertNull(view("COPY"))
    }

    @Test fun appearanceChangesUpdateTheVisibleCursorWithoutChangingMode() {
        enter()
        settings.saveCursorAppearance(CursorAppearance(shape = CursorShape.CIRCLE, sizeDp = 64, opacity = 0.5f))
        settle()
        assertEquals(CursorShape.CIRCLE, (view("Cursor") as CursorOverlayView).appearance.shape)
        assertEquals(0.5f, params("Cursor").alpha, 0f)
        assertFalse(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    private fun requestTap() {
        val input = view("Input")!!
        // Explicit dimensions also make the test independent of rendering scheduling.
        if (input.width == 0 || input.height == 0) input.layout(0, 0, 320, 480)
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 40L,
            MotionEvent.ACTION_DOWN to 100L, MotionEvent.ACTION_UP to 140L)) {
            val event = MotionEvent.obtain(0, time, action, 50f, 100f, 0)
            try { input.onTouchEvent(event) } finally { event.recycle() }
        }
    }

    @Test fun injectionRemovesTouchableWindowsAndSuccessRestoresCursorMode() {
        enter()
        requestTap()
        assertNull(view("Input"))
        assertNull(view("MODE"))
        assertTrue(platform.gesturesDispatched.isEmpty())
        settle()
        val gesture = platform.gesturesDispatched.single()
        gesture.callback().onCompleted(gesture.description())
        settle()
        assertNotNull(view("Cursor"))
        assertFalse(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun cancelledInjectionReturnsToNormalTouchInsteadOfRecapturingInput() {
        enter()
        requestTap()
        settle()
        val gesture = platform.gesturesDispatched.single()
        gesture.callback().onCancelled(gesture.description())
        settle()
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun unavailableWindowSnapshotTimesOutAndLateRecoveryCannotInject() {
        enter()
        platform.failWindowRead = true
        requestTap()
        settle(1000)
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        platform.failWindowRead = false
        settle(3000)
        assertTrue(platform.gesturesDispatched.isEmpty())
    }

    @Test fun missingGestureCallbackTimesOutToNormalTouch() {
        enter()
        requestTap()
        settle()
        assertEquals(1, platform.gesturesDispatched.size)
        settle(2100)
        assertNull(view("Cursor"))
        assertTrue(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun displayChangeDuringPreparationCancelsTheOldCoordinates() {
        enter()
        requestTap()
        manager.onDisplayChanged()
        settle()
        assertTrue(platform.gesturesDispatched.isEmpty())
        assertNotNull(view("Cursor"))
        assertFalse(flag("Input", WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test fun closePreventsPendingFramesCallbacksAndPreferencesFromRecreatingWindows() {
        enter()
        requestTap()
        manager.close()
        manager.close()
        settings.sensitivity = 2f
        manager.resume()
        manager.show()
        manager.recoverWindows()
        settle(4000)
        assertTrue(windows().isEmpty())
        assertTrue(platform.gesturesDispatched.isEmpty())
    }
}
