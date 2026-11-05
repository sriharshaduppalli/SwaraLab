package com.swaralab.domain

import com.swaralab.domain.audio.MonitorMix
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorMixTest {
    @Test
    fun mixerAppliesVoiceAndTrackGain() {
        val voice = FloatArray(4) { 0.5f }
        val track = FloatArray(4) { 0.8f }
        val output = FloatArray(4)
        MonitorMix.process(voice, track, output, 4, voiceGain = 1.0f, trackGain = 0.5f)
        assertEquals(0.9f, output[0], 0.01f)
    }

    @Test
    fun mixerClampsHotSum() {
        val out = MonitorMix.sample(voice = 1f, track = 1f, voiceGain = 2f, trackGain = 2f)
        assertEquals(1f, out, 0.001f)
    }
}
