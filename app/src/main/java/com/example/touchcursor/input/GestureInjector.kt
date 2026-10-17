package com.example.touchcursor.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

class GestureInjector(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var busy = false
    private var closed = false
    private var watchdog: Runnable? = null

    fun inject(x: Float, y: Float, duration: Long, result: (Boolean) -> Unit) {
        if (closed || busy) {
            result(false)
            return
        }
        busy = true
        val token = ++generation
        fun finish(success: Boolean) {
            if (closed || token != generation || !busy) return
            busy = false
            watchdog?.let(handler::removeCallbacks)
            watchdog = null
            result(success)
        }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, duration)
        ).build()
        watchdog = Runnable { finish(false) }.also {
            // Recovery only: a disconnected/OEM service may lose its callback.
            handler.postDelayed(it, duration + 2000)
        }
        try {
            val accepted = service.dispatchGesture(gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) = finish(true)
                    override fun onCancelled(gestureDescription: GestureDescription) = finish(false)
                }, handler)
            if (!accepted) finish(false)
        } catch (_: RuntimeException) {
            finish(false)
        }
    }

    fun close() {
        closed = true
        generation++
        busy = false
        watchdog?.let(handler::removeCallbacks)
        watchdog = null
    }
}
