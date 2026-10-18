package com.alex.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LockService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var lastLaunch = 0L
    private var lastFg: String? = null
    private var lastNudge = 0L

    companion object {
        /** Blocked during a timer so the user can't switch off this service, the admin, or uninstall the app. */
        val GUARDED = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.transsion.packageinstaller",
            "com.oplus.packageinstaller",
            "com.coloros.packageinstaller",
            "com.miui.securitycenter",
            "com.transsion.phonemaster"
        )
        /** True only while Android really has this service connected (Settings can show ON when it is not). */
        var connected by mutableStateOf(false)
        @Volatile private var instance: LockService? = null
        @Volatile private var lastGuarded: String? = null
        /** Called when the lock screen is removed (e.g. swiped from recents) without being unlocked. */
        fun nudge() { instance?.nudgeSoon() }
    }

    override fun onServiceConnected() {
        instance = this; connected = true
        startWatch()
        enforce(rootInActiveWindow?.packageName?.toString())
    }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; instance = null; stopWatch(); return super.onUnbind(intent) }
    override fun onDestroy() { connected = false; instance = null; stopWatch(); super.onDestroy() }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val p = e.packageName?.toString()
                if (p != null && p != packageName) lastFg = p
                enforce(p)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCheck < 300) return
                lastCheck = now
                enforce(e.packageName?.toString())
            }
        }
    }

    private fun shouldLock(pkg: String): Boolean {
        val session = SessionStore.active(this)
        val extras = ExtraTimers.active(this)
        val byMain = session != null &&
            ((pkg in session.lockedApps && !SessionStore.breakUnlocked(this)) || pkg in GUARDED)
        val byExtra = extras.any { pkg in it.apps } || (pkg in GUARDED && extras.isNotEmpty())
        // During a scheduled lock, Settings and the uninstallers are blocked too
        val bySchedule = Schedules.isBlocked(this, pkg) ||
            (pkg in GUARDED && Schedules.activeWindows(this).isNotEmpty())
        return byMain || byExtra || bySchedule
    }

    private fun enforce(pkg: String?) {
        if (pkg == null || pkg == packageName) return // never block ourselves
        if (shouldLock(pkg)) { lastGuarded = pkg; showLock(pkg) }
    }

    private fun showLock(pkg: String) {
        if (LockActivity.visible && LockActivity.shownPkg == pkg) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 500) return
        lastLaunch = now
        startActivity(
            Intent(this, LockActivity::class.java)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun nudgeSoon() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNudge < 5000) return
        lastNudge = now
        handler.postDelayed({
            val p = lastGuarded ?: return@postDelayed
            // Only re-lock if that app is really still the one on screen
            if (lastFg == p && shouldLock(p)) showLock(p)
        }, 400)
    }

    // Every 2 seconds, re-check the app on screen while the phone is in use, so a timer or schedule
    // that starts while an app is already open locks it straight away.
    private val watch = object : Runnable {
        override fun run() {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val p = lastFg
            if (p != null && pm.isInteractive) enforce(p)
            handler.postDelayed(this, 2000)
        }
    }
    private fun startWatch() { handler.removeCallbacks(watch); handler.postDelayed(watch, 2000) }
    private fun stopWatch() { handler.removeCallbacks(watch) }

    override fun onInterrupt() {}
}
