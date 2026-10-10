package com.focuslock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context)
    }

    companion object {
        /** Pushes the current session state to every FocusLock widget on the home screen. */
        fun update(c: Context) {
            val app = c.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, FocusWidget::class.java))
            if (ids.isEmpty()) return
            val rv = RemoteViews(app.packageName, R.layout.widget_focus)
            val open = PendingIntent.getActivity(
                app, 1,
                Intent(app, MainActivity::class.java).putExtra("go", "home")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            val s = SessionStore.current(app)
            val rem = if (s != null) SessionStore.remainingMs(app, s) else 0L
            if (s != null && rem > 0) {
                val ph = SessionStore.phase(app)
                rv.setTextViewText(R.id.widget_title, when {
                    ph == null -> "Focus session"
                    ph.isBreak -> "Break time"
                    else -> "Focus ${ph.focusNumber} of ${ph.focusTotal}"
                })
                rv.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                rv.setChronometerCountDown(R.id.widget_timer, true)
                rv.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + rem, null, true)
                rv.setTextViewText(
                    R.id.widget_sub,
                    if (SessionStore.breakUnlocked(app)) "Break: apps unlocked" else "Apps locked. Tap to open."
                )
            } else {
                rv.setTextViewText(R.id.widget_title, "FocusLock")
                rv.setViewVisibility(R.id.widget_timer, View.GONE)
                rv.setTextViewText(R.id.widget_sub, "No session running. Tap to start.")
            }
            mgr.updateAppWidget(ids, rv)
        }
    }
}
