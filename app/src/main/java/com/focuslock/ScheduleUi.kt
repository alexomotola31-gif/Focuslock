package com.focuslock

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ScheduleScreen(picked: Set<String>) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var list by remember { mutableStateOf(ScheduleStore.all(ctx)) }
    var tick by remember { mutableStateOf(0) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var startMin by remember { mutableStateOf(19 * 60) }
    var endMin by remember { mutableStateOf(22 * 60) }
    var days by remember { mutableStateOf(127) }
    var formApps by remember { mutableStateOf(picked) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    val active = remember(list, tick) { Schedules.activeWindows(ctx).associate { it.first.id to it.second } }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick++ } }

    fun pickTime(cur: Int, onSet: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onSet(h * 60 + m) }, cur / 60, cur % 60, false).show()
    }
    fun clash(s: Schedule): String? {
        val o = Schedules.conflict(s, list) ?: return null
        return "Overlaps with ${Schedules.timeText(o.startMin)} to ${Schedules.timeText(o.endMin)} " +
            "(${Schedules.daysLabel(o.days)}). Change the times or days."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Lock apps automatically on the days and times you choose.", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Panel {
            Text(
                if (editingId == null) "New schedule" else "Edit schedule",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickTime(startMin) { startMin = it } }, modifier = Modifier.weight(1f)) {
                    Text("From ${Schedules.timeText(startMin)}")
                }
                OutlinedButton(onClick = { pickTime(endMin) { endMin = it } }, modifier = Modifier.weight(1f)) {
                    Text("To ${Schedules.timeText(endMin)}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                letters.forEachIndexed { i, l ->
                    val on = ((days shr i) and 1) == 1
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(if (on) cs.primary else cs.surfaceVariant)
                            .clickable { days = days xor (1 shl i) },
                        contentAlignment = Alignment.Center
                    ) { Text(l, color = if (on) cs.onPrimary else cs.onSurface, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("${formApps.size} apps in this schedule", color = cs.onSurfaceVariant)
            OutlinedButton(onClick = { formApps = picked }, enabled = picked.isNotEmpty()) {
                Text("Use the ${picked.size} apps selected on the Apps page")
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                val keep = list.firstOrNull { it.id == editingId }?.enabled ?: true
                val cand = Schedule(editingId ?: System.currentTimeMillis(), startMin, endMin, formApps, days, keep)
                val c = clash(cand)
                error = when {
                    startMin == endMin -> "Start and end time can't be the same."
                    days == 0 -> "Pick at least one day."
                    formApps.isEmpty() -> "Choose apps on the Apps page first."
                    c != null -> c
                    else -> ""
                }
                if (error.isEmpty()) {
                    if (editingId == null) ScheduleStore.add(ctx, cand) else ScheduleStore.update(ctx, cand)
                    list = ScheduleStore.all(ctx)
                    editingId = null; startMin = 19 * 60; endMin = 22 * 60; days = 127; formApps = picked
                    saved = "Schedule saved."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (editingId == null) "Add schedule" else "Save changes", fontWeight = FontWeight.Bold) }
        }
        if (error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(error, color = cs.error) }
        if (saved.isNotEmpty() && error.isEmpty()) { Spacer(Modifier.height(8.dp)); Text(saved, color = cs.primary) }
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) {
            Panel {
                Text("No schedules yet", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Pick times and days above, then tap Add schedule.", color = cs.onSurfaceVariant)
            }
        }
        list.forEach { s ->
            val endMs = active[s.id]
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${Schedules.timeText(s.startMin)} to ${Schedules.timeText(s.endMin)}",
                            fontWeight = FontWeight.Bold, color = cs.primary
                        )
                        Text(Schedules.daysLabel(s.days), color = cs.onSurfaceVariant)
                        Text("${s.pkgs.size} apps", color = cs.onSurfaceVariant)
                        if (endMs != null) {
                            Text("Locking now, until ${Schedules.clockText(endMs)}",
                                style = MaterialTheme.typography.bodySmall, color = cs.primary)
                        }
                    }
                    Switch(
                        checked = s.enabled, enabled = endMs == null,
                        onCheckedChange = { on ->
                            val msg = if (on) clash(s.copy(enabled = true)) else null
                            if (msg != null) { error = msg } else {
                                error = ""; saved = ""
                                ScheduleStore.update(ctx, s.copy(enabled = on)); list = ScheduleStore.all(ctx)
                            }
                        }
                    )
                }
                if (endMs == null) {
                    Row {
                        TextButton(onClick = {
                            editingId = s.id; startMin = s.startMin; endMin = s.endMin
                            days = s.days; formApps = s.pkgs; error = ""; saved = ""
                        }) { Text("Edit") }
                        TextButton(onClick = {
                            ScheduleStore.remove(ctx, s.id); list = ScheduleStore.all(ctx); saved = ""
                        }) { Text("Delete") }
                    }
                } else {
                    Text("Can't be changed while it is locking.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Scheduled locks follow your phone's clock and time zone. They don't block Settings, " +
                "and Android can't guarantee any app lock is impossible to bypass.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }
}
