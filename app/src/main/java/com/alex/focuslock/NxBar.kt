package com.alex.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NxGlassBar(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    val dark = nxDark()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(shape)
            .background(if (dark) NxColors.GlassHi else Color.Black.copy(alpha = 0.06f))
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f), shape)
    ) { content() }
}
