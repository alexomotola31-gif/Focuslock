package com.focuslock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

data class Session(
    val lockedApps: Set<String>,
    val endElapsed: Long,   // SystemClock.elapsedRealtime() at end (valid until reboot)
    val endWall: Long,      // wall-clock end, fallback after a reboot
    val bootCount: Int      // detects reboots
)

object SessionStore {
    private const val PREFS = "session"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bootCount(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun start(c: Context, apps: Set<String>, durationMs: Long) {
        prefs(c).edit()
            .putStringSet("apps", apps)
            .putLong("endElapsed", SystemClock.elapsedRealtime() + durationMs)
            .putLong("endWall", System.currentTimeMillis() + durationMs)
            .putInt("boot", bootCount(c))
            .putBoolean("active", true)
            .apply()
    }

    fun end(c: Context) { prefs(c).edit().clear().apply(); Vault.wipe(c) }

    /** Remaining time in ms. Uses elapsedRealtime (immune to clock changes) unless the phone rebooted. */
    fun remainingMs(c: Context, s: Session): Long =
        if (s.bootCount == bootCount(c)) s.endElapsed - SystemClock.elapsedRealtime()
        else s.endWall - System.currentTimeMillis()

    /** Returns the active session, or null (and clears it) if none or expired. */
    fun active(c: Context): Session? {
        val p = prefs(c)
        if (!p.getBoolean("active", false)) return null
        val s = Session(
            p.getStringSet("apps", emptySet()) ?: emptySet(),
            p.getLong("endElapsed", 0), p.getLong("endWall", 0), p.getInt("boot", 0)
        )
        if (remainingMs(c, s) <= 0) { end(c); return null }
        return s
    }
}
