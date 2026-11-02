package com.swaralab.domain.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Local karaoke instrumental: pull a centered lead vocal out of a stereo song.
 * Open DSP only (mid/side + vocal-band cut). Not an ML stem split.
 */
object VocalCancel {
    data class Result(
        val left: FloatArray,
        val right: FloatArray,
        val mostlyMono: Boolean,
    )

    fun apply(
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int,
        amount: Float = 1f,
    ): Result {
        val frames = min(left.size, right.size)
        val strength = amount.coerceIn(0f, 1f)
        val outL = FloatArray(frames)
        val outR = FloatArray(frames)
        if (frames == 0) return Result(outL, outR, mostlyMono = true)

        var midEnergy = 0.0
        var sideEnergy = 0.0
        for (i in 0 until frames) {
            val mid = (left[i] + right[i]) * 0.5f
            val side = (left[i] - right[i]) * 0.5f
            midEnergy += mid * mid
            sideEnergy += side * side
        }
        val mostlyMono = sqrt(sideEnergy / frames) < 0.08 * (sqrt(midEnergy / frames) + 1e-5)

        val bassCoeff = onePoleCoeff(180f, sampleRate)
        var bassLp = 0f
        val lyricKeep = if (mostlyMono) 1f - 0.35f * strength else 0f

        for (i in 0 until frames) {
            val mid = (left[i] + right[i]) * 0.5f
            val side = (left[i] - right[i]) * 0.5f
            bassLp += bassCoeff * (mid - bassLp)
            val lyricBand = mid - bassLp
            val midOut = bassLp + lyricBand * lyricKeep
            if (mostlyMono) {
                outL[i] = midOut
                outR[i] = midOut
            } else {
                outL[i] = midOut + side
                outR[i] = midOut - side
            }
        }

        var peak = 1e-6f
        for (i in 0 until frames) {
            peak = max(peak, max(abs(outL[i]), abs(outR[i])))
        }
        val gain = min(0.89f / peak, 1.35f)
        if (gain != 1f) {
            for (i in 0 until frames) {
                outL[i] *= gain
                outR[i] *= gain
            }
        }
        return Result(left = outL, right = outR, mostlyMono = mostlyMono)
    }

    private fun onePoleCoeff(hz: Float, sampleRate: Int): Float {
        val safeRate = sampleRate.coerceAtLeast(8_000)
        return (1f - exp((-2.0 * Math.PI * hz / safeRate).toDouble())).toFloat().coerceIn(0.01f, 0.95f)
    }
}
