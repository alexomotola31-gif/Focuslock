package com.alex.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class FocusRecord(
    val start: Long, val durationMs: Long, val plannedMs: Long,
    val completed: Boolean, val cycles: Int = 0
)

/** Real session records, stored locally. Everything in Insights/Goals is computed from this. */
object HistoryStore {
    private fun prefs(c: Context) = c.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun all(c: Context): List<FocusRecord> = try {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FocusRecord(o.getLong("s"), o.getLong("d"), o.getLong("p"), o.getBoolean("c"), o.optInt("k", 0))
        }
    } catch (e: Exception) { emptyList() }

    fun add(c: Context, r: FocusRecord) {
        if (r.durationMs < 60_000) return
        val list = (all(c) + r).takeLast(3000)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("s", it.start).put("d", it.durationMs)
                .put("p", it.plannedMs).put("c", it.completed).put("k", it.cycles))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun today(c: Context): List<FocusRecord> = all(c).filter { it.start >= startOfToday() }
    fun todayMs(c: Context): Long = today(c).sumOf { it.durationMs }

    fun goalMinutes(c: Context): Int = prefs(c).getInt("goalMin", 60)
    fun setGoalMinutes(c: Context, m: Int) { prefs(c).edit().putInt("goalMin", m.coerceIn(5, 720)).apply() }
}
