package com.example.touchcursor.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.example.touchcursor.R
import com.example.touchcursor.input.CursorController
import com.example.touchcursor.input.GestureInjector
import com.example.touchcursor.input.TouchpadGestureDetector
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.settings.SettingsRepository
import com.example.touchcursor.model.ScreenEdge

/** Owns every window. All transitions and gesture callbacks run on the main thread. */
class OverlayManager(private val service: AccessibilityService, private val onAction: (OverlayAction) -> Unit) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val attached = linkedMapOf<View, WindowManager.LayoutParams>()
    private val controller = CursorController()
    private val injector = GestureInjector(service)
    private val settings = SettingsRepository(service)
    private var edgeConfig = settings.edgeReveal()
    private var controlsExpanded = !edgeConfig.enabled
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable {
        controller.sensitivity = settings.sensitivity
        val updated = settings.edgeReveal()
        if (updated.enabled != edgeConfig.enabled) {
            controlsExpanded = !updated.enabled
            cursorMode = false
            detector.reset()
            remove(capture)
            buttons.values.forEach(::remove)
            remove(edgeHandle)
        }
        edgeConfig = updated
        syncWindows()
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
    private val cursor = CursorOverlayView(service)
    private val configuration = ViewConfiguration.get(service)
    private val detector = TouchpadGestureDetector(
        configuration.scaledTouchSlop.toFloat(), configuration.scaledDoubleTapSlop.toFloat(),
        ViewConfiguration.getDoubleTapTimeout().toLong(), ViewConfiguration.getLongPressTimeout().toLong(),
        onMove = { dx, dy -> controller.move(dx, dy); positionCursor() },
        onTap = { inject(false) }, onLongPress = { inject(true) },
    )
    private val capture = TouchCaptureOverlayView(service, detector)
    private val edgeHandle = EdgeHandleView(service, ::revealControls, ::hideControls)
    private val buttons = (OverlayAction.entries.filter { it != OverlayAction.MODE } + OverlayAction.MODE).associateWith { action ->
        Button(service).apply {
            text = action.label
            contentDescription = if (action == OverlayAction.MODE) "Toggle cursor mode" else action.title
            textSize = 10f
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
            compoundDrawablePadding = dp(2)
            minWidth = 0
            minHeight = 0
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (destroyed || excluded || suspended || injecting) return@setOnClickListener
                if (action == OverlayAction.MODE) setCursorMode(!cursorMode)
                else {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onAction(action)
                }
            }
        }
    }
    private val toggle get() = buttons.getValue(OverlayAction.MODE)
    private var bounds = Rect()
    private var cursorMode = false
    private var injecting = false
    private var destroyed = false
    private var suspended = false
    private var excluded = false
    private var ready = false
    private var injectionToken = 0
    private var displayGeneration = 0

    init {
        capture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
        controller.sensitivity = settings.sensitivity
        settings.observe(preferenceListener)
    }

    fun show() { syncWindows() }

    fun setCursorMode(enabled: Boolean) {
        if (destroyed || excluded || suspended || cursorMode == enabled || (enabled && !controlsExpanded)) return
        cursorMode = enabled
        detector.reset()
        toggle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        // Recreate the input channel instead of reusing a channel with stale touchability flags.
        remove(capture)
        buttons.values.forEach(::remove)
        remove(edgeHandle)
        syncWindows()
    }

    fun setExcluded(value: Boolean) {
        if (excluded == value) return
        excluded = value
        cursorMode = false
        controlsExpanded = !edgeConfig.enabled
        detector.reset()
        syncWindows()
    }

    private fun revealControls() {
        if (destroyed || excluded || suspended || injecting || controlsExpanded) return
        controlsExpanded = true
        remove(edgeHandle)
        syncWindows()
    }

    private fun hideControls() {
        if (!edgeConfig.enabled || !controlsExpanded) return
        controlsExpanded = false
        cursorMode = false
        detector.reset()
        remove(capture)
        buttons.values.forEach(::remove)
        remove(edgeHandle)
        syncWindows()
    }

    fun suspend() {
        suspended = true
        cursorMode = false
        controlsExpanded = !edgeConfig.enabled
        detector.reset()
        syncWindows()
    }

    fun resume() {
        suspended = false
        syncWindows()
    }

    fun onDisplayChanged() {
        displayGeneration++
        detector.reset()
        ready = false
        capture.requestLayout()
        capture.post { updateBounds() }
    }

    private fun syncWindows() {
        if (destroyed || suspended || excluded) {
            attached.keys.toList().forEach(::remove)
            return
        }
        toggle.setText(if (cursorMode) R.string.cursor_on_label else R.string.cursor_off_label)
        toggle.contentDescription = service.getString(if (cursorMode) R.string.cursor_on_description else R.string.cursor_off_description)
        if (!injecting) {
            val captureParams = params(-1, -1, touchable = cursorMode)
            captureParams.title = "TouchCursor.Input"
            if (!put(capture, captureParams)) {
                cursorMode = false
                Toast.makeText(service, "Unable to attach cursor overlay. Re-enable Touch Cursor in Accessibility settings.", Toast.LENGTH_LONG).show()
                return
            }
        } else remove(capture)

        if (cursorMode) {
            if (put(cursor, cursorParams())) positionCursor()
            else {
                cursorMode = false
                toggle.setText(R.string.cursor_off_label)
                put(capture, params(-1, -1, touchable = false).apply { title = "TouchCursor.Input" })
                Toast.makeText(service, "Unable to display the cursor", Toast.LENGTH_SHORT).show()
            }
        } else remove(cursor)

        // Keep the exit control above newly enabled optional controls, even if positions overlap.
        if (!injecting && buttons.any { (action, button) -> action != OverlayAction.MODE &&
                settings.button(action).enabled && !attached.containsKey(button) }) remove(toggle)
        for ((action, button) in buttons) {
            val config = settings.button(action)
            if (injecting || !controlsExpanded || !config.enabled) {
                remove(button)
                continue
            }
            val width = dp(config.widthDp).coerceAtMost(bounds.width().coerceAtLeast(1))
            val height = dp(config.heightDp).coerceAtMost(bounds.height().coerceAtLeast(1))
            val iconSize = dp((minOf(config.widthDp, config.heightDp) * 0.4f).toInt().coerceIn(16, 28))
            val icon = service.getDrawable(action.iconRes)?.mutate()?.apply {
                setBounds(0, 0, iconSize, iconSize)
            }
            button.setCompoundDrawables(null, icon, null, null)
            button.textSize = if (config.heightDp < 48) 9f else 10f
            button.background = GradientDrawable().apply {
                setColor(if (action == OverlayAction.MODE && cursorMode) Color.rgb(0, 115, 100) else Color.rgb(35, 47, 65))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.WHITE)
            }
            val added = put(button, params(width, height).apply {
                title = "TouchCursor.${action.name}"
                alpha = config.opacity
                x = bounds.left + ((bounds.width() - width).coerceAtLeast(0) * config.xNormalized).toInt()
                y = bounds.top + ((bounds.height() - height).coerceAtLeast(0) * config.yNormalized).toInt()
            }.absolute())
            if (!added && action == OverlayAction.MODE) {
                cursorMode = false
                remove(cursor)
                put(capture, params(-1, -1, touchable = false))
            }
        }
        if (edgeConfig.enabled && !injecting && !bounds.isEmpty) {
            edgeHandle.configure(edgeConfig.edge, controlsExpanded)
            val vertical = edgeConfig.edge.isVertical
            val width = dp(if (vertical) edgeConfig.thicknessDp else edgeConfig.lengthDp).coerceAtMost(bounds.width())
            val height = dp(if (vertical) edgeConfig.lengthDp else edgeConfig.thicknessDp).coerceAtMost(bounds.height())
            val added = put(edgeHandle, params(width, height).apply {
                title = "TouchCursor.Edge"
                x = when (edgeConfig.edge) {
                    ScreenEdge.LEFT -> bounds.left
                    ScreenEdge.RIGHT -> bounds.right - width
                    else -> bounds.left + ((bounds.width() - width) * edgeConfig.position).toInt()
                }
                y = when (edgeConfig.edge) {
                    ScreenEdge.TOP -> bounds.top
                    ScreenEdge.BOTTOM -> bounds.bottom - height
                    else -> bounds.top + ((bounds.height() - height) * edgeConfig.position).toInt()
                }
            }.absolute())
            if (!added) {
                edgeConfig = edgeConfig.copy(enabled = false)
                controlsExpanded = true
                Toast.makeText(service, "Edge handle unavailable; showing controls", Toast.LENGTH_SHORT).show()
                syncWindows()
            }
        } else remove(edgeHandle)
    }

    private fun updateBounds() {
        if (!capture.isAttachedToWindow || capture.width == 0 || capture.height == 0) return
        val location = IntArray(2)
        capture.getLocationOnScreen(location)
        val updated = Rect(location[0], location[1], location[0] + capture.width, location[1] + capture.height)
        val changed = bounds != updated
        bounds = updated
        ready = true
        controller.setBounds(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        positionCursor()
        if (changed) syncWindows()
    }

    private fun positionCursor() {
        val layout = attached[cursor] ?: return
        layout.x = controller.x.toInt() - dp(3)
        layout.y = controller.y.toInt() - dp(3)
        put(cursor, layout)
    }

    private fun inject(longPress: Boolean) {
        if (destroyed || suspended || excluded || injecting || !cursorMode || !ready) return
        injecting = true
        val token = ++injectionToken
        val displayToken = displayGeneration
        val x = controller.x
        val y = controller.y
        detector.reset()
        // Removal completes before dispatch, including controls that could cover the arrow tip.
        remove(capture)
        buttons.values.forEach(::remove)
        remove(edgeHandle)
        if (capture.isAttachedToWindow || edgeHandle.isAttachedToWindow || buttons.values.any { it.isAttachedToWindow }) {
            injecting = false
            syncWindows()
            Toast.makeText(service, "Unable to release the touch overlay", Toast.LENGTH_SHORT).show()
            return
        }
        Choreographer.getInstance().postFrameCallback {
            if (destroyed || token != injectionToken) return@postFrameCallback
            if (suspended || excluded || !cursorMode || displayToken != displayGeneration) {
                injecting = false
                syncWindows()
                return@postFrameCallback
            }
            val duration = if (longPress) ViewConfiguration.getLongPressTimeout().toLong() + 150 else 60L
            injector.inject(x, y, duration) { success ->
                if (!destroyed && token == injectionToken) {
                    injecting = false
                    if (success) cursor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    else Toast.makeText(service, "Touch cancelled or unavailable", Toast.LENGTH_SHORT).show()
                    syncWindows()
                }
            }
        }
    }

    private fun cursorParams() = params(dp(32), dp(38), touchable = false).absolute().apply {
        title = "TouchCursor.Cursor"
    }

    // These offsets are physical display coordinates, independent of text layout direction.
    @SuppressLint("RtlHardcoded")
    private fun params(width: Int, height: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            setFitInsetsIgnoringVisibility(true)
        }
    }

    private fun WindowManager.LayoutParams.absolute(): WindowManager.LayoutParams = apply {
        flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun put(view: View, layout: WindowManager.LayoutParams): Boolean = try {
        if (attached.containsKey(view)) wm.updateViewLayout(view, layout) else wm.addView(view, layout)
        attached[view] = layout
        true
    } catch (_: RuntimeException) {
        remove(view)
        false
    }

    private fun remove(view: View) {
        if (attached.containsKey(view) || view.isAttachedToWindow) {
            try { wm.removeViewImmediate(view) } catch (_: RuntimeException) { /* Already detached by system. */ }
            if (!view.isAttachedToWindow) attached.remove(view)
        }
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density + 0.5f).toInt()

    fun close() {
        destroyed = true
        injectionToken++
        settings.stopObserving(preferenceListener)
        handler.removeCallbacks(refresh)
        injector.close()
        detector.reset()
        attached.keys.toList().forEach(::remove)
    }
}
