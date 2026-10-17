package com.alex.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceCard() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(16.dp)
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Theme", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, t ->
                val sel = ThemePrefs.mode == i
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) cs.primary else cs.surfaceVariant)
                        .clickable { ThemePrefs.changeMode(i) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) { Text(t, color = if (sel) cs.onPrimary else cs.onSurface) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Accent color", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemePrefs.accents.forEachIndexed { i, (name, color) ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { ThemePrefs.changeAccent(i) }
                        .semantics { contentDescription = "$name accent" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(color)
                            .border(if (ThemePrefs.accentIndex == i) 3.dp else 0.dp, cs.onSurface, CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Text size ${(ThemePrefs.textScale * 100).toInt()}%", color = cs.onSurfaceVariant)
        Slider(
            value = ThemePrefs.textScale,
            onValueChange = { ThemePrefs.changeTextScale(it) },
            valueRange = 0.85f..1.3f
        )
        OutlinedButton(onClick = { ThemePrefs.reset() }) { Text("Reset to defaults") }
    }
}
