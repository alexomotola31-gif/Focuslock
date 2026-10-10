package com.focuslock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyCard() {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Panel {
        Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "FocusLock keeps everything on your phone. No account, no ads, no analytics, and no internet access.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        TextButton(onClick = { open = true }) { Text("Read details") }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Privacy details") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Stored on this phone only: the apps you choose to lock, your schedules, goals and settings, " +
                            "and a history of your focus sessions for Insights and streaks.\n\n" +
                            "Your emergency code is shown once and kept only as a salted hash in encrypted storage.\n\n" +
                            "Accessibility is used only to notice when an app you chose is opened. It does not read your " +
                            "screen, typing, passwords or messages.\n\n" +
                            "Device admin is used only to stop uninstalling during a session.\n\n" +
                            "Nothing is sent anywhere. Uninstalling the app or clearing its storage deletes all data."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}
