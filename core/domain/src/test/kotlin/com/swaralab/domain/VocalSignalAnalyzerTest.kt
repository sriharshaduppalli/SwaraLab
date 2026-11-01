package com.swaralab.domain

import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class VocalSignalAnalyzerTest {
    @Test
    fun detects440HzSineWaveAsA4AndDha() {
        val sampleRate = 48_000
        val samples = ShortArray(2048) { i ->
            val phase = 2.0 * Math.PI * 440.0 * i / sampleRate
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
            val phase = 2.0 * Math.PI * 110.0 * i / sampleRate
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
}
