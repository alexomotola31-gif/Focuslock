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
            .putLong("startWall", System.currentTimeMillis())
            .putLong("planned", durationMs)
            .putInt("boot", bootCount(c))
            .putBoolean("active", true)
            .apply()
    }

    fun plannedMs(c: Context): Long = prefs(c).getLong("planned", 0)

    /** Ends the session and logs it. early = true when ended with the emergency code. */
    fun end(c: Context, early: Boolean = false) {
        val p = prefs(c)
        val startWall = p.getLong("startWall", 0)
        val planned = p.getLong("planned", 0)
        if (p.getBoolean("active", false) && startWall > 0 && planned > 0) {
            val spent = if (early) (System.currentTimeMillis() - startWall).coerceIn(0, planned) else planned
            HistoryStore.add(c, FocusRecord(startWall, spent, planned, !early))
        }
        p.edit().clear().apply()
        Vault.wipe(c)
    }

    /** Remaining time in ms. Uses elapsedRealtime (immune to clock changes) unless the phone rebooted. */
    fun remainingMs(c: Context, s: Session): Long =
        if (s.bootCount == bootCount(c)) s.endElapsed - SystemClock.elapsedRealtime()
        else s.endWall - System.currentTimeMillis()

    /** Returns the active session, or null (and logs/clears it) if none or expired. */
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
