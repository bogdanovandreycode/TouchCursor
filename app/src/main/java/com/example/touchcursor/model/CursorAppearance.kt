package com.example.touchcursor.model

enum class CursorShape(val title: String) {
    ARROW("Arrow"), CIRCLE("Circle"), CROSSHAIR("Crosshair"),
}

data class CursorAppearance(
    val shape: CursorShape = CursorShape.ARROW,
    val sizeDp: Int = 38,
    val opacity: Float = 1f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    val outlineWidthDp: Int = 2,
)
