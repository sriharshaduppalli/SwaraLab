package com.swaralab.domain

import com.swaralab.domain.audio.AutocorrelationPitchDetector
import com.swaralab.domain.audio.PcmWarp
import com.swaralab.domain.layout.SessionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PcmWarpTest {
    @Test
    fun tempoStretchShortensBuffer() {
        val src = FloatArray(48_000) { 0.1f }
        val faster = PcmWarp.shiftAndStretch(src, 0, 1.5f)
        assertTrue(faster.size < src.size)
        assertTrue(abs(faster.size - src.size / 1.5f) < src.size * 0.08f)
    }

    @Test
    fun pitchShiftKeepsApproximateDuration() {
        val src = FloatArray(48_000) { i -> sin(2.0 * PI * 220.0 * i / 48_000).toFloat() }
        val shifted = PcmWarp.shiftAndStretch(src, 2, 1f)
        assertTrue(abs(shifted.size - src.size) < src.size * 0.08f)
    }

    @Test
    fun processedCacheNameIsStable() {
        val path = SessionLayout.processedBacking("media/backing/Song Name.mp3", -2, 0.9f)
        assertEquals("media/processed/Song_Name_s-2_t0.90.wav", path)
        assertTrue(SessionLayout.isSafeRelative(path))
    }

    @Test
    fun autocorrelationDetectsA4() {
        val rate = 48_000
        val buffer = FloatArray(2048) { i ->
            sin(2.0 * PI * 440.0 * i / rate).toFloat() * 0.5f
        }
        val sample = AutocorrelationPitchDetector().process(buffer, rate)
        assertTrue(sample != null)
        assertTrue(abs(sample!!.frequencyHz - 440f) < 12f)
        assertTrue(sample.confidence >= 0.3f)
        assertEquals(69, sample.midiNote)
        assertTrue(abs(sample.centsOffset ?: 99f) < 20f)
    }

    @Test
    fun autocorrelationSkipsSilence() {
        val buffer = FloatArray(2048) { 0f }
        assertNull(AutocorrelationPitchDetector().process(buffer, 48_000))
    }
}
