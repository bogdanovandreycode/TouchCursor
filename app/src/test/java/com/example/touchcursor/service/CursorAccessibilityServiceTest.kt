@file:Suppress("DEPRECATION")

package com.example.touchcursor.service

import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.touchcursor.settings.SettingsRepository
import com.example.touchcursor.testing.accessibilityNode
import com.example.touchcursor.testing.accessibilityWindow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 34])
class CursorAccessibilityServiceTest {
    private val lifecycle = Robolectric.buildService(CursorAccessibilityService::class.java).create()
    private val service = lifecycle.get()
    private val repository = SettingsRepository(service)
    private val power = shadowOf(service.getSystemService(PowerManager::class.java))
    private val keyguard = shadowOf(service.getSystemService(KeyguardManager::class.java))
    private val platform = shadowOf(service)
    private fun settle(ms: Long = 50) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun windows() = Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java))
        .views.filter { (it.layoutParams as? WindowManager.LayoutParams)?.title?.startsWith("TouchCursor.") == true }
    private fun view(name: String) = windows().firstOrNull { (it.layoutParams as WindowManager.LayoutParams).title == "TouchCursor.$name" }
    // Android invokes this protected callback after binding the accessibility service.
    private fun connect() { ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected"); settle() }
    private fun app(name: String) { platform.setWindows(listOf(accessibilityWindow(1, root = accessibilityNode(name), active = true))) }
    private fun broadcast(action: String) { service.sendBroadcast(Intent(action)); settle() }

    @Before fun setup() {
        repository.reset()
        power.setIsInteractive(true); keyguard.setKeyguardLocked(false)
    }
    @After fun cleanup() { lifecycle.destroy(); settle(2500) }

    @Test fun connectAndReconnectCreateOneSetOfControls() {
        connect()
        assertTrue(CursorAccessibilityService.running)
        val count = windows().size
        assertTrue(count > 0)
        connect()
        assertEquals(count, windows().size)
    }

    @Test fun screenOffRemovesControlsAndUnlockRestoresNormalTouch() {
        connect(); view("MODE")!!.performClick(); settle()
        assertNotNull(view("Cursor"))
        power.setIsInteractive(false); broadcast(Intent.ACTION_SCREEN_OFF)
        assertTrue(windows().isEmpty())
        power.setIsInteractive(true); keyguard.setKeyguardLocked(true)
        broadcast(Intent.ACTION_SCREEN_ON)
        assertTrue(windows().isEmpty())
        keyguard.setKeyguardLocked(false); broadcast(Intent.ACTION_USER_PRESENT)
        assertNotNull(view("MODE")); assertNull(view("Cursor"))
        assertTrue((view("Input")!!.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }

    @Test fun periodicCheckRecoversAfterMissedScreenBroadcast() {
        connect()
        power.setIsInteractive(false); settle(2100)
        assertTrue(windows().isEmpty())
        power.setIsInteractive(true); settle(2100)
        assertNotNull(view("MODE")); assertNull(view("Cursor"))
    }

    @Test fun interruptExitsCursorMode() {
        connect(); view("MODE")!!.performClick(); settle()
        service.onInterrupt(); settle()
        assertNull(view("Cursor")); assertNotNull(view("MODE"))
    }

    @Test fun excludedAppHidesControlsAndOwnAppCanAlwaysRestoreThem() {
        repository.excludedPackages = setOf("game.app")
        app("game.app"); connect()
        assertTrue(windows().isEmpty())
        app(service.packageName)
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        service.onAccessibilityEvent(event); event.recycle(); settle()
        assertNotNull(view("MODE"))
    }

    @Test fun changingExclusionsAppliesWithoutReconnectingTheService() {
        app("game.app"); connect()
        assertNotNull(view("MODE"))
        app("game.app"); repository.excludedPackages = setOf("game.app"); settle()
        assertTrue(windows().isEmpty())
        repository.excludedPackages = emptySet(); settle()
        assertNotNull(view("MODE"))
    }

    @Test @Config(sdk = [34]) fun nativeAccessibilityButtonTogglesAndRespectsExclusions() {
        connect()
        val button = shadowOf(service.accessibilityButtonController)
        button.performAccessibilityButtonClick(); settle()
        assertNotNull(view("Cursor"))
        button.performAccessibilityButtonClick(); settle()
        assertNull(view("Cursor"))
        app("game.app"); repository.excludedPackages = setOf("game.app"); settle()
        button.performAccessibilityButtonClick(); settle()
        assertTrue(windows().isEmpty())
    }

    @Test fun unbindStopsRecoveryAndPreferenceObservers() {
        connect()
        service.onUnbind(Intent())
        repository.sensitivity = 2f
        broadcast(Intent.ACTION_USER_PRESENT); settle(5000)
        assertFalse(CursorAccessibilityService.running)
        assertFalse(CursorAccessibilityService.accessibilityButtonAvailable)
        assertTrue(windows().isEmpty())
    }

    @Test fun configurationChangePreservesWorkingCursorMode() {
        connect(); view("MODE")!!.performClick(); settle()
        service.onConfigurationChanged(Configuration(service.resources.configuration)); settle()
        assertNotNull(view("Cursor"))
        assertTrue(CursorAccessibilityService.running)
    }
}
