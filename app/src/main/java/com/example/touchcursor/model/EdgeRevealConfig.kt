package com.example.touchcursor.model

enum class ScreenEdge(val title: String) {
    LEFT("Left"), RIGHT("Right"), TOP("Top"), BOTTOM("Bottom");

    val isVertical: Boolean get() = this == LEFT || this == RIGHT
}

data class EdgeRevealConfig(
    val enabled: Boolean = false,
    val edge: ScreenEdge = ScreenEdge.RIGHT,
    val position: Float = 0.5f,
    val lengthDp: Int = 96,
    val thicknessDp: Int = 14,
)
