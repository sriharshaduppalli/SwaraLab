package com.swaralab.domain.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

data class TrackProcessingConfig(
    val semitones: Int = 0,
    val tempo: Float = 1f,
) {
    fun isIdentity(): Boolean = semitones == 0 && abs(tempo - 1f) < 0.02f
}

interface TrackProcessor {
    suspend fun process(
        input: java.io.File,
        output: java.io.File,
        config: TrackProcessingConfig,
        onProgress: (Float) -> Unit = {},
    ): Boolean
}

/**
 * Independent pitch then tempo. Pitch: resample + overlap-add back to length.
 * Tempo: overlap-add stretch. No native rubberband / FFmpeg.
 */
object PcmWarp {
    fun shiftAndStretch(
        samples: FloatArray,
        semitones: Int,
        tempo: Float,
        onProgress: (Float) -> Unit = {},
    ): FloatArray {
        if (samples.isEmpty()) return samples
        var x = samples
        val shift = semitones.coerceIn(-6, 6)
        val speed = tempo.coerceIn(0.5f, 1.5f)
        if (shift != 0) {
            val pitch = 2.0.pow(shift / 12.0).toFloat()
            x = resample(x, pitch)
            onProgress(0.45f)
            x = overlapAdd(x, 1f / pitch)
            onProgress(0.7f)
        } else {
            onProgress(0.45f)
        }
        if (abs(speed - 1f) >= 0.02f) {
            x = overlapAdd(x, speed)
        }
        onProgress(0.9f)
        return x
    }

    fun resample(src: FloatArray, rate: Float): FloatArray {
        val factor = rate.coerceIn(0.5f, 1.5f)
        if (abs(factor - 1f) < 0.01f) return src
        val dest = FloatArray((src.size / factor).toInt().coerceAtLeast(1))
        var pos = 0.0
        val step = factor.toDouble()
        for (i in dest.indices) {
            val idx = pos.toInt().coerceIn(0, src.lastIndex)
            val frac = (pos - idx).toFloat()
            val next = (idx + 1).coerceAtMost(src.lastIndex)
            dest[i] = src[idx] * (1f - frac) + src[next] * frac
            pos += step
        }
        return dest
    }

    fun overlapAdd(src: FloatArray, rate: Float, hop: Int = 1024): FloatArray {
        val factor = rate.coerceIn(0.5f, 1.5f)
        if (abs(factor - 1f) < 0.01f) return src
        val win = hop * 2
        if (src.size < win) return resample(src, factor)
        val outLen = (src.size / factor).toInt().coerceAtLeast(win)
        val dest = FloatArray(outLen)
        val window = FloatArray(win) { i ->
            val x = i / (win - 1f)
            (0.5 - 0.5 * cos(2.0 * Math.PI * x)).toFloat()
        }
        var inPos = 0.0
        var outPos = 0
        while (outPos + win <= outLen && inPos.toInt() + win <= src.size) {
            val start = inPos.toInt()
            for (i in 0 until win) {
                dest[outPos + i] += src[start + i] * window[i]
            }
            outPos += hop
            inPos += hop * factor
        }
        return dest
    }

    fun toFloat(src: ShortArray): FloatArray =
        FloatArray(src.size) { i -> src[i] / 32768f }

    fun toShort(src: FloatArray): ShortArray =
        ShortArray(src.size) { i ->
            (src[i] * 32767f).roundToInt().coerceIn(-32767, 32767).toShort()
        }
}
