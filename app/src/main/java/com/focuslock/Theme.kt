package com.focuslock

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePrefs.mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val accent = ThemePrefs.accent
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF1A1200),
        secondary = Color(0xFF4DA3FF), onSecondary = Color(0xFF001B3A),
        background = Color(0xFF08132F), onBackground = Color(0xFFF3F5FA),
        surface = Color(0xFF14234A), onSurface = Color(0xFFF3F5FA),
        surfaceVariant = Color(0xFF263252), onSurfaceVariant = Color(0xFFA8B5D6),
        outline = Color(0xFF3A4A7A)
    ) else lightColorScheme(
        primary = lerp(accent, Color.Black, 0.45f), onPrimary = Color.White,
        secondary = Color(0xFF1F5FBF), onSecondary = Color.White,
        background = Color(0xFFF3F5FA), onBackground = Color(0xFF08132F),
        surface = Color.White, onSurface = Color(0xFF08132F),
        surfaceVariant = Color(0xFFE3E8F5), onSurfaceVariant = Color(0xFF43507A),
        outline = Color(0xFF9AA6C8)
    )
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * ThemePrefs.textScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
