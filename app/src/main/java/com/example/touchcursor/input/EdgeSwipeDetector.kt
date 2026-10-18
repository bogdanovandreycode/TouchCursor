package com.example.touchcursor.input

import com.example.touchcursor.model.ScreenEdge
import kotlin.math.abs
import kotlin.math.hypot

class EdgeSwipeDetector(
    private val threshold: Float,
    private val touchSlop: Float,
    private val onInward: () -> Unit,
    private val onOutward: () -> Unit,
    private val onTap: () -> Unit,
    private val outwardThreshold: Float = threshold,
) {
    var edge = ScreenEdge.RIGHT
    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var moved = false

    fun down(x: Float, y: Float) {
        startX = x
        startY = y
        tracking = true
        moved = false
    }

    fun move(x: Float, y: Float) {
        if (tracking && hypot(x - startX, y - startY) > touchSlop) moved = true
    }

    fun up(x: Float, y: Float) {
        if (!tracking) return
        move(x, y)
        tracking = false
        val dx = x - startX
        val dy = y - startY
        val inward = when (edge) {
            ScreenEdge.LEFT -> dx
            ScreenEdge.RIGHT -> -dx
            ScreenEdge.TOP -> dy
            ScreenEdge.BOTTOM -> -dy
        }
        val cross = if (edge.isVertical) dy else dx
        when {
            inward >= threshold && inward > abs(cross) * 1.2f -> onInward()
            -inward >= outwardThreshold && -inward > abs(cross) * 1.2f -> onOutward()
            !moved -> onTap()
        }
    }

    fun cancel() { tracking = false }
}
