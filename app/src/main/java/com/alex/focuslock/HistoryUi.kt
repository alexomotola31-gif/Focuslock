package com.alex.focuslock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class HistoryItem(val start: Long, val timer: FocusRecord?, val run: ScheduleRun?)

/** Past timers and scheduled locks, newest first. */
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var filter by remember { mutableStateOf(0) } // 0 all, 1 timers, 2 schedules
    val timers = remember { HistoryStore.all(ctx) }
    val runs = remember { ScheduleHistory.all(ctx) }
    val items = remember(filter) {
        (timers.map { HistoryItem(it.start, it, null) } + runs.map { HistoryItem(it.start, null, it) })
            .filter { filter == 0 || (filter == 1 && it.timer != null) || (filter == 2 && it.run != null) }
            .sortedByDescending { it.start }
            .take(150)
    }
    val dayFmt = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
    val clockFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your past timers and scheduled locks.", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Timers", "Schedules").forEachIndexed { i, label ->
                if (filter == i) {
                    Button(onClick = { filter = i }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { filter = i }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(label) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            NxGlassCard(Modifier.fillMaxWidth()) {
                Text("Nothing here yet", fontWeight = FontWeight.Bold)
                Text("Finished timers and scheduled locks will show up here.", color = cs.onSurfaceVariant)
            }
        }
        items.forEach { item ->
            NxGlassCard(Modifier.fillMaxWidth()) {
                val t = item.timer
                val r = item.run
                if (t != null) {
                    Text("TIMER", color = cs.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(dayFmt.format(Date(t.start)), fontWeight = FontWeight.Bold)
                    Text("Focused ${t.durationMs / 60_000} of ${t.plannedMs / 60_000} min planned", color = cs.onSurfaceVariant)
                    Text(if (t.completed) "Finished" else "Ended early", color = if (t.completed) cs.primary else cs.error)
                    if (t.cycles > 0) Text("${t.cycles} Pomodoro cycles", color = cs.onSurfaceVariant)
                } else if (r != null) {
                    Text("SCHEDULE", color = cs.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(dayFmt.format(Date(r.start)), fontWeight = FontWeight.Bold)
                    Text("${clockFmt.format(Date(r.start))} to ${clockFmt.format(Date(r.end))}", color = cs.onSurfaceVariant)
                    if (r.note.isNotBlank()) Text(r.note)
                    Text("${r.apps} apps locked", color = cs.onSurfaceVariant)
                    Text(if (r.end > now) "Running now" else "Completed", color = cs.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
