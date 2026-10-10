package com.focuslock

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

object GoalStore {
    private fun prefs(c: Context) = c.getSharedPreferences("goals", Context.MODE_PRIVATE)
    fun sessions(c: Context): Int = prefs(c).getInt("sessions", 1)
    fun setSessions(c: Context, n: Int) { prefs(c).edit().putInt("sessions", n.coerceIn(1, 20)).apply() }
}

private data class Ach(val title: String, val desc: String, val have: Long, val need: Long)

@Composable
private fun Stepper(value: Int, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus) { Text("-") }
        Text(
            "$value $unit", Modifier.weight(1f), textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun Bar(fraction: Float) {
    val cs = MaterialTheme.colorScheme
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = cs.primary, trackColor = cs.surfaceVariant
    )
}

@Composable
fun GoalsScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var goalMin by remember { mutableStateOf(HistoryStore.goalMinutes(ctx)) }
    var sessGoal by remember { mutableStateOf(GoalStore.sessions(ctx)) }
    val records = remember { HistoryStore.all(ctx) }
    val byDay = remember { Stats.byDay(records) }
    val today = Stats.dayIndex(System.currentTimeMillis())
    val todayMin = ((byDay[today] ?: 0L) / 60_000).toInt()
    val todaySessions = records.count { Stats.dayIndex(it.start) == today && it.completed }
    val (cur, best) = remember { Stats.streaks(byDay, today) }
    val completed = records.count { it.completed }
    val totalHours = records.sumOf { it.durationMs } / 3_600_000L
    val goalDays = byDay.count { it.value >= goalMin * 60_000L }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Goals", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Set targets and build a habit at your own pace.", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        Panel {
            Text("Daily focus target", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Stepper(goalMin, "min",
                { goalMin = (goalMin - 15).coerceAtLeast(15); HistoryStore.setGoalMinutes(ctx, goalMin) },
                { goalMin = (goalMin + 15).coerceAtMost(720); HistoryStore.setGoalMinutes(ctx, goalMin) })
            Spacer(Modifier.height(8.dp))
            Text("Today: $todayMin of $goalMin min", color = cs.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Bar(todayMin.toFloat() / goalMin)
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            Text("Daily sessions target", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Stepper(sessGoal, if (sessGoal == 1) "session" else "sessions",
                { sessGoal = (sessGoal - 1).coerceAtLeast(1); GoalStore.setSessions(ctx, sessGoal) },
                { sessGoal = (sessGoal + 1).coerceAtMost(20); GoalStore.setSessions(ctx, sessGoal) })
            Spacer(Modifier.height(8.dp))
            Text("Today: $todaySessions of $sessGoal completed", color = cs.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Bar(todaySessions.toFloat() / sessGoal)
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            Text("Streak", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("$cur day${if (cur == 1) "" else "s"} current, $best best",
                style = MaterialTheme.typography.titleMedium, color = cs.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "A day counts when you focus for at least 10 minutes. If you haven't focused yet today, " +
                    "your streak stays alive until midnight.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        val list = listOf(
            Ach("First focus", "Complete one focus session", completed.toLong(), 1),
            Ach("Ten sessions", "Complete 10 focus sessions", completed.toLong(), 10),
            Ach("Goal reached", "Hit your daily target once", goalDays.toLong(), 1),
            Ach("3-day streak", "Focus 3 days in a row", best.toLong(), 3),
            Ach("7-day streak", "Focus 7 days in a row", best.toLong(), 7),
            Ach("Ten hours", "Focus 10 hours in total", totalHours, 10),
            Ach("Fifty hours", "Focus 50 hours in total", totalHours, 50)
        )
        Panel {
            Text("Achievements", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val done = list.filter { it.have >= it.need }
            val next = list.filter { it.have < it.need }
            if (done.isNotEmpty()) {
                Text("Completed", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                done.forEach {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("✓ ${it.title}", fontWeight = FontWeight.Bold)
                        Text(it.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (next.isNotEmpty()) {
                Text("Up next", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                next.forEach {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(it.title, fontWeight = FontWeight.Bold)
                        Text("${it.desc} (${it.have} / ${it.need})",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Bar(it.have.toFloat() / it.need)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
