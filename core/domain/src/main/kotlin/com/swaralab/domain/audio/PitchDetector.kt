package com.swaralab.domain.audio

import kotlin.math.ln
import kotlin.math.roundToInt

interface PitchDetector {
    fun process(buffer: FloatArray, sampleRate: Int, timestampMs: Long = 0L): PitchSample?
}

/**
 * Lightweight autocorrelation on a downsampled buffer. Skip silence and low correlation.
 */
class AutocorrelationPitchDetector(
    private val minHz: Float = 70f,
    private val maxHz: Float = 1000f,
) : PitchDetector {
    override fun process(buffer: FloatArray, sampleRate: Int, timestampMs: Long): PitchSample? {
        if (buffer.size < 512 || sampleRate <= 0) return null
        val decimation = if (sampleRate >= 32_000) 2 else 1
        val effRate = sampleRate / decimation
        val n = buffer.size / decimation
        if (n < 64) return null
        val x = FloatArray(n)
        var mean = 0f
        for (i in 0 until n) {
            val v = buffer[i * decimation]
            x[i] = v
            mean += v
        }
        mean /= n
        var energy = 0f
        for (i in 0 until n) {
            x[i] -= mean
            energy += x[i] * x[i]
        }
        if (energy <= 1e-8f) return null

        val minLag = (effRate / maxHz).toInt().coerceAtLeast(4)
        val maxLag = (effRate / minHz).toInt().coerceAtMost(n - 2)
        if (minLag >= maxLag) return null

        var bestLag = -1
        var bestCorr = 0f
        var passedFirstDip = false
        for (lag in minLag..maxLag) {
            var sum = 0f
            val limit = n - lag
            for (i in 0 until limit) {
                sum += x[i] * x[i + lag]
            }
            val normCorr = sum / energy
            if (!passedFirstDip) {
                if (normCorr < 0.25f) passedFirstDip = true
            } else if (normCorr > bestCorr) {
                bestCorr = normCorr
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestCorr < 0.3f) return null
        val freq = effRate.toFloat() / bestLag
        if (freq !in minHz..maxHz) return null
        val midiExact = 69.0 + 12.0 * (ln((freq / 440.0)) / ln(2.0))
        val midi = midiExact.roundToInt()
        val cents = ((midiExact - midi) * 100.0).toFloat()
        return PitchSample(
            timestampMs = timestampMs,
            frequencyHz = freq,
            confidence = bestCorr.coerceIn(0f, 1f),
            midiNote = midi,
            centsOffset = cents,
        )
    }
}
