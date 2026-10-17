package com.example.touchcursor.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Visual cursor only. Must never consume input. */
class CursorOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path().apply {
        moveTo(0f, 0f)
        lineTo(2f, 25f)
        lineTo(8f, 19f)
        lineTo(14f, 30f)
        lineTo(19f, 27f)
        lineTo(13f, 17f)
        lineTo(22f, 16f)
        close()
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(resources.displayMetrics.density, resources.displayMetrics.density)
        canvas.translate(3f, 3f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawPath(arrow, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.BLACK
        canvas.drawPath(arrow, paint)
        canvas.restore()
    }
}
