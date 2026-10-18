package com.example.touchcursor

import android.app.AlertDialog
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.example.touchcursor.model.CursorShape
import com.example.touchcursor.model.OverlayAction
import com.example.touchcursor.model.ScreenEdge
import com.example.touchcursor.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    private val lifecycle = Robolectric.buildActivity(MainActivity::class.java)
    private val activity = lifecycle.get()
    private val settings = SettingsRepository(activity)
    private val root get() = activity.window.decorView
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private inline fun <reified T : View> views(view: View = root) = descendants(view).filterIsInstance<T>()
    private fun click(label: String) {
        views<Button>().single { it.text.toString() == label }.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun dialog() = ShadowAlertDialog.getLatestAlertDialog()
    private fun save(dialog: AlertDialog = dialog()) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun cancel(dialog: AlertDialog = dialog()) {
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun progress(view: View, name: String, value: Int) {
        val seekbar = views<SeekBar>(view).single { it.contentDescription == name }
        seekbar.progress = value
        shadowOf(seekbar).onSeekBarChangeListener.onProgressChanged(seekbar, value, true)
    }

    @Before fun setup() { settings.reset(); lifecycle.setup() }
    @After fun cleanup() {
        dialog()?.takeIf { it.isShowing }?.dismiss()
        lifecycle.pause().stop().destroy()
    }

    @Test fun opensAndroidAccessibilitySettings() {
        click("Open Accessibility settings")
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test fun cursorSavePersistsAllFields() {
        click("Configure cursor appearance")
        val dialog = dialog(); val form = dialog.window!!.decorView
        views<Spinner>(form).single().setSelection(CursorShape.CROSSHAIR.ordinal)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        progress(form, "Cursor size", 64 - 16)
        progress(form, "Cursor opacity", 65 - 10)
        progress(form, "Outline width (0 disables outline)", 4)
        views<EditText>(form)[0].setText("#12abEF")
        views<EditText>(form)[1].setText("#123456")
        save(dialog)
        val actual = SettingsRepository(activity).cursorAppearance()
        assertEquals(CursorShape.CROSSHAIR, actual.shape)
        assertEquals(64, actual.sizeDp); assertEquals(.65f, actual.opacity, .001f)
        assertEquals(0xFF12ABEF.toInt(), actual.color)
        assertEquals(0xFF123456.toInt(), actual.outlineColor); assertEquals(4, actual.outlineWidthDp)
        assertFalse(dialog.isShowing)
    }

    @Test fun invalidColorKeepsDialogOpenAndDoesNotSave() {
        val original = settings.cursorAppearance()
        click("Configure cursor appearance")
        val dialog = dialog(); val fields = views<EditText>(dialog.window!!.decorView)
        fields[0].setText("#bad-input"); save(dialog)
        assertTrue(dialog.isShowing); assertNotNull(fields[0].error)
        assertEquals(original, settings.cursorAppearance())
        fields[0].setText("#112233"); fields[1].setText("123456"); save(dialog)
        assertTrue(dialog.isShowing); assertNotNull(fields[1].error)
        assertEquals(original, settings.cursorAppearance())
    }

    @Test fun cancelCursorDiscardsPreviewChanges() {
        val original = settings.cursorAppearance()
        click("Configure cursor appearance")
        val dialog = dialog(); val form = dialog.window!!.decorView
        progress(form, "Cursor size", 70)
        views<EditText>(form)[0].setText("#FF0000")
        cancel(dialog)
        assertEquals(original, settings.cursorAppearance())
    }

    @Test fun buttonConfigurationSavesOnlyTheChosenButton() {
        val untouched = settings.button(OverlayAction.COPY)
        click("Configure ${OverlayAction.MODE.title}")
        val dialog = dialog(); val form = dialog.window!!.decorView
        views<CheckBox>(form).single().isChecked = false
        progress(form, "Button opacity", 20)
        progress(form, "Horizontal position", 35)
        progress(form, "Vertical position", 60)
        progress(form, "Width", 80)
        progress(form, "Height", 40)
        save(dialog)
        val actual = settings.button(OverlayAction.MODE)
        assertFalse(actual.enabled); assertEquals(.5f, actual.opacity, .001f)
        assertEquals(.35f, actual.xNormalized, .001f); assertEquals(.6f, actual.yNormalized, .001f)
        assertEquals(120, actual.widthDp); assertEquals(80, actual.heightDp)
        assertEquals(untouched, settings.button(OverlayAction.COPY))
    }

    @Test fun edgeSwipeConfigurationSavesAndCancelPreservesIt() {
        click("Configure edge swipe")
        val dialog = dialog(); val form = dialog.window!!.decorView
        views<CheckBox>(form).single().isChecked = true
        views<Spinner>(form).single().setSelection(ScreenEdge.BOTTOM.ordinal)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        progress(form, "Trigger position", 30)
        progress(form, "Trigger length", 100)
        progress(form, "Trigger thickness", 12)
        save(dialog)
        val saved = settings.edgeReveal()
        assertTrue(saved.enabled); assertEquals(ScreenEdge.BOTTOM, saved.edge)
        assertEquals(.3f, saved.position, .001f); assertEquals(148, saved.lengthDp); assertEquals(20, saved.thicknessDp)
        click("Configure edge swipe")
        views<CheckBox>(dialog().window!!.decorView).single().isChecked = false
        cancel()
        assertEquals(saved, settings.edgeReveal())
    }

    @Test fun resetRequiresConfirmationAndRestoresDefaults() {
        settings.sensitivity = 3f; settings.excludedPackages = setOf("game.app")
        click("Reset settings to defaults"); cancel()
        assertEquals(3f, settings.sensitivity, .001f)
        click("Reset settings to defaults"); save()
        assertEquals(1.5f, settings.sensitivity, .001f); assertTrue(settings.excludedPackages.isEmpty())
    }

    @Test fun sensitivityUpdatesImmediatelyOnlyForUserChanges() {
        progress(root, "Sensitivity", 175)
        assertEquals(2f, settings.sensitivity, .001f)
        views<SeekBar>().single { it.contentDescription == "Sensitivity" }.progress = 300
        assertEquals(2f, settings.sensitivity, .001f)
    }

    @Test fun enabledButDisconnectedServiceHasDistinctStatus() {
        Settings.Secure.putString(activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${activity.packageName}/.service.CursorAccessibilityService")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1100))
        assertTrue(views<TextView>().any { it.text.toString().contains("waiting for Android to connect") })
    }
}
