package com.example.touchcursor.input

import kotlin.math.hypot

/** Injection is requested only after the last physical pointer is up. */
class TouchpadGestureDetector(
    private val touchSlop: Float,
    private val doubleTapSlop: Float,
    private val doubleTapTimeout: Long,
    private val longPressTimeout: Long,
    private val onMove: (Float, Float) -> Unit,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private var down = false
    private var cancelled = false
    private var moved = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var firstUpTime: Long? = null
    private var firstX = 0f
    private var firstY = 0f
    private var secondTap = false

    fun down(x: Float, y: Float, time: Long) {
        secondTap = firstUpTime?.let {
            time - it in 0..doubleTapTimeout && distance(x, y, firstX, firstY) <= doubleTapSlop
        } ?: false
        firstUpTime = null
        down = true
        cancelled = false
        moved = false
        startX = x
        startY = y
        lastX = x
        lastY = y
        downTime = time
    }

    fun move(x: Float, y: Float) {
        if (!down || cancelled) return
        if (!moved && distance(x, y, startX, startY) > touchSlop) {
            moved = true
            onMove(x - startX, y - startY)
        } else if (moved) {
            onMove(x - lastX, y - lastY)
        }
        lastX = x
        lastY = y
    }

    fun up(x: Float, y: Float, time: Long) {
        if (!down) return
        move(x, y)
        down = false
        if (cancelled || moved) return
        when {
            time - downTime >= longPressTimeout -> onLongPress()
            secondTap -> onTap()
            else -> {
                firstUpTime = time
                firstX = x
                firstY = y
            }
        }
    }

    fun cancel() {
        cancelled = true
        firstUpTime = null
        secondTap = false
    }

    fun reset() {
        cancel()
        down = false
    }

    private fun distance(x: Float, y: Float, otherX: Float, otherY: Float) =
        hypot(x - otherX, y - otherY)
}
