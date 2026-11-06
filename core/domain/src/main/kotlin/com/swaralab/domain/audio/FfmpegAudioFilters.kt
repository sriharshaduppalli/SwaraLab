package com.swaralab.domain.audio

import java.util.Locale
import kotlin.math.pow

/**
 * Exact FFmpeg filter chains for independent key and tempo.
 * atempo is only valid in 0.5–2.0, so extreme values are chained.
 */
object FfmpegAudioFilters {
    fun pitchFactor(semitones: Int): Double = 2.0.pow(semitones / 12.0)

    fun atempoChain(factor: Double): String {
        val parts = ArrayList<String>()
        var x = factor
        while (x < 0.5) {
            parts += formatAtempo(0.5)
            x /= 0.5
        }
        while (x > 2.0) {
            parts += formatAtempo(2.0)
            x /= 2.0
        }
        parts += formatAtempo(x)
        return parts.joinToString(",")
    }

    fun audioFilter(semitones: Int, tempo: Float, sampleRate: Int = 48_000): String {
        val speed = tempo.toDouble().coerceIn(0.5, 1.5)
        if (semitones == 0) return atempoChain(speed)
        val pitch = pitchFactor(semitones)
        val tempoCorrection = speed / pitch
        return "asetrate=$sampleRate*${"%.6f".format(Locale.US, pitch)},aresample=$sampleRate,${atempoChain(tempoCorrection)}"
    }

    fun buildCommand(
        input: String,
        output: String,
        semitones: Int,
        tempo: Float,
        ffmpeg: String = "ffmpeg",
    ): List<String> = listOf(
        ffmpeg, "-y",
        "-i", input,
        "-filter:a", audioFilter(semitones, tempo),
        "-vn",
        output,
    )

    private fun formatAtempo(value: Double): String =
        "atempo=${"%.4f".format(Locale.US, value)}"
}
