package com.alex.focuslock

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NxSplash() {
    val t = rememberInfiniteTransition(label = "nxSplash")
    val s by t.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "nxs")
    val bg = Brush.verticalGradient(listOf(Color(0xFF081947), Color(0xFF071642), Color(0xFF061540)))
    Box(Modifier.fillMaxSize().background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val wave = Color(0xFF1B3A8A).copy(alpha = 0.20f)
            val top = Path().apply {
                moveTo(0f, h * 0.07f)
                cubicTo(w * 0.35f, h * 0.22f, w * 0.65f, h * 0.20f, w, h * 0.10f)
                lineTo(w, 0f); lineTo(0f, 0f); close()
            }
            val bottom = Path().apply {
                moveTo(0f, h * 0.88f)
                cubicTo(w * 0.35f, h * 0.76f, w * 0.65f, h * 0.76f, w, h * 0.84f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(top, wave)
            drawPath(bottom, wave)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp)
                        .blur(40.dp, BlurredEdgeTreatment.Unbounded)
                        .background(NxColors.Blue.copy(alpha = 0.30f), CircleShape)
                )
                Image(
                    painterResource(R.drawable.logo), null,
                    Modifier.size(130.dp).graphicsLayer { scaleX = s; scaleY = s }
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Focus") }
                    withStyle(SpanStyle(color = Color(0xFF9A8DF7))) { append("Lock") }
                },
                fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(10.dp))
            Text("Lock in. Stay focused.", color = Color(0xFF9AA6DD), fontSize = 18.sp)
        }
    }
}
