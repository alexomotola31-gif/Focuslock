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

    fun start(c: Context, apps: Set<String>, durationMs: Long, phases: String = "", lockBreaks: Boolean = true) {
        prefs(c).edit()
            .putStringSet("apps", apps)
            .putLong("endElapsed", SystemClock.elapsedRealtime() + durationMs)
            .putLong("endWall", System.currentTimeMillis() + durationMs)
            .putLong("startWall", System.currentTimeMillis())
            .putLong("planned", durationMs)
            .putString("phases", phases)
            .putBoolean("lockBreaks", lockBreaks)
            .putInt("boot", bootCount(c))
            .putBoolean("active", true)
            .apply()
        Notifier.scheduleAll(c)
        Notifier.sessionStarted(c)
    }

    fun plannedMs(c: Context): Long = prefs(c).getLong("planned", 0)
    fun startWall(c: Context): Long = prefs(c).getLong("startWall", 0)
    fun locksBreaks(c: Context): Boolean = prefs(c).getBoolean("lockBreaks", true)
    fun phases(c: Context): List<Phase> = Pomodoro.decode(prefs(c).getString("phases", "") ?: "")

    /** The stored session without checking expiry. */
    fun current(c: Context): Session? {
        val p = prefs(c)
        if (!p.getBoolean("active", false)) return null
        return Session(
            p.getStringSet("apps", emptySet()) ?: emptySet(),
            p.getLong("endElapsed", 0), p.getLong("endWall", 0), p.getInt("boot", 0)
        )
    }

    /** Remaining time in ms. Uses elapsedRealtime (immune to clock changes) unless the phone rebooted. */
    fun remainingMs(c: Context, s: Session): Long =
        if (s.bootCount == bootCount(c)) s.endElapsed - SystemClock.elapsedRealtime()
        else s.endWall - System.currentTimeMillis()

    /** Returns the active session, or null (and logs/clears it) if none or expired. */
    fun active(c: Context): Session? {
        val s = current(c) ?: return null
        if (remainingMs(c, s) <= 0) { end(c); return null }
        return s
    }

    /** Which Pomodoro phase we are in, or null for a single session. */
    fun phase(c: Context): PhaseInfo? {
        val ph = phases(c)
        if (ph.isEmpty()) return null
        val s = current(c) ?: return null
        val planned = plannedMs(c)
        val elapsed = (planned - remainingMs(c, s)).coerceIn(0, planned)
        val total = ph.count { it.kind == 'F' }
        var acc = 0L
        for ((i, p) in ph.withIndex()) {
            if (elapsed < acc + p.ms || i == ph.lastIndex) {
                return PhaseInfo(
                    p.isBreak, p.kind == 'L',
                    (acc + p.ms - elapsed).coerceAtLeast(0), p.ms,
                    ph.take(i + 1).count { it.kind == 'F' }, total, i, i == ph.lastIndex
                )
            }
            acc += p.ms
        }
        return null
    }

    /** True only during a Pomodoro break when the user chose not to lock breaks. */
    fun breakUnlocked(c: Context): Boolean {
        if (prefs(c).getBoolean("lockBreaks", true)) return false
        return phase(c)?.isBreak == true
    }

    private fun rewrite(c: Context, newPhases: List<Phase>) {
        val p = prefs(c)
        val old = p.getLong("planned", 0)
        val np = newPhases.sumOf { it.ms }
        val delta = old - np
        p.edit()
            .putString("phases", Pomodoro.encode(newPhases))
            .putLong("planned", np)
            .putLong("endElapsed", p.getLong("endElapsed", 0) - delta)
            .putLong("endWall", p.getLong("endWall", 0) - delta)
            .apply()
        Notifier.scheduleAll(c)
    }

    fun skipBreak(c: Context) {
        val i = phase(c) ?: return
        if (!i.isBreak) return
        val ph = phases(c).toMutableList()
        ph[i.index] = Phase(ph[i.index].kind, i.phaseTotalMs - i.phaseRemainingMs)
        rewrite(c, ph)
    }

    /** Ends the cycle right now (only offered during a break). */
    fun finishNow(c: Context) {
        val i = phase(c) ?: return
        if (!i.isBreak) return
        val ph = phases(c).take(i.index + 1).toMutableList()
        ph[i.index] = Phase(ph[i.index].kind, i.phaseTotalMs - i.phaseRemainingMs)
        rewrite(c, ph)
    }

    /** Stop after the focus period that is running now. */
    fun finishAfterCurrent(c: Context) {
        val i = phase(c) ?: return
        if (i.isBreak) return
        rewrite(c, phases(c).take(i.index + 1))
    }

    private fun focusDone(ph: List<Phase>, elapsed: Long): Pair<Long, Int> {
        var acc = 0L
        var ms = 0L
        var cycles = 0
        for (p in ph) {
            val inPhase = (elapsed - acc).coerceIn(0, p.ms)
            if (p.kind == 'F') { ms += inPhase; if (inPhase >= p.ms) cycles++ }
            acc += p.ms
        }
        return ms to cycles
    }

    /** Ends the session and logs it. early = true when ended with the emergency code. */
    fun end(c: Context, early: Boolean = false) {
        val p = prefs(c)
        val startWall = p.getLong("startWall", 0)
        val planned = p.getLong("planned", 0)
        if (p.getBoolean("active", false) && startWall > 0 && planned > 0) {
            val elapsed = if (early) (System.currentTimeMillis() - startWall).coerceIn(0, planned) else planned
            val ph = phases(c)
            val (spent, cycles) = if (ph.isEmpty()) (elapsed to 0) else focusDone(ph, elapsed)
            HistoryStore.add(c, FocusRecord(startWall, spent, planned, !early, cycles))
            Notifier.afterSession(c, spent, cycles, !early)
        }
        Notifier.cancelAll(c)
        p.edit().clear().apply()
        Vault.wipe(c)
    }
}
