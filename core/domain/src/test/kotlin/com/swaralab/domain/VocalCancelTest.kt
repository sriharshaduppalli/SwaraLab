package com.swaralab.domain

import com.swaralab.domain.audio.VocalCancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VocalCancelTest {
    @Test
    fun centeredVoiceDropsAndSideInstrumentStays() {
        val rate = 48_000
        val n = rate / 4
        val left = FloatArray(n)
        val right = FloatArray(n)
        val voice = FloatArray(n)
        val side = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            voice[i] = (0.55 * sin(2 * PI * 1000 * t)).toFloat()
            side[i] = (0.4 * sin(2 * PI * 400 * t)).toFloat()
            left[i] = voice[i] + side[i]
            right[i] = voice[i] - side[i]
        }
        val made = VocalCancel.apply(left, right, rate)
        assertFalse(made.mostlyMono)
        val outMid = FloatArray(n) { i -> (made.left[i] + made.right[i]) * 0.5f }
        val outSide = FloatArray(n) { i -> (made.left[i] - made.right[i]) * 0.5f }
        assertTrue(
            "lyrics should leave the middle: ${rms(outMid)} vs ${rms(voice)}",
            rms(outMid) < rms(voice) * 0.35f,
        )
        assertTrue(
            "band should stay on the sides: ${rms(outSide)} vs ${rms(side)}",
            rms(outSide) > rms(side) * 0.65f,
        )
    }

    @Test
    fun monoSongIsNotSilenced() {
        val rate = 48_000
        val n = rate / 5
        val tone = FloatArray(n) { i ->
            (0.6 * sin(2 * PI * 880 * i.toDouble() / rate)).toFloat()
        }
        val made = VocalCancel.apply(tone, tone.copyOf(), rate)
        assertTrue(made.mostlyMono)
        assertTrue(
            "mono music should still be heard: ${rms(made.left)} vs ${rms(tone)}",
            rms(made.left) > rms(tone) * 0.2f,
        )
    }

    private fun rms(samples: FloatArray): Float {
        var acc = 0.0
        samples.forEach { acc += it * it }
        return sqrt(acc / samples.size).toFloat()
    }
}
