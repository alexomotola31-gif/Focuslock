package com.alex.focuslock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** A timer that runs alongside the main session, with its own apps, end time and emergency code. */
data class ExtraTimer(
    val id: String,
    val apps: Set<String>,
    val endElapsed: Long,
    val endWall: Long,
    val boot: Int,
    val startWall: Long,
    val planned: Long
)

object ExtraTimers {
    private fun prefs(c: Context) = c.getSharedPreferences("extra_timers", Context.MODE_PRIVATE)
    private fun bootCount(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun load(c: Context): List<ExtraTimer> {
        val raw = prefs(c).getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val a = o.getJSONArray("apps")
                ExtraTimer(
                    o.getString("id"),
                    (0 until a.length()).map { a.getString(it) }.toSet(),
                    o.getLong("endElapsed"), o.getLong("endWall"), o.getInt("boot"),
                    o.getLong("startWall"), o.getLong("planned")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(c: Context, list: List<ExtraTimer>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().put("id", t.id).put("apps", JSONArray(t.apps.toList()))
                    .put("endElapsed", t.endElapsed).put("endWall", t.endWall).put("boot", t.boot)
                    .put("startWall", t.startWall).put("planned", t.planned)
            )
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun newId(): String = System.currentTimeMillis().toString()

    @Synchronized
    fun start(c: Context, id: String, apps: Set<String>, durationMs: Long) {
        val now = System.currentTimeMillis()
        val t = ExtraTimer(id, apps, SystemClock.elapsedRealtime() + durationMs, now + durationMs, bootCount(c), now, durationMs)
        save(c, load(c) + t)
    }

    fun remainingMs(c: Context, t: ExtraTimer): Long =
        if (t.boot == bootCount(c)) t.endElapsed - SystemClock.elapsedRealtime() else t.endWall - System.currentTimeMillis()

    /** Timers still running. Finished ones are logged and removed. */
    @Synchronized
    fun active(c: Context): List<ExtraTimer> {
        val all = load(c)
        if (all.isEmpty()) return all
        val live = all.filter { remainingMs(c, it) > 0 }
        if (live.size != all.size) {
            all.filter { remainingMs(c, it) <= 0 }.forEach { log(c, it, early = false) }
            save(c, live)
        }
        return live
    }

    /** Ends one timer. early = true when ended with its emergency code. */
    @Synchronized
    fun end(c: Context, id: String, early: Boolean) {
        val all = load(c)
        val t = all.firstOrNull { it.id == id } ?: return
        log(c, t, early)
        save(c, all.filter { it.id != id })
    }

    private fun log(c: Context, t: ExtraTimer, early: Boolean) {
        val spent = if (early) (System.currentTimeMillis() - t.startWall).coerceIn(0, t.planned) else t.planned
        if (t.planned > 0) HistoryStore.add(c, FocusRecord(t.startWall, spent, t.planned, !early, 0))
        try { Vault.wipe(c, t.id) } catch (e: Exception) { }
    }
}
