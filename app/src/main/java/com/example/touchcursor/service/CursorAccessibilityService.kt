package com.example.touchcursor.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.touchcursor.actions.KeyboardController
import com.example.touchcursor.actions.TextActionController
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.overlay.OverlayManager
import com.example.touchcursor.settings.SettingsRepository

class CursorAccessibilityService : AccessibilityService() {
    private var overlays: OverlayManager? = null
    private var receiverRegistered = false
    private var keyboard: KeyboardController? = null
    private var settings: SettingsRepository? = null
    private var foreground: ForegroundAppTracker? = null
    private val handler = Handler(Looper.getMainLooper())
    private var eventPackage: String? = null
    private var eventWindowId = -1
    private var appExcluded = false
    private var foregroundScheduled = false
    private val foregroundRefresh = Runnable {
        foregroundScheduled = false
        val current = foreground?.refresh(eventPackage, eventWindowId)
        eventPackage = null
        eventWindowId = -1
        val excludedPackages = settings?.excludedPackages ?: emptySet()
        val excluded = excludedPackages.isNotEmpty() && current != packageName &&
            (current == null || current in excludedPackages)
        if (excluded && !appExcluded) keyboard?.close()
        appExcluded = excluded
        overlays?.setExcluded(excluded)
    }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "excluded_packages") scheduleForegroundRefresh()
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> overlays?.suspend()
                Intent.ACTION_SCREEN_ON -> if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) overlays?.resume()
                Intent.ACTION_USER_PRESENT -> overlays?.resume()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlays?.close()
        settings?.stopObserving(settingsListener)
        settings = SettingsRepository(this).also { it.observe(settingsListener) }
        foreground = ForegroundAppTracker(this)
        appExcluded = false
        keyboard?.close()
        keyboard = KeyboardController(this)
        val textActions = TextActionController(this)
        overlays = OverlayManager(this) { action ->
            val message = when (action) {
                OverlayAction.COPY -> if (textActions.copy()) "Copied" else "Copy is not supported by the focused selection"
                OverlayAction.PASTE -> if (textActions.paste()) "Pasted" else "Paste is not supported by the focused field"
                OverlayAction.KEYBOARD -> keyboard?.toggle()
                OverlayAction.MODE -> null
            }
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        foregroundRefresh.run()
        overlays?.show()
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            overlays?.suspend()
        running = true
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (Build.VERSION.SDK_INT >= 28) {
                val relevant = AccessibilityEvent.WINDOWS_CHANGE_ADDED or AccessibilityEvent.WINDOWS_CHANGE_REMOVED or
                    AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED
                if (event.windowChanges and relevant != 0) scheduleForegroundRefresh()
            } else scheduleForegroundRefresh(100)
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            eventPackage = event.packageName?.toString()
            eventWindowId = event.windowId
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) scheduleForegroundRefresh()
    }

    private fun scheduleForegroundRefresh(delay: Long = 0) {
        if (foreground == null || (delay > 0 && foregroundScheduled)) return
        if (delay == 0L) handler.removeCallbacks(foregroundRefresh)
        foregroundScheduled = true
        handler.postDelayed(foregroundRefresh, delay)
    }
    override fun onInterrupt() { overlays?.setCursorMode(false) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlays?.onDisplayChanged()
        scheduleForegroundRefresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        running = false
        handler.removeCallbacks(foregroundRefresh)
        foregroundScheduled = false
        settings?.stopObserving(settingsListener)
        settings = null
        foreground = null
        keyboard?.close()
        keyboard = null
        overlays?.close()
        overlays = null
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
    }

    companion object {
        var running = false
            private set
    }
}
