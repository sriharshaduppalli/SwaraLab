package com.swaralab.domain.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Zero-lookahead cleanup for the live mic. Runs on each capture buffer so
 * headphones and the written take lose rumble and gap hiss without a
 * spectral pass that would add latency.
 */
class LiveCaptureCleaner(
    sampleRate: Int,
) {
    private val hpA = run {
        val rc = 1.0 / (2.0 * Math.PI * 90.0)
        val dt = 1.0 / sampleRate.coerceAtLeast(8_000)
        (rc / (rc + dt)).toFloat()
    }
    private val airA = run {
        val rc = 1.0 / (2.0 * Math.PI * 4_200.0)
        val dt = 1.0 / sampleRate.coerceAtLeast(8_000)
        (rc / (rc + dt)).toFloat()
    }
    private var hpX = 0f
    private var hpY = 0f
    private var airX = 0f
    private var airY = 0f
    private var env = 0f
    private var floor = 0.018f
    private var airEnv = 0f

    fun process(samples: ShortArray, length: Int) {
        val n = length.coerceIn(0, samples.size)
        for (i in 0 until n) {
            val incoming = samples[i] / 32768f
            val highPassed = hpA * (hpY + incoming - hpX)
            hpX = incoming
            hpY = highPassed
            val mag = abs(highPassed)
            env = if (mag > env) env + 0.28f * (mag - env) else env * 0.9964f
            if (env < 0.045f) {
                floor = (floor * 0.9992f) + (env * 0.0008f)
            }
            val threshold = max(0.011f, floor * 2.35f)
            val gate = if (env < threshold) {
                (env / threshold).pow(1.85f).coerceIn(0.04f, 1f)
            } else {
                1f
            }
            val air = airA * (airY + highPassed - airX)
            airX = highPassed
            airY = air
            airEnv = if (abs(air) > airEnv) abs(air) else airEnv * 0.975f
            var out = highPassed * gate
            if (env < 0.038f && airEnv > env * 1.55f) {
                out *= 0.52f
            }
            samples[i] = (out.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }
}
