package com.example.touchcursor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.touchcursor.input.TouchpadGestureDetector

@SuppressLint("ViewConstructor")
class TouchCaptureOverlayView(
    context: Context,
    private val detector: TouchpadGestureDetector,
    private val isSystemShortcut: (Float, Float) -> Boolean,
    private val onExit: () -> Unit,
) : View(context) {
    private var exitRequested = false
    private var shortcutRequested = false
    private var shortcutX = 0f
    private var shortcutY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                exitRequested = false
                shortcutRequested = isSystemShortcut(event.rawX, event.rawY)
                shortcutX = event.rawX
                shortcutY = event.rawY
                if (shortcutRequested) detector.reset()
                else detector.down(event.rawX, event.rawY, event.eventTime)
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                if (shortcutRequested && kotlin.math.hypot(event.rawX - shortcutX, event.rawY - shortcutY) > touchSlop) shortcutRequested = false
                if (!shortcutRequested) detector.move(event.rawX, event.rawY, event.eventTime)
            }
            MotionEvent.ACTION_UP -> if (exitRequested || (shortcutRequested && isSystemShortcut(event.rawX, event.rawY))) {
                exitRequested = false
                shortcutRequested = false
                detector.reset()
                // Release only after the last physical pointer is up.
                post { onExit() }
            } else detector.up(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_POINTER_DOWN -> {
                detector.cancel()
                exitRequested = true
            }
            MotionEvent.ACTION_CANCEL -> {
                detector.cancel()
                exitRequested = false
                shortcutRequested = false
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        detector.reset()
        exitRequested = false
        shortcutRequested = false
        super.onDetachedFromWindow()
    }
}
