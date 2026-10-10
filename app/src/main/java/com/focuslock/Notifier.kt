package com.focuslock

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotifPrefs {
    private fun p(c: Context) = c.getSharedPreferences("notif", Context.MODE_PRIVATE)
    fun on(c: Context, key: String): Boolean = p(c).getBoolean(key, key != "started")
    fun set(c: Context, key: String, v: Boolean) { p(c).edit().putBoolean(key, v).apply() }
}

object Notifier {
    private const val CHANNEL = "focus_events"

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "Focus updates", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "Session, break and goal updates"
                nm.createNotificationChannel(ch)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun post(c: Context, id: Int, title: String, text: String) {
        val app = c.applicationContext
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(app)
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        try { nm.notify(id, n) } catch (e: SecurityException) { }
    }

    fun sessionStarted(c: Context) {
        if (!NotifPrefs.on(c, "started")) return
        val ph = SessionStore.phases(c)
        if (ph.isEmpty()) {
            post(c, 1, "Focus session started", "Locked for ${SessionStore.plannedMs(c) / 60_000} min.")
        } else {
            post(c, 1, "Pomodoro started", "${ph.count { it.kind == 'F' }} focus periods planned.")
        }
    }

    fun phaseChange(c: Context) {
        if (!NotifPrefs.on(c, "phase")) return
        val i = SessionStore.phase(c) ?: return
        if (i.isBreak) {
            post(c, 1, if (i.isLong) "Long break" else "Break time",
                "Nice work. Take ${i.phaseTotalMs / 60_000} min.")
        } else {
            post(c, 1, "Focus time", "Focus period ${i.focusNumber} of ${i.focusTotal} has started.")
        }
    }

    fun afterSession(c: Context, spentMs: Long, cycles: Int, completed: Boolean) {
        if (completed && NotifPrefs.on(c, "done")) {
            val text = if (cycles > 0) "$cycles focus periods done. Well done."
            else "You focused for ${spentMs / 60_000} min."
            post(c, 2, "Focus session complete", text)
        }
        if (spentMs >= 60_000 && NotifPrefs.on(c, "goal")) {
            val goalMs = HistoryStore.goalMinutes(c) * 60_000L
            val after = HistoryStore.todayMs(c)
            if (after >= goalMs && after - spentMs < goalMs) {
                post(c, 3, "Daily goal reached", "You hit your ${goalMs / 60_000} minute target today.")
            }
        }
    }

    private fun pi(app: Context, i: Int, tok: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app, i, Intent(app, PhaseReceiver::class.java).putExtra("tok", tok),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    fun cancelAll(c: Context) {
        val app = c.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..40) {
            val p = pi(app, i, 0, PendingIntent.FLAG_NO_CREATE) ?: continue
            am.cancel(p)
            p.cancel()
        }
    }

    /** One alarm per phase boundary (or just the end for a single session). */
    fun scheduleAll(c: Context) {
        val app = c.applicationContext
        cancelAll(app)
        val s = SessionStore.current(app) ?: return
        val ph = SessionStore.phases(app)
        val planned = SessionStore.plannedMs(app)
        val elapsed = planned - SessionStore.remainingMs(app, s)
        val bounds = ArrayList<Long>()
        if (ph.isEmpty()) bounds.add(planned)
        else { var acc = 0L; ph.forEach { acc += it.ms; bounds.add(acc) } }
        val tok = SessionStore.startWall(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        bounds.forEachIndexed { i, b ->
            val until = b - elapsed
            if (until > 0) {
                val p = pi(app, i, tok, PendingIntent.FLAG_UPDATE_CURRENT)
                if (p != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + until, p)
            }
        }
    }
}

class PhaseReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (SessionStore.startWall(c) != i.getLongExtra("tok", -1L)) return
        if (SessionStore.active(c) == null) return   // session ended: end() already notified
        Notifier.phaseChange(c)
    }
}
