package com.alex.focuslock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class PomoCfg(
    val focusMin: Int = 25, val shortMin: Int = 5, val longMin: Int = 15,
    val every: Int = 4, val rounds: Int = 4, val lockBreaks: Boolean = true
)

/** kind: F focus, S short break, L long break */
data class Phase(val kind: Char, val ms: Long) { val isBreak: Boolean get() = kind != 'F' }

data class PhaseInfo(
    val isBreak: Boolean, val isLong: Boolean,
    val phaseRemainingMs: Long, val phaseTotalMs: Long,
    val focusNumber: Int, val focusTotal: Int,
    val index: Int, val isLast: Boolean
)

object PomodoroPrefs {
    private lateinit var prefs: SharedPreferences
    var enabled by mutableStateOf(false)
        private set

    fun init(c: Context) {
        prefs = c.applicationContext.getSharedPreferences("pomodoro", Context.MODE_PRIVATE)
        enabled = prefs.getBoolean("enabled", false)
    }
    fun enable(v: Boolean) { enabled = v; prefs.edit().putBoolean("enabled", v).apply() }
}

object Pomodoro {
    private fun prefs(c: Context) = c.getSharedPreferences("pomodoro", Context.MODE_PRIVATE)

    fun config(c: Context): PomoCfg {
        val p = prefs(c)
        val d = PomoCfg()
        return PomoCfg(
            p.getInt("focus", d.focusMin).coerceIn(1, 90),
            p.getInt("short", d.shortMin).coerceIn(1, 30),
            p.getInt("long", d.longMin).coerceIn(1, 60),
            p.getInt("every", d.every).coerceIn(2, 8),
            p.getInt("rounds", d.rounds).coerceIn(1, 12),
            p.getBoolean("lockBreaks", d.lockBreaks)
        )
    }

    fun save(c: Context, cfg: PomoCfg) {
        prefs(c).edit()
            .putInt("focus", cfg.focusMin).putInt("short", cfg.shortMin).putInt("long", cfg.longMin)
            .putInt("every", cfg.every).putInt("rounds", cfg.rounds).putBoolean("lockBreaks", cfg.lockBreaks)
            .apply()
    }

    fun buildPhases(cfg: PomoCfg): List<Phase> {
        val out = ArrayList<Phase>()
        for (i in 1..cfg.rounds) {
            out.add(Phase('F', cfg.focusMin * 60_000L))
            if (i < cfg.rounds) {
                if (i % cfg.every == 0) out.add(Phase('L', cfg.longMin * 60_000L))
                else out.add(Phase('S', cfg.shortMin * 60_000L))
            }
        }
        return out
    }

    fun encode(ph: List<Phase>): String = ph.joinToString(",") { "${it.kind}${it.ms}" }

    fun decode(s: String): List<Phase> =
        if (s.isBlank()) emptyList()
        else s.split(",").mapNotNull { t ->
            val k = t.firstOrNull() ?: return@mapNotNull null
            val ms = t.drop(1).toLongOrNull() ?: return@mapNotNull null
            Phase(k, ms)
        }

    fun startRun(c: Context, apps: Set<String>) {
        val cfg = config(c)
        val ph = buildPhases(cfg)
        SessionStore.start(c, apps, ph.sumOf { it.ms }, encode(ph), cfg.lockBreaks)
    }
}
