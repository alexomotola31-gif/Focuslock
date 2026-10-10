package com.focuslock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun clock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60)
    else "%02d:%02d".format(t / 60, t % 60)
}

@Composable
fun ActiveSessionCard(onEnded: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var remaining by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(1L) }
    var info by remember { mutableStateOf<PhaseInfo?>(null) }
    var lockBreaks by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            val s = SessionStore.active(ctx)
            if (s == null) { onEnded(); break }
            remaining = SessionStore.remainingMs(ctx, s)
            total = SessionStore.plannedMs(ctx).coerceAtLeast(remaining).coerceAtLeast(1)
            info = SessionStore.phase(ctx)
            lockBreaks = SessionStore.locksBreaks(ctx)
            delay(500)
        }
    }
    val i = info
    val br = i?.isBreak == true
    val shownRem = i?.phaseRemainingMs ?: remaining
    val shownTotal = (i?.phaseTotalMs ?: total).coerceAtLeast(1)
    val progress = (1f - shownRem.toFloat() / shownTotal.toFloat()).coerceIn(0f, 1f)
    val title = if (i == null) "Session active"
        else if (i.isBreak) (if (i.isLong) "Long break" else "Short break")
        else "Focus ${i.focusNumber} of ${i.focusTotal}"
    val note = when {
        i == null -> "Your selected apps are locked until the timer ends."
        br && !lockBreaks -> "Break: your apps are unlocked until the next focus period."
        br -> "Break: apps stay locked. Total remaining ${clock(remaining)}."
        else -> "Apps are locked. Total remaining ${clock(remaining)}."
    }
    val track = cs.surfaceVariant
    val arc = cs.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = cs.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 16.dp.toPx()
                val sz = Size(size.width - w, size.height - w)
                val tl = Offset(w / 2, w / 2)
                drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
                drawArc(arc, -90f, 360f * progress, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clock(shownRem), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(if (i == null) "remaining" else if (br) "break left" else "focus left", color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(note, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        if (i != null) {
            Spacer(Modifier.height(12.dp))
            if (i.isBreak) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { SessionStore.skipBreak(ctx) }, modifier = Modifier.weight(1f)) { Text("Skip break") }
                    OutlinedButton(onClick = { SessionStore.finishNow(ctx) }, modifier = Modifier.weight(1f)) { Text("Finish cycle") }
                }
            } else if (!i.isLast) {
                OutlinedButton(onClick = { SessionStore.finishAfterCurrent(ctx) }) { Text("Finish after this period") }
            }
        }
    }
}

@Composable
fun DailyProgressCard(refreshKey: Any) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val doneMin = remember(refreshKey) { (HistoryStore.todayMs(ctx) / 60_000).toInt() }
    val sessions = remember(refreshKey) { HistoryStore.today(ctx).size }
    val goal = remember(refreshKey) { HistoryStore.goalMinutes(ctx) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(16.dp)
    ) {
        Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("$doneMin of $goal min focused", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (doneMin.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = cs.primary, trackColor = cs.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (sessions == 0) "No sessions yet today. Start one below." else "$sessions session(s) today",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}
