package com.example.touchcursor

import android.app.KeyguardManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.service.CursorAccessibilityService
import com.example.touchcursor.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class CursorShortcutActivityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val lifecycle = Robolectric.buildService(CursorAccessibilityService::class.java).create()
    private val service = lifecycle.get()
    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
    private fun hasWindow(name: String) = Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java))
        .views.any { (it.layoutParams as? WindowManager.LayoutParams)?.title == "TouchCursor.$name" }
    private fun launch() {
        val controller = Robolectric.buildActivity(CursorShortcutActivity::class.java).create()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.destroy()
        settle()
    }

    @After fun cleanup() { lifecycle.destroy(); settle() }

    @Test fun shortcutWithoutServiceOnlyExplainsRequiredAccessAndFinishes() {
        launch()
        assertEquals(context.getString(R.string.accessibility_shortcut_service_required), ShadowToast.getTextOfLatestToast())
        assertFalse(CursorAccessibilityService.running)
        assertFalse(hasWindow("Input"))
    }

    @Test @Suppress("DEPRECATION") fun shortcutTogglesWithoutShowingAppControlsOrChangingServiceAccess() {
        shadowOf(service.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(service.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        SettingsRepository(service).apply { reset(); save(button(OverlayAction.MODE).copy(enabled = false)) }
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected"); settle()
        launch()
        assertTrue(hasWindow("Cursor")); assertFalse(hasWindow("MODE"))
        launch()
        assertFalse(hasWindow("Cursor")); assertFalse(hasWindow("MODE"))
        assertTrue(CursorAccessibilityService.running)
    }

    @Test fun modernServiceMetadataDoesNotRequireShortcutAssignment() {
        val component = ComponentName(context, CursorAccessibilityService::class.java)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        val parser = info.loadXmlMetaData(context.packageManager, "android.accessibilityservice")!!
        parser.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* Root element. */ }
            assertEquals(0, it.getAttributeIntValue("http://schemas.android.com/apk/res/android", "accessibilityFlags", 0)
                and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON)
        }
        assertTrue(context.resources.getBoolean(R.bool.supports_accessibility_shortcut_activity))
        val targets = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET), PackageManager.GET_META_DATA)
        val target = targets.single { it.activityInfo.name == CursorShortcutActivity::class.java.name }
        assertEquals(R.xml.accessibility_shortcut, target.activityInfo.metaData.getInt("android.accessibilityshortcut.target"))
    }

    @Test @Config(sdk = [28]) fun legacyMetadataRetainsServiceButtonAndDisablesActivityTarget() {
        assertFalse(context.resources.getBoolean(R.bool.supports_accessibility_shortcut_activity))
        val component = ComponentName(context, CursorAccessibilityService::class.java)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        info.loadXmlMetaData(context.packageManager, "android.accessibilityservice")!!.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* Root element. */ }
            assertEquals(AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON,
                it.getAttributeIntValue("http://schemas.android.com/apk/res/android", "accessibilityFlags", 0)
                    and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON)
        }
    }
}
