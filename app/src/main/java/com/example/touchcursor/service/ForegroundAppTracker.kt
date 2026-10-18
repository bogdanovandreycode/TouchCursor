package com.example.touchcursor.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo

/** Only application-window package metadata is needed; IME and overlay focus must not unpause a game. */
class ForegroundAppTracker(private val service: AccessibilityService) {
    private var currentPackage: String? = null
    private var currentWindowId = -1

    fun refresh(eventPackage: String? = null, eventWindowId: Int = -1): String? {
        try {
            val windows = service.windows
            try {
                val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val app = apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused }
                    ?: apps.maxByOrNull { it.layer }
                if (app != null) {
                    val root = app.root
                    val fromRoot = try { root?.packageName?.toString() } finally {
                        @Suppress("DEPRECATION")
                        root?.recycle()
                    }
                    val found = fromRoot ?: eventPackage?.takeIf { app.id == eventWindowId }
                        ?: currentPackage?.takeIf { app.id == currentWindowId }
                    if (found != null) {
                        currentPackage = found
                        currentWindowId = app.id
                    } else if (app.id != currentWindowId) {
                        currentPackage = null
                        currentWindowId = app.id
                    }
                }
            } finally {
                @Suppress("DEPRECATION")
                windows.forEach { it.recycle() }
            }
        } catch (_: RuntimeException) { /* Retain the last application through transient window changes. */ }
        return currentPackage
    }
}
