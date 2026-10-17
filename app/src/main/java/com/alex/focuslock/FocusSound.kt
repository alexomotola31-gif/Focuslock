package com.alex.focuslock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Generates a few seconds of noise and loops it. No audio files, so nothing to license. */
object FocusSound {
    val names = listOf("Silence", "Soft noise", "Rain-like", "Wind-like")
    private const val RATE = 16000
    private const val SECONDS = 4
    private var track: AudioTrack? = null

    var choice by mutableStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var volume by mutableStateOf(0.5f)
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("sound", Context.MODE_PRIVATE)

    fun init(c: Context) {
        val p = prefs(c)
        choice = p.getInt("choice", 0).coerceIn(0, names.size - 1)
        volume = p.getFloat("volume", 0.5f).coerceIn(0f, 1f)
    }

    private fun gen(kind: Int): ShortArray {
        val n = RATE * SECONDS
        val out = ShortArray(n)
        val r = Random(kind * 7919)
        var brown = 0.0
        var low = 0.0
        for (i in 0 until n) {
            val white = r.nextDouble() * 2 - 1
            val v: Double = when (kind) {
                1 -> { brown = (brown + 0.02 * white) / 1.02; brown * 10.0 }
                2 -> { low = low * 0.6 + white * 0.4; (white * 0.35 + low * 0.9) * 1.5 }
                else -> {
                    brown = (brown + 0.02 * white) / 1.02
                    brown * 10.0 * (0.6 + 0.4 * sin(2 * PI * i / n))
                }
            }
            val edge = minOf(i, n - 1 - i).coerceAtMost(400) / 400.0
            out[i] = (v.coerceIn(-1.0, 1.0) * 12000 * edge).toInt().toShort()
        }
        return out
    }

    private fun stopTrack() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    private fun start() {
        if (choice == 0) return
        stopTrack()
        val data = gen(choice)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        t.setLoopPoints(0, data.size, -1)
        t.setVolume(volume)
        t.play()
        track = t
        playing = true
    }

    fun stop() { stopTrack(); playing = false }

    fun toggle(c: Context) { if (playing) stop() else start() }

    fun select(c: Context, i: Int) {
        choice = i
        prefs(c).edit().putInt("choice", i).apply()
        if (i == 0) stop() else if (playing) start()
    }

    fun changeVolume(c: Context, v: Float) {
        volume = v
        track?.setVolume(v)
        prefs(c).edit().putFloat("volume", v).apply()
    }
}
