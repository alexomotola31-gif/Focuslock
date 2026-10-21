package com.alex.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One scheduled lock that actually ran. */
data class ScheduleRun(val key: String, val start: Long, val end: Long, val note: String, val apps: Int)

object ScheduleHistory {
    private fun prefs(c: Context) = c.getSharedPreferences("schedule_history", Context.MODE_PRIVATE)

    fun all(c: Context): List<ScheduleRun> = try {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ScheduleRun(o.getString("k"), o.getLong("s"), o.getLong("e"), o.optString("n", ""), o.optInt("a", 0))
        }
    } catch (e: Exception) { emptyList() }

    /** Called regularly by the lock service: records each scheduled lock the first time it is seen running. */
    @Synchronized
    fun logActive(c: Context) {
        val windows = Schedules.activeWindows(c)
        if (windows.isEmpty()) return
        val known = all(c)
        val fresh = windows.mapNotNull { (s, endMs) ->
            val key = "${s.id}_$endMs"
            if (known.any { it.key == key }) return@mapNotNull null
            val mins = ((s.endMin - s.startMin + 1440) % 1440).let { if (it == 0) 1440 else it }
            ScheduleRun(key, endMs - mins * 60_000L, endMs, s.note, s.pkgs.size)
        }
        if (fresh.isEmpty()) return
        val arr = JSONArray()
        (known + fresh).takeLast(500).forEach {
            arr.put(JSONObject().put("k", it.key).put("s", it.start).put("e", it.end).put("n", it.note).put("a", it.apps))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }
}
