package com.example.touchcursor.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock

class GestureInjector(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var busy = false
    private var closed = false
    private var watchdog: Runnable? = null
    private var releaseHold: (() -> Unit)? = null
    private var continuation: Runnable? = null
    private var cancelHold: (() -> Unit)? = null

    fun inject(x: Float, y: Float, duration: Long, result: (Boolean) -> Unit) {
        injectPath(Path().apply { moveTo(x, y) }, duration, 0, result)
    }

    /** Keep the same injected pointer down between the stationary hold and movement. */
    fun injectPath(path: Path, duration: Long, holdDuration: Long, result: (Boolean) -> Unit) {
        if (closed || busy) {
            result(false)
            return
        }
        if (holdDuration > 0 && Build.VERSION.SDK_INT < 26) {
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
            continuation?.let(handler::removeCallbacks)
            continuation = null
            cancelHold = null
            if (!success) releaseHold?.invoke()
            releaseHold = null
            result(success)
        }
        val movementDuration = duration.coerceIn(1, GestureDescription.getMaxGestureDuration())
        val hold = holdDuration.coerceIn(0, GestureDescription.getMaxGestureDuration())
        val movement = Path(path)
        val start = FloatArray(2)
        // PathMeasure cannot sample a zero-length path (e.g. movement clamped at an edge).
        // Extend only a copy to recover its actual start, preserving the injected geometry.
        val probe = Path(movement).apply { rLineTo(1f, 0f) }
        android.graphics.PathMeasure(probe, false).getPosTan(0f, start, null)
        val heldStroke = if (hold > 0 && Build.VERSION.SDK_INT >= 26)
            GestureDescription.StrokeDescription(Path().apply { moveTo(start[0], start[1]) }, 0, hold, true)
        else null
        if (heldStroke != null && Build.VERSION.SDK_INT >= 26) {
            cancelHold = { finish(false) }
            releaseHold = {
                // A continued hold otherwise has no scheduled UP if teardown loses its callback.
                try {
                    val end = heldStroke.continueStroke(Path().apply { moveTo(start[0], start[1]) }, 0, 1, false)
                    service.dispatchGesture(GestureDescription.Builder().addStroke(end).build(), null, handler)
                } catch (_: RuntimeException) { /* The system may already have cancelled the stream. */ }
            }
        }

        fun dispatch(stroke: GestureDescription.StrokeDescription, holding: Boolean) {
            if (closed || token != generation || !busy) return
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val holdUntil = SystemClock.uptimeMillis() + stroke.duration
            watchdog?.let(handler::removeCallbacks)
            watchdog = Runnable { finish(false) }.also {
                handler.postDelayed(it, stroke.duration + 2000)
            }
            try {
                val accepted = service.dispatchGesture(gesture,
                object : AccessibilityService.GestureResultCallback() {
                    private var delivered = false
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        if (delivered || closed || token != generation || !busy) return
                        delivered = true
                        if (holding && Build.VERSION.SDK_INT >= 26) {
                            // A stationary continued stroke may emit only DOWN: Android can
                            // complete its callback immediately, before its declared duration.
                            // Keep that pointer held until the requested hold has elapsed.
                            continuation = Runnable {
                                continuation = null
                                dispatch(stroke.continueStroke(movement, 0, movementDuration, false), false)
                                releaseHold = null
                                cancelHold = null
                            }.also { handler.postDelayed(it, (holdUntil - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
                        } else finish(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        if (delivered) return
                        delivered = true
                        finish(false)
                    }
                }, handler)
                if (!accepted) finish(false)
            } catch (_: RuntimeException) {
                finish(false)
            }
        }
        dispatch(heldStroke ?: GestureDescription.StrokeDescription(movement, 0, movementDuration), heldStroke != null)
    }

    /** Stop an unfinished hold before its movement starts, e.g. on lock/app/mode changes. */
    fun cancelPendingHold() { cancelHold?.invoke() }

    fun close() {
        closed = true
        generation++
        busy = false
        watchdog?.let(handler::removeCallbacks)
        watchdog = null
        continuation?.let(handler::removeCallbacks)
        continuation = null
        cancelHold = null
        releaseHold?.invoke()
        releaseHold = null
    }
}
