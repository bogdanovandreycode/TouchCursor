package com.example.touchcursor.input

class CursorController {
    var x = 0f
        private set
    var y = 0f
        private set
    var sensitivity = 1.5f
    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f
    private var initialized = false

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = (right - 1f).coerceAtLeast(left)
        this.bottom = (bottom - 1f).coerceAtLeast(top)
        if (!initialized) {
            x = (left + this.right) / 2f
            y = (top + this.bottom) / 2f
            initialized = true
        }
        clamp()
    }

    fun move(dx: Float, dy: Float) {
        x += dx * sensitivity
        y += dy * sensitivity
        clamp()
    }

    private fun clamp() {
        x = x.coerceIn(left, right)
        y = y.coerceIn(top, bottom)
    }
}
