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
import android.view.WindowInsets
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
        cursor.appearance = settings.cursorAppearance()
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
    private var systemShortcutBounds: Rect? = null
    private val capture = TouchCaptureOverlayView(service, detector,
        { x, y -> systemShortcutBounds?.contains(x.toInt(), y.toInt()) == true },
        { setCursorMode(false) },
    )
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
    private var injectionToken = 0
    private var displayGeneration = 0
    private var preparationTimeout: Runnable? = null

    init {
        capture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
        controller.sensitivity = settings.sensitivity
        cursor.appearance = settings.cursorAppearance()
        settings.observe(preferenceListener)
    }

    fun show() { syncWindows() }

    fun setSystemShortcutBounds(bounds: Rect?) { systemShortcutBounds = bounds?.let(::Rect) }

    /** System shortcut also works while the edge panel is collapsed or MODE is hidden. */
    fun toggleCursorMode() {
        if (destroyed || excluded || suspended || (injecting && !cursorMode)) return
        if (!cursorMode) controlsExpanded = true
        setCursorMode(!cursorMode)
    }

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
        if (suspended) return
        suspended = true
        cursorMode = false
        controlsExpanded = !edgeConfig.enabled
        detector.reset()
        syncWindows()
    }

    fun resume() {
        if (!suspended) return
        suspended = false
        syncWindows()
        // Reattachment at unchanged bounds does not trigger OnLayoutChangeListener.
        capture.post { updateBounds() }
    }

    /** Repair detached windows after an OEM removes an overlay or an addView fails. */
    fun recoverWindows() {
        if (destroyed || suspended || excluded || injecting) return
        val missing = !capture.isAttachedToWindow || (cursorMode && !cursor.isAttachedToWindow) ||
            (edgeConfig.enabled && !edgeHandle.isAttachedToWindow) ||
            (controlsExpanded && buttons.any { (action, view) -> settings.button(action).enabled && !view.isAttachedToWindow })
        if (missing) syncWindows()
    }

    fun onDisplayChanged() {
        displayGeneration++
        detector.reset()
        syncWindows()
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
            val captureParams = captureParams(cursorMode)
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
                put(capture, captureParams(false))
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
                put(capture, captureParams(false))
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
        controller.setBounds(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        positionCursor()
        if (changed) syncWindows()
    }

    private fun positionCursor() {
        val layout = attached[cursor] ?: return
        layout.x = (controller.x - cursor.hotspotX).toInt()
        layout.y = (controller.y - cursor.hotspotY).toInt()
        put(cursor, layout)
    }

    private fun inject(longPress: Boolean) {
        if (destroyed || suspended || excluded || injecting || !cursorMode) return
        // Read the current attached surface rather than a cached readiness flag. A reattached
        // View can keep identical layout bounds and never send an OnLayoutChange callback.
        if (!capture.isAttachedToWindow || capture.width <= 0 || capture.height <= 0) {
            setCursorMode(false)
            return
        }
        updateBounds()
        injecting = true
        val token = ++injectionToken
        val displayToken = displayGeneration
        val x = controller.x
        val y = controller.y
        val removedIds = (listOf(capture, edgeHandle) + buttons.values)
            .filter { it.isAttachedToWindow }.mapNotNull { view ->
                val node = view.createAccessibilityNodeInfo()
                try { node?.windowId?.takeIf { it >= 0 } } finally {
                    @Suppress("DEPRECATION")
                    node?.recycle()
                }
            }.toSet()
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
        preparationTimeout = Runnable {
            if (!destroyed && token == injectionToken && injecting) {
                injectionToken++
                injecting = false
                cursorMode = false
                syncWindows()
                Toast.makeText(service, "Touch overlay did not release; returned to normal touch", Toast.LENGTH_SHORT).show()
            }
        }.also { handler.postDelayed(it, 1000) }
        afterOverlayRemoval(token, removedIds) {
            preparationTimeout?.let(handler::removeCallbacks)
            preparationTimeout = null
            if (destroyed || token != injectionToken) return@afterOverlayRemoval
            if (suspended || excluded || !cursorMode || displayToken != displayGeneration) {
                injecting = false
                syncWindows()
                return@afterOverlayRemoval
            }
            val duration = if (longPress) ViewConfiguration.getLongPressTimeout().toLong() + 150 else 60L
            injector.inject(x, y, duration) { success ->
                if (!destroyed && token == injectionToken) {
                    injecting = false
                    if (success) cursor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    else {
                        // Failed input must release the fullscreen touchpad, not trap the user.
                        cursorMode = false
                        detector.reset()
                        Toast.makeText(service, "Touch unavailable; returned to normal touch", Toast.LENGTH_SHORT).show()
                    }
                    syncWindows()
                }
            }
        }
    }

    private fun afterOverlayRemoval(token: Int, removedIds: Set<Int>, frames: Int = 2, action: () -> Unit) {
        Choreographer.getInstance().postFrameCallback {
            if (destroyed || token != injectionToken) return@postFrameCallback
            // A first frame callback runs before traversal/SF input transactions are committed.
            // Also wait for Android's accessibility window snapshot to stop reporting the
            // removed input windows. The preparation watchdog releases normal touch on timeout.
            val removed = if (frames > 1) false else try {
                val windows = service.windows
                try { windows.none { it.id in removedIds } } finally {
                    @Suppress("DEPRECATION")
                    windows.forEach { it.recycle() }
                }
            } catch (_: RuntimeException) { false }
            if (removed) action() else afterOverlayRemoval(token, removedIds, (frames - 1).coerceAtLeast(1), action)
        }
    }

    private fun cursorParams() = params(
        (32f * cursor.appearance.sizeDp / 38f * service.resources.displayMetrics.density + 2 * cursor.paddingPx + 0.5f).toInt(),
        dp(cursor.appearance.sizeDp) + (2 * cursor.paddingPx + 0.5f).toInt(), touchable = false,
    ).absolute().apply {
        title = "TouchCursor.Cursor"
        alpha = cursor.appearance.opacity
    }

    private fun captureParams(touchable: Boolean): WindowManager.LayoutParams {
        val layout = if (Build.VERSION.SDK_INT >= 30) {
            // Accessibility windows can ignore fitInsetsTypes on newer Android. Never let
            // the input surface cover navigation/status bars or the system shortcut there.
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val area = metrics.bounds
            params((area.width() - insets.left - insets.right).coerceAtLeast(1),
                (area.height() - insets.top - insets.bottom).coerceAtLeast(1), touchable).absolute().apply {
                x = area.left + insets.left
                y = area.top + insets.top
            }
        } else params(-1, -1, touchable)
        return layout.apply { title = "TouchCursor.Input" }
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
        // A window is registered before its first traversal marks the View as attached.
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
        preparationTimeout?.let(handler::removeCallbacks)
        injector.close()
        detector.reset()
        attached.keys.toList().forEach(::remove)
    }
}
