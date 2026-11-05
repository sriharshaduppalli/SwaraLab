package com.swaralab.domain

import com.swaralab.domain.audio.VocalMixProcessor
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VocalMixProcessorTest {
    @Test
    fun spectralDenoiseCutsHissHarderThanTheSungTone() {
        val sampleRate = 48_000
        val samples = FloatArray(sampleRate) { i ->
            val hiss = ((i * 1103515245 + 12345) and 0x7fff) / 32768f * 0.14f - 0.07f
            if (i < sampleRate / 3) {
                hiss
            } else {
                hiss + 0.30f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat()
            }
        }
        val beforeNoise = rms(samples, 0, sampleRate / 4)
        val beforeTone = rms(samples, sampleRate / 2, sampleRate / 2 + 8192)
        VocalMixProcessor.applyChain(
            samples,
            sampleRate,
            VocalChain(preset = VocalPreset.RAW, denoise = 0.9f, compression = 0f, reverb = 0f, delay = 0f),
        )
        val afterNoise = rms(samples, 0, sampleRate / 4)
        val afterTone = rms(samples, sampleRate / 2, sampleRate / 2 + 8192)
        assertTrue("Noise floor should drop, before=$beforeNoise after=$afterNoise", afterNoise < beforeNoise * 0.65f)
        assertTrue("Sung tone should still be present, before=$beforeTone after=$afterTone", afterTone > beforeTone * 0.45f)
    }

    @Test
    fun voiceForwardMixDoesNotLiftGapHiss() {
        val sampleRate = 48_000
        val samples = FloatArray(sampleRate) { i ->
            val hiss = ((i * 1103515245 + 12345) and 0x7fff) / 32768f * 0.10f - 0.05f
            if (i < sampleRate / 3) hiss else hiss + 0.28f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat()
        }
        val beforeHiss = rms(samples, 0, sampleRate / 5)
        VocalMixProcessor.applyChain(samples, sampleRate, VocalChain.preset(VocalPreset.CLOSE))
        val afterHiss = rms(samples, 0, sampleRate / 5)
        val afterTone = rms(samples, sampleRate / 2, sampleRate / 2 + 8192)
        assertTrue("Mix gap should be quieter, before=$beforeHiss after=$afterHiss", afterHiss < beforeHiss * 0.35f)
        assertTrue("Voice should remain, after=$afterTone", afterTone > 0.04f)
    }

    private fun rms(samples: FloatArray, start: Int, end: Int): Float {
        var sum = 0.0
        val from = start.coerceIn(0, samples.lastIndex)
        val to = end.coerceIn(from + 1, samples.size)
        for (i in from until to) sum += samples[i] * samples[i]
        return kotlin.math.sqrt(sum / (to - from)).toFloat()
    }
}
