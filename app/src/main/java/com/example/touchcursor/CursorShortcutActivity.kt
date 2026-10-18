package com.example.touchcursor

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.example.touchcursor.service.CursorAccessibilityService

/** Optional Android 11+ shortcut target; never changes the service's accessibility permission. */
class CursorShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CursorAccessibilityService.toggleFromShortcut()) {
            Toast.makeText(this, R.string.accessibility_shortcut_service_required, Toast.LENGTH_LONG).show()
        }
        // Theme.NoDisplay must finish before onResume; no window may cover the target app.
        finish()
    }
}
