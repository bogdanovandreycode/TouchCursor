package com.example.touchcursor.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale
import com.example.touchcursor.R

class AppExclusionsDialog(private val activity: Activity, private val settings: SettingsRepository,
                          private val onSaved: () -> Unit) {
    private data class App(val packageName: String, val label: String)

    fun show() {
        val selected = settings.excludedPackages.toMutableSet().apply { remove(activity.packageName) }
        var apps = emptyList<App>()
        var visible = emptyList<App>()
        var dismissed = false
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        form.addView(TextView(activity).apply {
            setText(R.string.excluded_apps_description)
        })
        val search = EditText(activity).apply { hint = "Search apps or package names"; isSingleLine = true }
        form.addView(search)
        val loading = TextView(activity).apply { setText(R.string.loading_apps) }
        form.addView(loading)
        val adapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_multiple_choice)
        val list = ListView(activity).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE; this.adapter = adapter }
        form.addView(list, LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.4f).toInt()))
        fun filter(query: String) {
            visible = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
            list.clearChoices()
            adapter.clear()
            adapter.addAll(visible.map { "${it.label}\n${it.packageName}" })
            visible.forEachIndexed { index, app -> list.setItemChecked(index, app.packageName in selected) }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val pkg = visible[position].packageName
            if (list.isItemChecked(position)) selected.add(pkg) else selected.remove(pkg)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val dialog = AlertDialog.Builder(activity).setTitle("App exclusions").setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                settings.excludedPackages = selected
                onSaved()
                Toast.makeText(activity, "App exclusions saved", Toast.LENGTH_SHORT).show()
            }.create()
        dialog.setOnDismissListener { dismissed = true }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        Thread({
            val result = runCatching {
                val pm = activity.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                val launchers = pm.queryIntentActivities(intent, 0)
                val entries = launchers.mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == activity.packageName) null else App(pkg, info.loadLabel(pm).toString())
                }.distinctBy { it.packageName }
                (entries + selected.filter { pkg -> entries.none { it.packageName == pkg } }.map { App(it, it) })
                    .sortedBy { it.label.lowercase(Locale.getDefault()) }
            }
            activity.runOnUiThread {
                if (dismissed || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                apps = result.getOrDefault(emptyList())
                loading.text = if (result.isSuccess) "Select apps to exclude" else "Unable to load the app list. Try again."
                filter(search.text.toString())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = result.isSuccess
            }
        }, "TouchCursor-app-list").start()
    }
}
