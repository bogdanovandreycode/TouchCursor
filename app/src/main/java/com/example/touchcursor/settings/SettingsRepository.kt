package com.example.touchcursor.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.model.OverlayButtonConfig

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("touch_cursor", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = preferences.getFloat("sensitivity", 1.5f).coerceIn(0.25f, 4f)
        set(value) { preferences.edit().putFloat("sensitivity", value.coerceIn(0.25f, 4f)).apply() }

    fun button(action: OverlayAction): OverlayButtonConfig {
        val prefix = action.name
        return OverlayButtonConfig(
            action = action,
            enabled = action == OverlayAction.MODE || preferences.getBoolean("$prefix.enabled", false),
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
