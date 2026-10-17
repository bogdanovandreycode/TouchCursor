package com.example.touchcursor.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.touchcursor.actions.KeyboardController
import com.example.touchcursor.actions.TextActionController
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.overlay.OverlayManager

class CursorAccessibilityService : AccessibilityService() {
    private var overlays: OverlayManager? = null
    private var receiverRegistered = false
    private var keyboard: KeyboardController? = null
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
        }.also { it.show() }
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { overlays?.setCursorMode(false) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlays?.onDisplayChanged()
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
