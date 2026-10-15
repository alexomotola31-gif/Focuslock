package com.focuslock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Schedules {
    private const val WEEK = 7 * 1440
    private const val MAX_SKIPS = 3
    private fun prefs(c: Context) = c.getSharedPreferences("sched_state", Context.MODE_PRIVATE)

    /** days is a bitmask: bit 0 = Sunday ... bit 6 = Saturday. calDow is Calendar.DAY_OF_WEEK (1..7). */
    fun hasDay(days: Int, calDow: Int): Boolean = ((days shr (calDow - 1)) and 1) == 1

    fun timeText(min: Int): String {
        val h = min / 60
        val ap = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, min % 60, ap)
    }

    fun clockText(ms: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

    fun daysLabel(days: Int): String {
        if (days == 127) return "Every day"
        val n = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return n.filterIndexed { i, _ -> ((days shr i) and 1) == 1 }.joinToString(", ")
    }

    /** End time (ms) if the schedule's window contains [nowMs], else null. Uses the phone's current clock and zone. */
    fun endIfActive(s: Schedule, nowMs: Long): Long? {
        if (s.startMin == s.endMin || s.days == 0) return null
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dow = now.get(Calendar.DAY_OF_WEEK)
        val prevDow = if (dow == 1) 7 else dow - 1
        fun endAt(plusDays: Int): Long {
            val c = Calendar.getInstance().apply { timeInMillis = nowMs }
            c.add(Calendar.DAY_OF_YEAR, plusDays)
            c.set(Calendar.HOUR_OF_DAY, s.endMin / 60); c.set(Calendar.MINUTE, s.endMin % 60)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        return if (s.startMin < s.endMin) {
            if (hasDay(s.days, dow) && minute >= s.startMin && minute < s.endMin) endAt(0) else null
        } else {
            if (hasDay(s.days, dow) && minute >= s.startMin) endAt(1)
            else if (hasDay(s.days, prevDow) && minute < s.endMin) endAt(0)
            else null
        }
    }

    /** Enabled schedules that are locking right now (and not skipped), with their end times. */
    fun activeWindows(c: Context): List<Pair<Schedule, Long>> {
        val now = System.currentTimeMillis()
        val p = prefs(c)
        return ScheduleStore.all(c).filter { it.enabled }.mapNotNull { s ->
            val end = endIfActive(s, now) ?: return@mapNotNull null
            s to end // strict: a scheduled lock can never be skipped
        }
    }

    fun isBlocked(c: Context, pkg: String): Boolean = activeWindows(c).any { pkg in it.first.pkgs }

    fun skipsLeft(c: Context): Int {
        val p = prefs(c)
        val used = if (p.getLong("skipDay", 0L) == HistoryStore.startOfToday()) p.getInt("skipCount", 0) else 0
        return (MAX_SKIPS - used).coerceAtLeast(0)
    }

    /** Skips the window(s) locking right now. Limited per day. */
    fun skipActive(c: Context): Boolean {
        if (skipsLeft(c) <= 0) return false
        val w = activeWindows(c)
        if (w.isEmpty()) return false
        val p = prefs(c)
        val day = HistoryStore.startOfToday()
        val used = if (p.getLong("skipDay", 0L) == day) p.getInt("skipCount", 0) else 0
        val e = p.edit()
        w.forEach { e.putLong("skip_${it.first.id}", it.second) }
        e.putLong("skipDay", day).putInt("skipCount", used + 1).apply()
        return true
    }

    private fun spans(s: Schedule): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (d in 0..6) {
            if (((s.days shr d) and 1) == 1) {
                val a = d * 1440 + s.startMin
                val b = d * 1440 + (if (s.endMin > s.startMin) s.endMin else s.endMin + 1440)
                out.add(a to b)
            }
        }
        return out
    }

    /** The first other enabled schedule whose days and times overlap the candidate, or null. */
    fun conflict(candidate: Schedule, others: List<Schedule>): Schedule? {
        val mine = spans(candidate)
        return others.firstOrNull { o ->
            o.id != candidate.id && o.enabled && spans(o).any { (a2, b2) ->
                mine.any { (a1, b1) -> listOf(-WEEK, 0, WEEK).any { sh -> a1 < b2 + sh && a2 + sh < b1 } }
            }
        }
    }
}
