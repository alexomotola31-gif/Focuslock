package com.focuslock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NotifSettingsCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val items = listOf(
        "started" to "Session started",
        "done" to "Session completed",
        "phase" to "Pomodoro focus and break changes",
        "goal" to "Daily goal reached"
    )
    val states = remember {
        mutableStateMapOf<String, Boolean>().apply { items.forEach { put(it.first, NotifPrefs.on(ctx, it.first)) } }
    }
    Panel {
        Text("Notification choices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        items.forEach { (key, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Switch(checked = states[key] == true, onCheckedChange = { states[key] = it; NotifPrefs.set(ctx, key, it) })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Notifications never show your emergency code.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}
