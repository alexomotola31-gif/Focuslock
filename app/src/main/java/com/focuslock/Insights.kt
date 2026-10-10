package com.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Stats {
    private const val DAY = 86_400_000L
    const val ACTIVE_DAY_MS = 10 * 60_000L

    /** Local calendar day number (DST-safe, no java.time needed). */
    fun dayIndex(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / DAY

    fun byDay(r: List<FocusRecord>): Map<Long, Long> =
        r.groupBy { dayIndex(it.start) }.mapValues { e -> e.value.sumOf { it.durationMs } }

    /** Returns (current streak, best streak) in days. */
    fun streaks(byDay: Map<Long, Long>, today: Long): Pair<Int, Int> {
        val active = byDay.filter { it.value >= ACTIVE_DAY_MS }.keys.sorted()
        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in active) {
            run = if (d == prev + 1) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        val set = active.toSet()
        var d = if (today in set) today else today - 1
        var cur = 0
        while (d in set) { cur++; d-- }
        return cur to best
    }
}

fun fmtDur(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "%dh %02dm".format(m / 60, m % 60) else "$m min"
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        content = content
    )
}

@Composable
private fun Tile(mod: Modifier, label: String, value: String) {
    Panel(mod) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun InsightsScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val records = remember { HistoryStore.all(ctx) }
    val byDay = remember { Stats.byDay(records) }
    val today = Stats.dayIndex(System.currentTimeMillis())
    val goalMs = HistoryStore.goalMinutes(ctx) * 60_000L
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Built from your real sessions. Stored only on this phone.", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        if (records.isEmpty()) {
            Panel {
                Text("No sessions yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Finish a focus session and your stats, charts and history will appear here.", color = cs.onSurfaceVariant)
            }
        } else {
            val days = (today - 6..today).toList()
            val todayMs = byDay[today] ?: 0L
            val weekMs = days.sumOf { byDay[it] ?: 0L }
            val completed = records.count { it.completed }
            val avg = records.sumOf { it.durationMs } / records.size
            val reached = days.count { (byDay[it] ?: 0L) >= goalMs }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), "Today", fmtDur(todayMs))
                Tile(Modifier.weight(1f), "Last 7 days", fmtDur(weekMs))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), "Completed sessions", "$completed")
                Tile(Modifier.weight(1f), "Average session", fmtDur(avg))
            }
            Spacer(Modifier.height(10.dp))
            Panel {
                Text("Daily goal reached on $reached of the last 7 days", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))

            Panel {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val vals = days.map { (byDay[it] ?: 0L) / 60_000f }
                val max = maxOf(vals.maxOrNull() ?: 0f, 1f)
                Row(Modifier.fillMaxWidth().height(120.dp)) {
                    days.forEachIndexed { i, d ->
                        val v = vals[i]
                        Column(
                            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height((88f * v / max).coerceAtLeast(4f).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (v > 0f) cs.primary else cs.surfaceVariant)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(letters[((d + 4) % 7).toInt()], style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Panel {
                Text("Activity, last 5 weeks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    letters.forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                val first = today - ((today + 4) % 7) - 28
                for (r in 0..4) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0..6) {
                            val idx = first + r * 7 + c
                            val ms = byDay[idx] ?: 0L
                            val color = when {
                                idx > today -> Color.Transparent
                                ms <= 0L -> cs.surfaceVariant
                                ms < 15 * 60_000L -> cs.primary.copy(alpha = 0.35f)
                                ms < 30 * 60_000L -> cs.primary.copy(alpha = 0.55f)
                                ms < 60 * 60_000L -> cs.primary.copy(alpha = 0.8f)
                                else -> cs.primary
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .clip(RoundedCornerShape(6.dp)).background(color)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Panel {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val fmt = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
                records.reversed().take(20).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(Date(r.start)))
                            Text(
                                if (r.completed) "Completed" else "Ended early",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.completed) cs.primary else cs.onSurfaceVariant
                            )
                        }
                        Text(fmtDur(r.durationMs), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = cs.surfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
