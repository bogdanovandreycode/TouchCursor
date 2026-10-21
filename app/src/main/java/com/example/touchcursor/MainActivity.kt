package com.example.touchcursor

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.EditText
import android.widget.FrameLayout
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.service.CursorAccessibilityService
import com.example.touchcursor.settings.SettingsRepository
import com.example.touchcursor.settings.AppExclusionsDialog
import com.example.touchcursor.model.ScreenEdge
import com.example.touchcursor.model.CursorShape
import com.example.touchcursor.overlay.CursorOverlayView
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var repository: SettingsRepository
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdate = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(this)
        render()
    }

    private fun render() {
        content = column()
        val scroll = ScrollView(this).apply { addView(content) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
        text(content, "Touch Cursor", 26f)
        status = text(content, "", 16f)
        text(content, "Enable Touch Cursor Service in Android Accessibility settings. It draws controls over apps and sends touch gestures only when you request them. Copy and paste use the focused app's accessibility support. No screen text or clipboard content is stored or transmitted.")
        button(content, "Open Accessibility settings") {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: RuntimeException) { Toast.makeText(this, "Accessibility settings are unavailable", Toast.LENGTH_LONG).show() }
        }
        text(content, "Tap the floating arrow button (OFF/ON) to switch modes. Swipe to move the arrow; double-tap to click at its tip. Point at a page or list, hold still, then move your finger to scroll while holding. The cursor stays over that area. You can also tap once, then immediately touch again and swipe. Some apps scroll in steps. Areas without scrolling support replay the gesture after release (hold and drag requires Android 8+). Hold without moving for a long press. Touch with two fingers and lift both to exit. System bars remain accessible.")
        text(content, "Cursor sensitivity", 20f)
        slider(content, "Sensitivity", ((repository.sensitivity - 0.25f) * 100).roundToInt(), 0, 375,
            { String.format(Locale.US, "%.2f×", 0.25f + it / 100f) },
            { repository.sensitivity = 0.25f + it / 100f })
        button(content, "Configure cursor appearance") { configureCursor() }
        text(content, "System accessibility shortcut", 20f)
        text(content, when {
            Build.VERSION.SDK_INT >= 30 -> "Enable Touch Cursor Service in Android Accessibility settings. Its system shortcut is optional: the app's floating buttons work with or without it. Use Show button in each button's configuration to choose the app controls. The system shortcut turns the service and its controls on or off; use the app's Cursor mode button to switch cursor mode. If the system icon is covered by cursor input, tapping it first exits cursor mode. Touch with two fingers and lift both to exit. All system options remain in the same Touch Cursor Service entry."
            Build.VERSION.SDK_INT >= 26 -> "Use the single Touch Cursor Service entry in Android Accessibility settings for access, app settings and the optional system button. Its accessibility button switches cursor mode on and off while the service is enabled. Keep the app's Cursor mode button enabled until the system button works. Touch with two fingers and lift both to exit."
            else -> "The system accessibility button requires Android 8.0 or newer. Keep the floating Cursor mode button enabled on this device."
        })
        text(content, "Edge swipe controls", 20f)
        text(content, "Optionally hide the buttons behind a small edge handle. Swipe inward to show them; tap the handle or swipe outward to hide them and return to normal touch.")
        button(content, "Configure edge swipe") { configureEdgeSwipe() }
        text(content, "App exclusions", 20f)
        val excludedCount = text(content, getString(R.string.excluded_apps_count, repository.excludedPackages.size))
        button(content, "Choose excluded apps") {
            AppExclusionsDialog(this, repository) {
                excludedCount.text = getString(R.string.excluded_apps_count, repository.excludedPackages.size)
            }.show()
        }
        text(content, "Floating buttons", 20f)
        text(content, "Configure each control independently. Positions are percentages of the available travel area, so they adapt to rotation. Controls only move when you change their settings.")
        for (action in OverlayAction.entries) {
            button(content, "Configure ${action.title}", action.iconRes) { configureButton(action) }
        }
        button(content, "Reset settings to defaults") {
            AlertDialog.Builder(this).setTitle("Reset settings?")
                .setMessage("Restore all settings, including button layout, edge swipe and app exclusions?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ -> repository.reset(); render() }.show()
        }
        updateStatus()
    }

    private fun configureCursor() {
        var config = repository.cursorAppearance()
        val form = column()
        val preview = CursorOverlayView(this)
        val previewBox = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(135, 145, 155))
            minimumHeight = (124 * resources.displayMetrics.density).roundToInt()
        }
        form.addView(previewBox)
        previewBox.addView(preview)
        fun updatePreview() {
            preview.appearance = config
            preview.alpha = config.opacity
            preview.layoutParams = FrameLayout.LayoutParams(
                ((32f * config.sizeDp / 38f + 6) * resources.displayMetrics.density).roundToInt(),
                ((config.sizeDp + 6) * resources.displayMetrics.density).roundToInt(), Gravity.CENTER,
            )
        }
        updatePreview()
        text(form, "Cursor shape")
        form.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, CursorShape.entries.map { it.title })
            setSelection(config.shape.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    config = config.copy(shape = CursorShape.entries[position]); updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        slider(form, "Cursor size", config.sizeDp, 16, 96, { "$it dp" },
            { config = config.copy(sizeDp = it); updatePreview() })
        slider(form, "Cursor opacity", (config.opacity * 100).roundToInt(), 10, 100, { "$it%" },
            { config = config.copy(opacity = it / 100f); updatePreview() })
        fun colorField(label: String, initial: Int, change: (Int) -> Unit): EditText {
            text(form, label)
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine(true)
                setText(String.format(Locale.US, "#%06X", initial and 0xFFFFFF))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val value = s.toString().trim()
                        if (value.matches(Regex("#[0-9a-fA-F]{6}"))) {
                            error = null; change(Color.parseColor(value)); updatePreview()
                        } else error = "Use #RRGGBB, for example #FFFFFF"
                    }
                })
                form.addView(this)
            }
        }
        val fill = colorField("Cursor color (#RRGGBB)", config.color) { config = config.copy(color = it) }
        val outline = colorField("Outline color (#RRGGBB)", config.outlineColor) { config = config.copy(outlineColor = it) }
        slider(form, "Outline width (0 disables outline)", config.outlineWidthDp, 0, 6, { "$it dp" },
            { config = config.copy(outlineWidthDp = it); updatePreview() })
        val dialog = AlertDialog.Builder(this).setTitle("Cursor appearance")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val invalid = listOf(fill, outline).firstOrNull { !it.text.toString().trim().matches(Regex("#[0-9a-fA-F]{6}")) }
                if (invalid != null) { invalid.error = "Use #RRGGBB"; invalid.requestFocus() }
                else {
                    repository.saveCursorAppearance(config)
                    Toast.makeText(this, "Cursor appearance saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun configureEdgeSwipe() {
        var config = repository.edgeReveal()
        val form = column()
        form.addView(CheckBox(this).apply {
            setText(R.string.edge_reveal_enabled)
            isChecked = config.enabled
            setOnCheckedChangeListener { _, checked -> config = config.copy(enabled = checked) }
        })
        text(form, "Screen edge")
        form.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, ScreenEdge.entries.map { it.title })
            setSelection(config.edge.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    config = config.copy(edge = ScreenEdge.entries[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        text(form, "Position runs top to bottom on the left/right edges, and left to right on the top/bottom edges. Length is the active strip along the edge; thickness is its distance into the screen.")
        slider(form, "Trigger position", (config.position * 100).roundToInt(), 0, 100, { "$it%" },
            { config = config.copy(position = it / 100f) })
        slider(form, "Trigger length", config.lengthDp, 48, 240, { "$it dp" }, { config = config.copy(lengthDp = it) })
        slider(form, "Trigger thickness", config.thicknessDp, 8, 32, { "$it dp" }, { config = config.copy(thicknessDp = it) })
        AlertDialog.Builder(this).setTitle("Edge swipe")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                repository.saveEdgeReveal(config)
                Toast.makeText(this, "Edge swipe settings saved", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun configureButton(action: OverlayAction) {
        var config = repository.button(action)
        val form = column()
        form.addView(CheckBox(this).apply {
            setText(R.string.show_button)
            isChecked = config.enabled
            setOnCheckedChangeListener { _, checked -> config = config.copy(enabled = checked) }
        })
        if (action == OverlayAction.MODE) text(form, when {
            Build.VERSION.SDK_INT >= 30 -> "When disabled, this button stays hidden in both modes. Keep it enabled to enter cursor mode: the Android shortcut switches the service on or off. Touch with two fingers and lift both to exit cursor mode. Reopen this app to show the button again."
            Build.VERSION.SDK_INT >= 26 -> "When disabled, this button stays hidden in both modes. Use the Android accessibility shortcut to enter and exit cursor mode, or touch with two fingers and lift both to exit. Reopen this app to show the button again."
            else -> "When disabled, this button stays hidden in both modes. Keep it enabled to enter cursor mode on this device. Touch with two fingers and lift both to exit. Reopen this app to show the button again."
        })
        text(form, "Button opacity: 100% is solid; 30% is faint. Applies to the icon, label and background.")
        slider(form, "Button opacity", (config.opacity * 100).roundToInt(), 30, 100, { "$it%" },
            { config = config.copy(opacity = it / 100f) })
        slider(form, "Horizontal position", (config.xNormalized * 100).roundToInt(), 0, 100, { "$it%" },
            { config = config.copy(xNormalized = it / 100f) })
        slider(form, "Vertical position", (config.yNormalized * 100).roundToInt(), 0, 100, { "$it%" },
            { config = config.copy(yNormalized = it / 100f) })
        slider(form, "Width", config.widthDp, 40, 160, { "$it dp" }, { config = config.copy(widthDp = it) })
        slider(form, "Height", config.heightDp, 40, 160, { "$it dp" }, { config = config.copy(heightDp = it) })
        AlertDialog.Builder(this).setTitle(action.title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                repository.save(config)
                Toast.makeText(this, "Button settings saved", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun slider(parent: LinearLayout, name: String, initial: Int, min: Int, max: Int,
                       format: (Int) -> String, changed: (Int) -> Unit) {
        val label = text(parent, "$name: ${format(initial)}")
        parent.addView(SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            contentDescription = name
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    label.text = getString(R.string.setting_value, name, format(progress + min))
                    if (fromUser) changed(progress + min)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        })
    }

    private fun updateStatus() {
        val expected = ComponentName(this, CursorAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
        status.text = when {
            CursorAccessibilityService.running -> "Service: connected"
            enabled -> "Service: enabled, waiting for Android to connect"
            else -> "Service: disabled"
        }
        if (Build.VERSION.SDK_INT in 26..29 && CursorAccessibilityService.running) {
            status.append(if (CursorAccessibilityService.accessibilityButtonAvailable)
                "\nSystem navigation accessibility button: available" else "\nSystem navigation accessibility button: unavailable; floating shortcuts may still work")
        }
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).roundToInt()
        setPadding(padding, padding, padding, padding)
    }

    private fun text(parent: LinearLayout, value: String, size: Float = 15f): TextView = TextView(this).apply {
        text = value
        textSize = size
        setPadding(0, 12, 0, 12)
        parent.addView(this)
    }

    private fun button(parent: LinearLayout, label: String, iconRes: Int? = null, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            if (iconRes != null) {
                val size = (22 * resources.displayMetrics.density).roundToInt()
                val icon = getDrawable(iconRes)?.mutate()?.apply {
                    setTint(currentTextColor)
                    setBounds(0, 0, size, size)
                }
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = (8 * resources.displayMetrics.density).roundToInt()
            }
            setOnClickListener { action() }
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdate)
    }

    override fun onPause() {
        handler.removeCallbacks(statusUpdate)
        super.onPause()
    }
}
