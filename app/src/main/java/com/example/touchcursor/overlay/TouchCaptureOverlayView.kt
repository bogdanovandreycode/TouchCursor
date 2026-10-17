package com.example.touchcursor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.example.touchcursor.input.TouchpadGestureDetector

@SuppressLint("ViewConstructor")
class TouchCaptureOverlayView(context: Context, private val detector: TouchpadGestureDetector) : View(context) {
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> detector.down(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) detector.move(event.rawX, event.rawY)
            MotionEvent.ACTION_UP -> detector.up(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> detector.cancel()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        detector.reset()
        super.onDetachedFromWindow()
    }
}
