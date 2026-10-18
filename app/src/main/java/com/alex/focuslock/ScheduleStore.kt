package com.alex.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Schedule(
    val id: Long, val startMin: Int, val endMin: Int, val pkgs: Set<String>,
    val days: Int = 127, val enabled: Boolean = true, val note: String = ""
)

object ScheduleStore {
    private fun prefs(c: Context) = c.getSharedPreferences("schedules", Context.MODE_PRIVATE)

    fun all(c: Context): List<Schedule> = try {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.getJSONArray("pkgs")
            Schedule(
                o.getLong("id"), o.getInt("start"), o.getInt("end"),
                (0 until p.length()).map { p.getString(it) }.toSet(),
                o.optInt("days", 127), o.optBoolean("on", true), o.optString("note", "")
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun save(c: Context, list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject()
                .put("id", it.id).put("start", it.startMin).put("end", it.endMin)
                .put("pkgs", JSONArray(it.pkgs.toList()))
                .put("days", it.days).put("on", it.enabled).put("note", it.note))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
        ScheduleAlarms.rescheduleAll(c)
    }

    fun add(c: Context, s: Schedule) = save(c, all(c) + s)
    fun update(c: Context, s: Schedule) = save(c, all(c).map { if (it.id == s.id) s else it })
    fun remove(c: Context, id: Long) = save(c, all(c).filter { it.id != id })
}

object PickedStore {
    private fun prefs(c: Context) = c.getSharedPreferences("picked", Context.MODE_PRIVATE)
    fun load(c: Context): Set<String> = prefs(c).getStringSet("apps", emptySet())?.toSet() ?: emptySet()
    fun save(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("apps", s).apply() }
}
