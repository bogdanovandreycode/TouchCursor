package com.example.touchcursor.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.model.OverlayButtonConfig
import com.example.touchcursor.model.EdgeRevealConfig
import com.example.touchcursor.model.ScreenEdge
import com.example.touchcursor.model.CursorAppearance
import com.example.touchcursor.model.CursorShape

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("touch_cursor", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = preferences.getFloat("sensitivity", 1.5f).coerceIn(0.25f, 4f)
        set(value) { preferences.edit().putFloat("sensitivity", value.coerceIn(0.25f, 4f)).apply() }

    var excludedPackages: Set<String>
        get() = preferences.getStringSet("excluded_packages", emptySet())?.toSet() ?: emptySet()
        set(value) { preferences.edit().putStringSet("excluded_packages", value.toSet()).apply() }

    fun cursorAppearance() = CursorAppearance(
        shape = CursorShape.entries.firstOrNull { it.name == preferences.getString("cursor.shape", "ARROW") } ?: CursorShape.ARROW,
        sizeDp = preferences.getInt("cursor.size", 38).coerceIn(16, 96),
        opacity = preferences.getFloat("cursor.opacity", 1f).coerceIn(0.1f, 1f),
        color = preferences.getInt("cursor.color", -1),
        outlineColor = preferences.getInt("cursor.outlineColor", 0xFF000000.toInt()),
        outlineWidthDp = preferences.getInt("cursor.outlineWidth", 2).coerceIn(0, 6),
    )

    fun saveCursorAppearance(config: CursorAppearance) {
        preferences.edit()
            .putString("cursor.shape", config.shape.name)
            .putInt("cursor.size", config.sizeDp.coerceIn(16, 96))
            .putFloat("cursor.opacity", config.opacity.coerceIn(0.1f, 1f))
            .putInt("cursor.color", config.color or 0xFF000000.toInt())
            .putInt("cursor.outlineColor", config.outlineColor or 0xFF000000.toInt())
            .putInt("cursor.outlineWidth", config.outlineWidthDp.coerceIn(0, 6))
            .apply()
    }

    fun edgeReveal() = EdgeRevealConfig(
        enabled = preferences.getBoolean("edge.enabled", false),
        edge = ScreenEdge.entries.firstOrNull { it.name == preferences.getString("edge.side", "RIGHT") } ?: ScreenEdge.RIGHT,
        position = preferences.getFloat("edge.position", 0.5f).coerceIn(0f, 1f),
        lengthDp = preferences.getInt("edge.length", 96).coerceIn(48, 240),
        thicknessDp = preferences.getInt("edge.thickness", 14).coerceIn(8, 32),
    )

    fun saveEdgeReveal(config: EdgeRevealConfig) {
        preferences.edit()
            .putBoolean("edge.enabled", config.enabled)
            .putString("edge.side", config.edge.name)
            .putFloat("edge.position", config.position.coerceIn(0f, 1f))
            .putInt("edge.length", config.lengthDp.coerceIn(48, 240))
            .putInt("edge.thickness", config.thicknessDp.coerceIn(8, 32))
            .apply()
    }

    fun button(action: OverlayAction): OverlayButtonConfig {
        val prefix = action.name
        return OverlayButtonConfig(
            action = action,
            enabled = preferences.getBoolean("$prefix.enabled", action == OverlayAction.MODE),
            xNormalized = preferences.getFloat("$prefix.x", 1f).coerceIn(0f, 1f),
            yNormalized = preferences.getFloat("$prefix.y", 0.25f + action.ordinal * 0.14f).coerceIn(0f, 1f),
            widthDp = preferences.getInt("$prefix.width", 64).coerceIn(40, 160),
            heightDp = preferences.getInt("$prefix.height", 56).coerceIn(40, 160),
            opacity = preferences.getFloat("$prefix.opacity", 0.9f).coerceIn(0.3f, 1f),
        )
    }

    fun save(config: OverlayButtonConfig) {
        val prefix = config.action.name
        preferences.edit()
            .putBoolean("$prefix.enabled", config.enabled)
            .putFloat("$prefix.x", config.xNormalized.coerceIn(0f, 1f))
            .putFloat("$prefix.y", config.yNormalized.coerceIn(0f, 1f))
            .putInt("$prefix.width", config.widthDp.coerceIn(40, 160))
            .putInt("$prefix.height", config.heightDp.coerceIn(40, 160))
            .putFloat("$prefix.opacity", config.opacity.coerceIn(0.3f, 1f))
            .apply()
    }

    fun reset() { preferences.edit().clear().apply() }
    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)
    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
}
