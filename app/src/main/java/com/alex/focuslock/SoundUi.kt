package com.alex.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SoundCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Panel {
        Text("Focus sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        listOf(listOf(0, 1), listOf(2, 3)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val sel = FocusSound.choice == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (sel) cs.primary else cs.surfaceVariant)
                            .clickable { FocusSound.select(ctx, i) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(FocusSound.names[i], color = if (sel) cs.onPrimary else cs.onSurface) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = FocusSound.choice != 0, onClick = { FocusSound.toggle(ctx) }) {
                Text(if (FocusSound.playing) "Pause" else "Play")
            }
            Spacer(Modifier.width(12.dp))
            Slider(
                value = FocusSound.volume,
                onValueChange = { FocusSound.changeVolume(ctx, it) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Generated on your phone, no downloads. It keeps playing in the background while Android keeps FocusLock running, and stops when a session ends.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}
