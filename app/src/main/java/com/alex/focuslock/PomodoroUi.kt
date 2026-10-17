package com.alex.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ModeToggle() {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Single session" to false, "Pomodoro" to true).forEach { (label, mode) ->
            val sel = PomodoroPrefs.enabled == mode
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) cs.primary else cs.surfaceVariant)
                    .clickable { PomodoroPrefs.enable(mode) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text(label, color = if (sel) cs.onPrimary else cs.onSurface) }
        }
    }
}

@Composable
private fun Step(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("-") }
        Text(value, Modifier.width(76.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("+") }
    }
}

@Composable
fun PomodoroSetupCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var cfg by remember { mutableStateOf(Pomodoro.config(ctx)) }
    fun upd(n: PomoCfg) { cfg = n; Pomodoro.save(ctx, n) }
    val totalMin = Pomodoro.buildPhases(cfg).sumOf { it.ms } / 60_000

    Panel {
        Text("Pomodoro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Step("Focus", "${cfg.focusMin} min",
            { upd(cfg.copy(focusMin = (cfg.focusMin - 5).coerceAtLeast(1))) },
            { upd(cfg.copy(focusMin = (cfg.focusMin + 5).coerceAtMost(90))) })
        Step("Short break", "${cfg.shortMin} min",
            { upd(cfg.copy(shortMin = (cfg.shortMin - 1).coerceAtLeast(1))) },
            { upd(cfg.copy(shortMin = (cfg.shortMin + 1).coerceAtMost(30))) })
        Step("Long break", "${cfg.longMin} min",
            { upd(cfg.copy(longMin = (cfg.longMin - 5).coerceAtLeast(1))) },
            { upd(cfg.copy(longMin = (cfg.longMin + 5).coerceAtMost(60))) })
        Step("Focus periods", "${cfg.rounds}",
            { upd(cfg.copy(rounds = (cfg.rounds - 1).coerceAtLeast(1))) },
            { upd(cfg.copy(rounds = (cfg.rounds + 1).coerceAtMost(12))) })
        Step("Long break every", "${cfg.every}",
            { upd(cfg.copy(every = (cfg.every - 1).coerceAtLeast(2))) },
            { upd(cfg.copy(every = (cfg.every + 1).coerceAtMost(8))) })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Keep apps locked during breaks", Modifier.weight(1f))
            Switch(checked = cfg.lockBreaks, onCheckedChange = { upd(cfg.copy(lockBreaks = it)) })
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "About $totalMin min in total. The last focus period has no break after it.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}
