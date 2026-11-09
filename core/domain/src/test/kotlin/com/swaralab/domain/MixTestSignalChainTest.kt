package com.swaralab.domain

import com.swaralab.domain.audio.MixTestSignals
import com.swaralab.domain.audio.VocalMixProcessor
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class MixTestSignalChainTest {
    @Test
    fun cleanA4SurvivesEveryMixChip() {
        val rate = MixTestSignals.SAMPLE_RATE
        for (preset in listOf(VocalPreset.RAW, VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO, VocalPreset.BRIGHT)) {
            val samples = MixTestSignals.cleanTone()
            val before = rms(samples)
            VocalMixProcessor.applyChain(samples, rate, VocalChain.preset(preset))
            val after = rms(samples, rate, rate * 2)
            assertTrue("${preset.name} should keep the clean A4, before=$before after=$after", after > 0.12f)
        }
    }

    @Test
    fun noisyPhraseGapsDropWhileTheToneStays() {
        val rate = MixTestSignals.SAMPLE_RATE
        val noisy = MixTestSignals.noisyPhrase()
        val beforeGap = rms(noisy, 0, rate / 2)
        val beforeTone = rms(noisy, rate + rate / 4, rate * 2)
        for (preset in listOf(VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO)) {
            val samples = noisy.copyOf()
            VocalMixProcessor.applyChain(samples, rate, VocalChain.preset(preset))
            val gap = rms(samples, 0, rate / 2)
            val tone = rms(samples, rate + rate / 4, rate * 2)
            assertTrue("${preset.name} should cut gap hiss, before=$beforeGap after=$gap", gap < beforeGap * 0.45f)
            assertTrue("${preset.name} should keep the 440 Hz tone, before=$beforeTone after=$tone", tone > beforeTone * 0.35f)
        }
    }

    @Test
    fun batchNoisyRecipesLoseGapHissOnCleanWarmRadio() {
        val rate = MixTestSignals.SAMPLE_RATE
        val gapEnd = (rate * MixTestSignals.BATCH_GAP_SEC).toInt()
        val toneFrom = gapEnd + rate / 6
        val toneTo = toneFrom + rate / 2
        val chips = listOf(VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO)
        for (recipe in MixTestSignals.batchRecipes) {
            val noisy = MixTestSignals.batchNoisy(recipe)
            val beforeGap = rms(noisy, 0, gapEnd)
            val beforeTone = rms(noisy, toneFrom, toneTo)
            for (preset in chips) {
                val samples = noisy.copyOf()
                VocalMixProcessor.applyChain(samples, rate, VocalChain.preset(preset))
                val gap = rms(samples, 0, gapEnd)
                val tone = rms(samples, toneFrom, toneTo)
                assertTrue(
                    "${recipe.id} ${preset.name} should cut gap noise, before=$beforeGap after=$gap",
                    gap < beforeGap * 0.50f,
                )
                assertTrue(
                    "${recipe.id} ${preset.name} should keep the note, before=$beforeTone after=$tone",
                    tone > beforeTone * 0.28f && tone > 0.025f,
                )
            }
        }
    }

    @Test
    fun fixtureWavsAreWrittenForAppImport() {
        val dir = MixTestSignals.ensureFixtures()
        val clean = File(dir, "clean_test_signal.wav")
        val noisy = File(dir, "noisy_test_signal.wav")
        assertTrue("clean fixture missing at ${clean.absolutePath}", clean.isFile && clean.length() > 44L)
        assertTrue("noisy fixture missing at ${noisy.absolutePath}", noisy.isFile && noisy.length() > 44L)
        val (cleanPcm, _) = MixTestSignals.readMonoWav(clean)
        val (noisyPcm, _) = MixTestSignals.readMonoWav(noisy)
        val cleanRms = rms(cleanPcm)
        val noisyRms = rms(noisyPcm)
        assertTrue("Noisy fixture should be louder than clean, clean=$cleanRms noisy=$noisyRms", noisyRms > cleanRms + 0.01f)
    }

    private fun rms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val from = start.coerceIn(0, samples.lastIndex.coerceAtLeast(0))
        val to = end.coerceIn(from + 1, samples.size)
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        return sqrt(sum / (to - from)).toFloat()
    }
}
