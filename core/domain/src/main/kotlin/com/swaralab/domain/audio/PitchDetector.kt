package com.swaralab.domain.audio

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

interface PitchDetector {
    fun process(buffer: FloatArray, sampleRate: Int, timestampMs: Long = 0L): PitchSample?
}

/**
 * Lightweight normalized autocorrelation on a downsampled buffer.
 * Picks the first strong fundamental peak after the initial zero-crossing dip
 * with parabolic lag interpolation to prevent harmonic/octave jumping.
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
        val rms = sqrt(energy / n)
        if (rms < 0.008f || energy <= 1e-6f) return null

        val minLag = (effRate / maxHz).toInt().coerceAtLeast(4)
        val maxLag = (effRate / minHz).toInt().coerceAtMost(n - 2)
        if (minLag >= maxLag) return null

        val corr = FloatArray(maxLag + 1)
        var globalMaxCorr = 0f
        var passedFirstDip = false
        var firstDipLag = minLag
        for (lag in minLag..maxLag) {
            var sum = 0f
            var e0 = 0f
            var eLag = 0f
            val limit = n - lag
            for (i in 0 until limit) {
                val a = x[i]
                val b = x[i + lag]
                sum += a * b
                e0 += a * a
                eLag += b * b
            }
            val denom = sqrt(e0 * eLag)
            val normCorr = if (denom > 1e-8f) (sum / denom).coerceIn(-1f, 1f) else 0f
            corr[lag] = normCorr
            if (!passedFirstDip) {
                if (normCorr < 0.2f) {
                    passedFirstDip = true
                    firstDipLag = lag
                }
            } else if (normCorr > globalMaxCorr) {
                globalMaxCorr = normCorr
            }
        }
        if (!passedFirstDip || globalMaxCorr < 0.35f) return null

        val threshold = (globalMaxCorr * 0.85f).coerceAtLeast(0.35f)
        var bestLag = -1
        var bestCorr = 0f
        for (lag in (firstDipLag + 1) until maxLag) {
            val c = corr[lag]
            if (c >= threshold && c >= corr[lag - 1] && c >= corr[lag + 1]) {
                bestLag = lag
                bestCorr = c
                break
            }
        }
        if (bestLag < 0) {
            for (lag in (firstDipLag + 1)..maxLag) {
                if (corr[lag] > bestCorr) {
                    bestCorr = corr[lag]
                    bestLag = lag
                }
            }
        }
        if (bestLag <= 0 || bestCorr < 0.35f) return null

        val refinedLag = if (bestLag in (minLag + 1) until maxLag) {
            val prev = corr[bestLag - 1]
            val curr = corr[bestLag]
            val next = corr[bestLag + 1]
            val denom = 2f * (2f * curr - prev - next)
            val delta = if (kotlin.math.abs(denom) > 1e-6f) ((next - prev) / denom).coerceIn(-0.5f, 0.5f) else 0f
            bestLag + delta
        } else {
            bestLag.toFloat()
        }

        val freq = effRate.toFloat() / refinedLag
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
