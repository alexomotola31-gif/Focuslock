package com.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Schedule(val id: Long, val startMin: Int, val endMin: Int, val pkgs: Set<String>)

object ScheduleStore {
    private fun prefs(c: Context) = c.getSharedPreferences("schedules", Context.MODE_PRIVATE)

    fun all(c: Context): List<Schedule> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.getJSONArray("pkgs")
            Schedule(
                o.getLong("id"), o.getInt("start"), o.getInt("end"),
                (0 until p.length()).map { p.getString(it) }.toSet()
            )
        }
    }

    private fun save(c: Context, list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject()
                .put("id", it.id).put("start", it.startMin).put("end", it.endMin)
                .put("pkgs", JSONArray(it.pkgs.toList())))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, s: Schedule) = save(c, all(c) + s)
    fun remove(c: Context, id: Long) = save(c, all(c).filter { it.id != id })
}
