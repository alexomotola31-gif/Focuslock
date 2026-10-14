package com.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LockService : AccessibilityService() {
    // Blocked during a session so the user can't switch off this service, the admin, or uninstall the app
    private val guarded = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    companion object {
        /** True only while Android really has this service connected (Settings can show ON when it is not). */
        var connected by mutableStateOf(false)
    }

    override fun onServiceConnected() { connected = true; enforce(rootInActiveWindow?.packageName?.toString()) }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; return super.onUnbind(intent) }
    override fun onDestroy() { connected = false; super.onDestroy() }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        enforce(e.packageName?.toString())
    }

    private fun enforce(pkg: String?) {
        if (pkg == null || pkg == packageName) return // never block ourselves
        val session = SessionStore.active(this)
        val bySession = session != null &&
            ((pkg in session.lockedApps && !SessionStore.breakUnlocked(this)) || pkg in guarded)
        if (bySession || Schedules.isBlocked(this, pkg)) {
            startActivity(
                Intent(this, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
    override fun onInterrupt() {}
}
