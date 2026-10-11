package com.focuslock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NxRing(progress: Float, diameter: Dp = 250.dp, content: @Composable ColumnScope.() -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val track = if (nxDark()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx()
            val sz = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
            val brush = Brush.sweepGradient(
                listOf(NxColors.Blue, accent),
                Offset(size.width / 2, size.height / 2)
            )
            rotate(-90f) {
                drawArc(
                    brush, 0f, 360f * progress.coerceIn(0f, 1f), false, tl, sz,
                    style = Stroke(w, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
