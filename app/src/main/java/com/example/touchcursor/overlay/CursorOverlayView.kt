package com.example.touchcursor.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.example.touchcursor.model.CursorAppearance
import com.example.touchcursor.model.CursorShape

/** Visual cursor only. Must never consume input. */
class CursorOverlayView(context: Context) : View(context) {
    var appearance = CursorAppearance()
        set(value) { field = value; invalidate() }
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
        offset(3f, 3f)
    }
    private val circle = Path().apply { addCircle(16f, 19f, 11f, Path.Direction.CW) }
    private val crosshair = Path().apply {
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 17f)
        lineTo(28f, 17f); lineTo(28f, 21f); lineTo(18f, 21f)
        lineTo(18f, 33f); lineTo(14f, 33f); lineTo(14f, 21f)
        lineTo(4f, 21f); lineTo(4f, 17f); lineTo(14f, 17f); close()
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(paddingPx, paddingPx)
        val scale = resources.displayMetrics.density * appearance.sizeDp / 38f
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = appearance.color
        val shape = when (appearance.shape) {
            CursorShape.ARROW -> arrow
            CursorShape.CIRCLE -> circle
            CursorShape.CROSSHAIR -> crosshair
        }
        if (appearance.outlineWidthDp > 0) {
            paint.style = Paint.Style.STROKE
            // Keep the selected outline thickness in dp regardless of cursor size.
            paint.strokeWidth = appearance.outlineWidthDp * 38f / appearance.sizeDp
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = appearance.outlineColor
            canvas.drawPath(shape, paint)
        }
        // Fill last so a thick outline never hides the color of a small crosshair.
        paint.style = Paint.Style.FILL
        paint.color = appearance.color
        canvas.drawPath(shape, paint)
        canvas.restore()
    }

    // Leave enough room for the maximum outline even at the smallest size.
    val paddingPx get() = 3 * resources.displayMetrics.density
    val hotspotX get() = paddingPx + (if (appearance.shape == CursorShape.ARROW) 3f else 16f) * resources.displayMetrics.density * appearance.sizeDp / 38f
    val hotspotY get() = paddingPx + (if (appearance.shape == CursorShape.ARROW) 3f else 19f) * resources.displayMetrics.density * appearance.sizeDp / 38f
}
