package com.example.touchcursor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.touchcursor.input.EdgeSwipeDetector
import com.example.touchcursor.model.ScreenEdge

@SuppressLint("ViewConstructor")
class EdgeHandleView(context: Context, private val reveal: () -> Unit, private val hide: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chevron = Path()
    private val density = resources.displayMetrics.density
    private var edge = ScreenEdge.RIGHT
    private var expanded = false
    private val detector = EdgeSwipeDetector(32 * density, ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        onInward = reveal, onOutward = hide, onTap = { if (expanded) performClick() }, outwardThreshold = 4 * density)

    init { isClickable = true }

    fun configure(side: ScreenEdge, open: Boolean) {
        if (edge != side || expanded != open) detector.cancel()
        edge = side
        expanded = open
        detector.edge = side
        contentDescription = if (open) "Hide Touch Cursor controls" else "Swipe inward to reveal Touch Cursor controls"
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
    }

    override fun onDraw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(170, 35, 47, 65)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 7 * density, 7 * density, paint)
        var nx = when (edge) { ScreenEdge.LEFT -> 1f; ScreenEdge.RIGHT -> -1f; else -> 0f }
        var ny = when (edge) { ScreenEdge.TOP -> 1f; ScreenEdge.BOTTOM -> -1f; else -> 0f }
        if (expanded) { nx = -nx; ny = -ny }
        val cx = width / 2f
        val cy = height / 2f
        val size = 3 * density
        chevron.reset()
        chevron.apply {
            moveTo(cx - nx * size - ny * size, cy - ny * size + nx * size)
            lineTo(cx + nx * size, cy + ny * size)
            lineTo(cx - nx * size + ny * size, cy - ny * size - nx * size)
        }
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 1.5f * density
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(chevron, paint)
    }

    // The detector calls performClick for taps on an expanded handle; closed handles require a swipe.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> detector.down(event.rawX, event.rawY)
            MotionEvent.ACTION_MOVE -> detector.move(event.rawX, event.rawY)
            MotionEvent.ACTION_UP -> detector.up(event.rawX, event.rawY)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> detector.cancel()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (expanded) hide() else reveal()
        return true
    }

    override fun onDetachedFromWindow() {
        detector.cancel()
        super.onDetachedFromWindow()
    }
}
