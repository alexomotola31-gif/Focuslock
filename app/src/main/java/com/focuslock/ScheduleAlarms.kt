package com.focuslock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ScheduleAlarms {
    private const val LEAD_MIN = 5
    private const val ACTION = "com.focuslock.SCHEDULE_ALERT"

    private fun pi(app: Context, label: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app, 9001,
            Intent(app, ScheduleReceiver::class.java).setAction(ACTION).putExtra("name", label),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    private fun nextStart(s: Schedule, after: Long): Long? {
        for (off in 0..8) {
            val c = Calendar.getInstance().apply { timeInMillis = after }
            c.add(Calendar.DAY_OF_YEAR, off)
            if (!Schedules.hasDay(s.days, c.get(Calendar.DAY_OF_WEEK))) continue
            c.set(Calendar.HOUR_OF_DAY, s.startMin / 60); c.set(Calendar.MINUTE, s.startMin % 60)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > after) return c.timeInMillis
        }
        return null
    }

    /** Sets one alarm for the next upcoming "lock starts soon" reminder. Safe to call any time. */
    fun rescheduleAll(c: Context) {
        val app = c.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pi(app, "", PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        val lead = LEAD_MIN * 60_000L
        val now = System.currentTimeMillis()
        var bestT = Long.MAX_VALUE
        var bestMin = -1
        for (s in ScheduleStore.all(app)) {
            if (!s.enabled || s.days == 0 || s.startMin == s.endMin) continue
            val t = nextStart(s, now + lead) ?: continue
            if (t < bestT) { bestT = t; bestMin = s.startMin }
        }
        if (bestMin < 0) return
        val p = pi(app, Schedules.timeText(bestMin), PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestT - lead, p)
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == "com.focuslock.SCHEDULE_ALERT" && NotifPrefs.on(c, "sched")) {
            Notifier.post(c, 4, "Focus lock starting soon", "A scheduled lock starts at ${i.getStringExtra("name")}.")
        }
        ScheduleAlarms.rescheduleAll(c)
    }
}
