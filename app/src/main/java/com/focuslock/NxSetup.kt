package com.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NxSteps(done: List<Boolean>) {
    val cs = MaterialTheme.colorScheme
    NxGlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SETUP", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("${done.count { it }} of ${done.size} done", color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            done.forEach { d ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(CircleShape)
                        .background(if (d) cs.primary else cs.onSurface.copy(alpha = 0.12f))
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
