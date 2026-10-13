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
            "Your focus data stays on your phone. No account and no analytics. The free version shows ads from Google AdMob, which may use your device's advertising ID.",
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
                            "Ads: the free version shows banner ads on the Home screen only when no focus session is running, never on lock screens. " +
                            "They are served by Google AdMob, which may collect your device's advertising ID and IP address to show and measure ads, " +
                            "under Google's privacy policy. In Europe and the UK you are asked for consent first. " +
                            "The apps you lock, your schedules, your sessions and anything Accessibility detects are never shared with advertisers.\n\n" +
                            "Your focus data stays on this phone. Uninstalling the app or clearing its storage deletes it."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}
