package com.eymen.os

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional protection (enabled by the user in Accessibility settings): when the TV shows the
 * eski ana ekran home screen — after start-up or after the Home key — EYMEN OS is opened instead.
 * Only the other launcher's home screen is watched; its other screens and apps keep working.
 */
class EnforcerService : AccessibilityService() {
    private var homes: Set<String> = emptySet()
    private var homesAt = 0L
    private var lastLaunch = 0L

    private fun otherHomeActivities(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - homesAt < 60000L && homes.isNotEmpty()) return homes
        homesAt = now
        homes = try {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName != "com.android.settings" }
                .map { it.activityInfo.packageName + "/" + it.activityInfo.name }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        return homes
    }

    private fun allowed(): Boolean {
        val prefs = getSharedPreferences(LauncherState.PREFS, Context.MODE_PRIVATE)
        return System.currentTimeMillis() < prefs.getLong("allowOtherUntil", 0L)
    }

    private fun openLauncher() {
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 1500L) return
        lastLaunch = now
        try {
            val i = Intent(this, HomeActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || allowed()) return
        val cls = event.className?.toString() ?: ""
        val full = if (cls.startsWith(".")) pkg + cls else cls
        if (otherHomeActivities().contains("$pkg/$full")) openLauncher()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || allowed()) return false
        if (event.action == KeyEvent.ACTION_UP) openLauncher()
        return true
    }

    override fun onInterrupt() {}
}
