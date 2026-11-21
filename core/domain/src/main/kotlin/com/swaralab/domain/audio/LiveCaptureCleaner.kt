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
    private val safeRate = sampleRate.coerceAtLeast(8_000)
    private val hpA = run {
        val rc = 1.0 / (2.0 * Math.PI * 75.0)
        val dt = 1.0 / safeRate
        (rc / (rc + dt)).toFloat()
    }
    private val airA = run {
        val rc = 1.0 / (2.0 * Math.PI * 4_500.0)
        val dt = 1.0 / safeRate
        (rc / (rc + dt)).toFloat()
    }
    private val holdSamples = (safeRate * 0.035).toInt().coerceAtLeast(1)
    private var hpX = 0f
    private var hpY = 0f
    private var airX = 0f
    private var airY = 0f
    private var env = 0f
    private var floor = 0.018f
    private var airEnv = 0f
    private var sibilanceGain = 1f
    private var gateGain = 0.08f

    fun process(samples: ShortArray, length: Int) {
        val n = length.coerceIn(0, samples.size)
        for (i in 0 until n) {
            val incoming = samples[i] / 32768f
            val highPassed = hpA * (hpY + incoming - hpX)
            hpX = incoming
            hpY = highPassed
            val mag = abs(highPassed)
            env = if (mag > env) env + 0.26f * (mag - env) else env * 0.9964f
            if (env < 0.045f) {
                floor = (floor * 0.9992f) + (env * 0.0008f)
            }
            val threshold = max(0.011f, floor * 2.35f)
            val targetGate = if (env < threshold) {
                (env / threshold).pow(1.85f).coerceIn(0.04f, 1f)
            } else {
                1f
            }
            val gateStep = if (targetGate > gateGain) 0.35f else 0.08f
            gateGain += gateStep * (targetGate - gateGain)

            val air = airA * (airY + highPassed - airX)
            airX = highPassed
            airY = air
            airEnv = if (abs(air) > airEnv) abs(air) else airEnv * 0.98f
            val targetSib = if (env < 0.038f && airEnv > env * 1.55f) 0.52f else 1f
            sibilanceGain += 0.02f * (targetSib - sibilanceGain)
            val rawOut = highPassed * gateGain * sibilanceGain
            val softLimited = if (abs(rawOut) > 0.88f) {
                val sign = if (rawOut >= 0f) 1f else -1f
                sign * (0.88f + 0.12f * kotlin.math.tanh(((abs(rawOut) - 0.88f) / 0.12f).toDouble()).toFloat())
            } else {
                rawOut
            }
            samples[i] = (softLimited.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }
}
