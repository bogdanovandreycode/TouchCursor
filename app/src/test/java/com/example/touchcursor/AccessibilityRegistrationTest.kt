package com.example.touchcursor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.example.touchcursor.service.CursorAccessibilityService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 34])
class AccessibilityRegistrationTest {
    @Test @Suppress("DEPRECATION") fun onlyServiceIsExposedAndItsSettingsAndGestureCapabilitiesArePreserved() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val targets = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET), PackageManager.GET_META_DATA)
        assertTrue(targets.none { it.activityInfo.packageName == context.packageName })
        val services = pm.queryIntentServices(Intent("android.accessibilityservice.AccessibilityService")
            .setPackage(context.packageName), PackageManager.GET_META_DATA)
        assertEquals(listOf(CursorAccessibilityService::class.java.name), services.map { it.serviceInfo.name })
        val info = pm.getServiceInfo(ComponentName(context, CursorAccessibilityService::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", info.permission)
        info.loadXmlMetaData(pm, "android.accessibilityservice")!!.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* Root element. */ }
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals(AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON,
                it.getAttributeIntValue(ns, "accessibilityFlags", 0) and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON)
            assertTrue(it.getAttributeBooleanValue(ns, "canPerformGestures", false))
            assertTrue(it.getAttributeBooleanValue(ns, "canRetrieveWindowContent", false))
            assertEquals(MainActivity::class.java.name, it.getAttributeValue(ns, "settingsActivity"))
        }
    }
}
