package com.focuslock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Appearance settings, saved locally and restored on launch. */
object ThemePrefs {
    private lateinit var prefs: SharedPreferences

    val accents = listOf(
        "Gold" to Color(0xFFFFB323),
        "Blue" to Color(0xFF4DA3FF),
        "Green" to Color(0xFF3DDC97),
        "Purple" to Color(0xFFB388FF),
        "Coral" to Color(0xFFFF7A70)
    )

    var mode by mutableStateOf(0)          // 0 system, 1 light, 2 dark
        private set
    var accentIndex by mutableStateOf(0)
        private set
    var textScale by mutableStateOf(1f)
        private set

    val accent: Color get() = accents[accentIndex].second

    fun init(c: Context) {
        prefs = c.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        mode = prefs.getInt("mode", 0).coerceIn(0, 2)
        accentIndex = prefs.getInt("accent", 0).coerceIn(0, accents.size - 1)
        textScale = prefs.getFloat("textScale", 1f).coerceIn(0.85f, 1.3f)
    }

    fun setMode(v: Int) { mode = v; prefs.edit().putInt("mode", v).apply() }
    fun setAccent(v: Int) { accentIndex = v; prefs.edit().putInt("accent", v).apply() }
    fun setTextScale(v: Float) { textScale = v; prefs.edit().putFloat("textScale", v).apply() }
    fun reset() {
        prefs.edit().clear().apply()
        mode = 0; accentIndex = 0; textScale = 1f
    }
}
