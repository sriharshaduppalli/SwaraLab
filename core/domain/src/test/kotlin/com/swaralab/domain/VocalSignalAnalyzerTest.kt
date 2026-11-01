package com.swaralab.domain

import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VocalSignalAnalyzerTest {
    @Test
    fun detects440HzSineWaveAsA4AndDha() {
        val sampleRate = 48_000
        val samples = ShortArray(2048) { i ->
            val phase = 2.0 * PI * 440.0 * i / sampleRate
            (sin(phase) * 14_000).toInt().toShort()
        }
        val metrics = VocalSignalAnalyzer.analyzeFrame(
            samples = samples,
            length = samples.size,
            sampleRate = sampleRate,
            previous = VocalMetrics(),
        )
        assertEquals("A4", metrics.noteName)
        assertEquals("Dha", metrics.swaraName)
        assertTrue("Expected ~440 Hz, got ${metrics.pitchHz}", abs(metrics.pitchHz - 440f) < 12f)
        assertTrue(metrics.midLevel > 0.1f)
        assertEquals(1, metrics.waveformHistory.size)
        assertEquals(1, metrics.pitchHistory.size)
    }

    @Test
    fun lowFrequencyToneRaisesBassMoreThanTreble() {
        val sampleRate = 48_000
        val samples = ShortArray(2048) { i ->
            val phase = 2.0 * PI * 110.0 * i / sampleRate
            (sin(phase) * 16_000).toInt().toShort()
        }
        val metrics = VocalSignalAnalyzer.analyzeFrame(
            samples = samples,
            length = samples.size,
            sampleRate = sampleRate,
            previous = VocalMetrics(),
        )
        assertTrue(
            "Expected bass (${metrics.bassLevel}) > treble (${metrics.trebleLevel})",
            metrics.bassLevel > metrics.trebleLevel,
        )
    }

    @Test
    fun sampleTanpuraDroneAndBriefBreathDropoutsDoNotFlickerNoteReadout() {
        val sampleRate = 48_000
        val droneFrame = ShortArray(2048) { i ->
            val t = i.toDouble() / sampleRate.toDouble()
            val wave = 0.72 * sin(2.0 * PI * 130.81 * t) + 0.28 * sin(2.0 * PI * 261.63 * t)
            (wave * 9500.0).toInt().toShort()
        }
        val silentFrame = ShortArray(2048)
        val singleSpikeFrame = ShortArray(2048) { i ->
            val t = i.toDouble() / sampleRate.toDouble()
            (sin(2.0 * PI * 392.0 * t) * 12_000.0).toInt().toShort()
        }

        var metrics = VocalMetrics()
        // 10 frames of sample Tanpura drone -> should lock cleanly onto C3 (Sa)
        repeat(10) {
            metrics = VocalSignalAnalyzer.analyzeFrame(droneFrame, droneFrame.size, sampleRate, metrics)
            assertEquals("C3", metrics.noteName)
            assertEquals("Sa", metrics.swaraName)
        }

        // 3 frames of silence (e.g. consonant / breath gap) -> should hold C3 (Sa) without flashing "—"
        repeat(3) {
            metrics = VocalSignalAnalyzer.analyzeFrame(silentFrame, silentFrame.size, sampleRate, metrics)
            assertEquals("C3", metrics.noteName)
            assertEquals("Sa", metrics.swaraName)
        }

        // 1 isolated frame of a different pitch spike -> hysteresis prevents single-frame note flicker
        metrics = VocalSignalAnalyzer.analyzeFrame(singleSpikeFrame, singleSpikeFrame.size, sampleRate, metrics)
        assertEquals("C3", metrics.noteName)
    }
}
