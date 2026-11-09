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
    fun denoiseGateAndBreathAtZeroLeaveTheTake() {
        val sampleRate = 48_000
        val samples = FloatArray(sampleRate) { i ->
            val hiss = ((i * 1103515245 + 12345) and 0x7fff) / 32768f * 0.10f - 0.05f
            if (i < sampleRate / 4) hiss else hiss + 0.28f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat()
        }
        val beforeGap = rms(samples, 0, sampleRate / 5)
        val beforeTone = rms(samples, sampleRate / 2, sampleRate / 2 + 8192)
        VocalMixProcessor.applyChain(
            samples,
            sampleRate,
            VocalChain(preset = VocalPreset.CUSTOM, denoise = 0f, noiseGate = 0f, breathReduction = 0f),
        )
        val afterGap = rms(samples, 0, sampleRate / 5)
        val afterTone = rms(samples, sampleRate / 2, sampleRate / 2 + 8192)
        assertTrue("Zero denoise should keep the gap, before=$beforeGap after=$afterGap", afterGap > beforeGap * 0.85f)
        assertTrue("Zero processing should keep the note, before=$beforeTone after=$afterTone", afterTone > beforeTone * 0.85f)
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

    @Test
    fun handlingKnockInAQuietGapIsTamed() {
        val sampleRate = 48_000
        val samples = FloatArray(sampleRate) { i ->
            val hiss = ((i * 1103515245 + 12345) and 0x7fff) / 32768f * 0.10f - 0.05f
            val knock = if (i in (sampleRate * 0.4).toInt()..(sampleRate * 0.42).toInt()) 0.32f else 0f
            val tone = if (i > sampleRate / 2) 0.30f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat() else 0f
            hiss + knock + tone
        }
        VocalMixProcessor.applyChain(samples, sampleRate, VocalChain.preset(VocalPreset.CLOSE))
        val knock = samples.sliceArray((sampleRate * 0.39).toInt() until (sampleRate * 0.43).toInt()).maxOf { kotlin.math.abs(it) }
        val voice = rms(samples, sampleRate / 2 + 2048, sampleRate / 2 + 8192)
        assertTrue("Knock should be tamed, knock=$knock", knock < 0.16f)
        assertTrue("Voice should remain after knock taming, voice=$voice", voice > 0.04f)
    }

    @Test
    fun softHallSpreadIsWiderThanKeepDry() {
        val sampleRate = 48_000
        val mono = FloatArray(sampleRate) { i ->
            0.22f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat()
        }
        val dry = VocalMixProcessor.spreadStereo(mono.copyOf(), sampleRate, VocalChain.preset(VocalPreset.RAW))
        val hall = VocalMixProcessor.spreadStereo(mono.copyOf(), sampleRate, VocalChain.preset(VocalPreset.STAGE))
        fun width(pair: Pair<FloatArray, FloatArray>): Float {
            var side = 0.0
            var mid = 0.0
            for (i in pair.first.indices) {
                val m = (pair.first[i] + pair.second[i]) * 0.5f
                val s = (pair.first[i] - pair.second[i]) * 0.5f
                mid += m * m
                side += s * s
            }
            return kotlin.math.sqrt((side / mid).toFloat())
        }
        assertTrue("Hall should be wider than dry, dry=${width(dry)} hall=${width(hall)}", width(hall) > width(dry) * 1.25f)
    }

    @Test
    fun stereoSpreadDoesNotLiftGapHiss() {
        val sampleRate = 48_000
        val hiss = FloatArray(sampleRate) { i ->
            ((i * 1103515245 + 12345) and 0x7fff) / 32768f * 0.12f - 0.06f
        }
        val before = rms(hiss, 0, sampleRate)
        val beforeAir = airEnergy(hiss, sampleRate)
        val (left, right) = VocalMixProcessor.spreadStereo(
            hiss.copyOf(),
            sampleRate,
            VocalChain.preset(VocalPreset.STAGE).copy(preset = VocalPreset.CUSTOM, effectAmount = 1f, thicken = 0.4f),
        )
        val mid = FloatArray(hiss.size) { i -> (left[i] + right[i]) * 0.5f }
        val after = rms(mid, 0, sampleRate)
        val afterAir = airEnergy(mid, sampleRate)
        assertTrue("Spread should not lift the gap, before=$before after=$after", after < before * 1.08f)
        assertTrue("Spread should not add air hiss, before=$beforeAir after=$afterAir", afterAir < beforeAir * 1.12f)
    }

    @Test
    fun reelsCeilingStaysHotterThanMaster() {
        val sampleRate = 8_000
        fun tone(): Pair<FloatArray, FloatArray> {
            val left = FloatArray(sampleRate) { i -> 0.55f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat() }
            return left to left.copyOf()
        }
        val reels = tone()
        val master = tone()
        VocalMixProcessor.finishMasterStereo(reels.first, reels.second, targetLufs = -11f, truePeakDbtp = -0.3f)
        VocalMixProcessor.finishMasterStereo(master.first, master.second, targetLufs = -14f, truePeakDbtp = -1f)
        val reelsPeak = reels.first.maxOf { kotlin.math.abs(it) }
        val masterPeak = master.first.maxOf { kotlin.math.abs(it) }
        assertTrue("Reels peak $reelsPeak should exceed Master $masterPeak", reelsPeak > masterPeak + 0.02f)
    }

    private fun rms(samples: FloatArray, start: Int, end: Int): Float {
        var sum = 0.0
        val from = start.coerceIn(0, samples.lastIndex)
        val to = end.coerceIn(from + 1, samples.size)
        for (i in from until to) sum += samples[i] * samples[i]
        return kotlin.math.sqrt(sum / (to - from)).toFloat()
    }

    private fun airEnergy(samples: FloatArray, sampleRate: Int): Float {
        var sum = 0.0
        var prev = 0f
        val step = (sampleRate / 8_000).coerceAtLeast(1)
        for (i in samples.indices step step) {
            val hp = samples[i] - prev
            prev = samples[i]
            sum += hp * hp
        }
        return kotlin.math.sqrt(sum / (samples.size / step).coerceAtLeast(1)).toFloat()
    }
}
