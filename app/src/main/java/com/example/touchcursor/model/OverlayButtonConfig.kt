package com.example.touchcursor.model

import com.example.touchcursor.R

enum class OverlayAction(val title: String, val label: String, val iconRes: Int) {
    MODE("Cursor mode", "OFF", R.drawable.ic_mode),
    COPY("Copy", "COPY", R.drawable.ic_copy),
    PASTE("Paste", "PASTE", R.drawable.ic_paste),
    KEYBOARD("Keyboard", "KEYS", R.drawable.ic_keyboard),
}

data class OverlayButtonConfig(
    val action: OverlayAction,
    val enabled: Boolean,
    val xNormalized: Float,
    val yNormalized: Float,
    val widthDp: Int,
    val heightDp: Int,
    val opacity: Float,
) {
    val id: String get() = action.name.lowercase(java.util.Locale.ROOT)
}
