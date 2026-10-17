package com.alex.focuslock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NxColors {
    val BgTop = Color(0xFF0B1226)
    val BgBottom = Color(0xFF121C3D)
    val Blue = Color(0xFF4C8DFF)
    val BlueDark = Color(0xFF3B72D9)
    val Amber = Color(0xFFF5B942)
    val Crimson = Color(0xFFEF4444)
    val Mint = Color(0xFF34D399)
    val Glass = Color.White.copy(alpha = 0.08f)
    val GlassHi = Color.White.copy(alpha = 0.14f)
    val GlassBorder = Color.White.copy(alpha = 0.15f)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.70f)
    val TextMuted = Color.White.copy(alpha = 0.45f)
}

@Composable
fun nxDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun NxBackground(content: @Composable BoxScope.() -> Unit) {
    val flat = MaterialTheme.colorScheme.background
    val bg = if (nxDark()) Brush.verticalGradient(listOf(NxColors.BgTop, NxColors.BgBottom)) else Brush.verticalGradient(listOf(flat, flat))
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(
            Modifier.size(320.dp).offset((-90).dp, (-60).dp)
                .blur(90.dp, BlurredEdgeTreatment.Unbounded)
                .background(NxColors.Blue.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(260.dp).offset(80.dp, 60.dp)
                .blur(80.dp, BlurredEdgeTreatment.Unbounded)
                .background(NxColors.Amber.copy(alpha = 0.14f), CircleShape)
        )
        content()
    }
}

@Composable
fun NxGlassCard(
    modifier: Modifier = Modifier,
    radius: Int = 24,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    Column(
        modifier.clip(shape)
            .background(if (nxDark()) Brush.linearGradient(listOf(NxColors.GlassHi, NxColors.Glass)) else Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.03f))))
            .border(1.dp, if (nxDark()) NxColors.GlassBorder else Color.Black.copy(alpha = 0.12f), shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
fun NxPillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(NxColors.Blue, NxColors.BlueDark)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun NxChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) NxColors.Amber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val edge = if (selected) NxColors.Amber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Box(
        Modifier.clip(CircleShape).background(fill).border(1.dp, edge, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (selected) NxColors.Amber else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
