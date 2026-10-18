@file:Suppress("DEPRECATION")

package com.example.touchcursor.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
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
class AppExclusionsDialogTest {
    private val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = lifecycle.get()
    private val repository = SettingsRepository(activity)
    private var saved = 0
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private inline fun <reified T : View> view(dialog: AlertDialog) = descendants(dialog.window!!.decorView).filterIsInstance<T>().single()
    private fun waitForAppList() {
        // The production worker posts to the paused main looper. Join it before processing its result.
        Thread.getAllStackTraces().keys.filter { it.name == "TouchCursor-app-list" }.forEach {
            it.join(5000); assertFalse("App-list worker did not finish", it.isAlive)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun show(): AlertDialog {
        AppExclusionsDialog(activity, repository) { saved++ }.show()
        waitForAppList()
        return ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun select(list: ListView, position: Int) {
        list.performItemClick(list.getChildAt(position), position, list.adapter.getItemId(position))
    }
    private fun apps(vararg names: Pair<String, String>) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowOf(activity.packageManager).addResolveInfoForIntent(intent, names.map { (pkg, label) ->
            ResolveInfo().apply {
                nonLocalizedLabel = label
                activityInfo = ActivityInfo().apply {
                    packageName = pkg; name = "$pkg.MainActivity"
                    applicationInfo = ApplicationInfo().apply { packageName = pkg }
                }
            }
        })
    }

    @Before fun setup() { repository.reset() }
    @After fun cleanup() {
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        waitForAppList(); lifecycle.pause().stop().destroy()
    }

    @Test fun listDeduplicatesLaunchersOmitsOwnAppAndRetainsMissingSelections() {
        apps("game.app" to "Game", "game.app" to "Game shortcut", activity.packageName to "Touch Cursor")
        repository.excludedPackages = setOf(activity.packageName, "missing.app")
        val dialog = show(); val list = view<ListView>(dialog)
        assertEquals(2, list.count)
        assertEquals("Game\ngame.app", list.adapter.getItem(0))
        assertEquals("missing.app\nmissing.app", list.adapter.getItem(1))
        assertTrue(list.isItemChecked(1))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf("missing.app"), repository.excludedPackages)
        assertEquals(1, saved)
    }

    @Test fun searchByLabelOrPackagePreservesSelectionAcrossFilters() {
        apps("game.app" to "Puzzle", "reader.app" to "Books")
        val dialog = show(); val search = view<EditText>(dialog); val list = view<ListView>(dialog)
        search.setText("PUZZLE"); assertEquals(1, list.count)
        select(list, 0)
        search.setText("reader.app"); assertEquals(1, list.count); assertFalse(list.isItemChecked(0))
        search.setText("game.app"); assertTrue(list.isItemChecked(0))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf("game.app"), repository.excludedPackages)
    }

    @Test fun cancelDoesNotPersistSelectionOrInvokeSavedCallback() {
        apps("game.app" to "Game")
        val dialog = show(); select(view(dialog), 0)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(repository.excludedPackages.isEmpty()); assertEquals(0, saved)
    }

    @Test fun finishingActivityIgnoresPendingAppListResult() {
        apps("game.app" to "Game")
        AppExclusionsDialog(activity, repository) { saved++ }.show()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        activity.finish(); dialog.dismiss(); waitForAppList()
        assertFalse(dialog.isShowing)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertEquals(0, saved)
    }
}
