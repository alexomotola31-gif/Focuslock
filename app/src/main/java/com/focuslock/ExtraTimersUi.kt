package com.focuslock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Lists running extra timers and, while the main timer is busy, lets the user start another one. */
@Composable
fun ExtraTimersCard(picked: Set<String>, ready: Boolean, canAdd: Boolean, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var tick by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf("30") }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val timers = remember(tick) { ExtraTimers.active(ctx) }
    if (timers.isEmpty() && !canAdd) return

    NxGlassCard(Modifier.fillMaxWidth()) {
        Text("More timers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (timers.isEmpty()) {
            Text("Run another timer at the same time on different apps.", color = cs.onSurfaceVariant)
        }
        timers.forEach { t ->
            val left = (ExtraTimers.remainingMs(ctx, t) / 1000).coerceAtLeast(0)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60),
                    color = cs.primary, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                Text("${t.apps.size} apps locked", color = cs.onSurfaceVariant)
            }
        }
        if (canAdd) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Starts a new timer on the ${picked.size} apps you selected. Change your selection on the Apps page first to lock different apps.",
                color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("15", "30", "60", "90").forEach { m ->
                    OutlinedButton(
                        onClick = { minutes = m }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("${m}m") }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = minutes, onValueChange = { minutes = it.filter(Char::isDigit) },
                label = { Text("Minutes") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = ready && picked.isNotEmpty() && (minutes.toLongOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val id = ExtraTimers.newId()
                    val code = try { Vault.newCode(ctx, id) } catch (e: Exception) { null }
                    ExtraTimers.start(ctx, id, picked, minutes.toLong() * 60_000)
                    tick++
                    if (code != null) onCode(code)
                }
            ) {
                Text(
                    when {
                        !ready -> "Enable accessibility first"
                        picked.isEmpty() -> "Choose apps first"
                        else -> "Start another timer"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
