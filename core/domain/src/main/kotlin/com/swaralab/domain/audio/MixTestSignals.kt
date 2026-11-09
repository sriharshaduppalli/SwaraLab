package com.swaralab.domain.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Mix/edit booth fixtures at 48 kHz mono. Used by tests and
 * tools/generate_mix_test_signals.py (same recipe ids).
 */
object MixTestSignals {
    const val SAMPLE_RATE = 48_000
    const val DURATION_SEC = 5.0
    const val FREQUENCY_HZ = 440.0
    const val VOLUME = 0.5f
    const val NOISE_LEVEL = 0.2f
    const val BATCH_DURATION_SEC = 3.0
    const val BATCH_GAP_SEC = 0.5

    data class Recipe(
        val id: String,
        val hz: Double,
        val volume: Float,
        val white: Float = 0f,
        val hiss: Float = 0f,
        val rumble: Float = 0f,
        val hum: Float = 0f,
        val knock: Boolean = false,
        val secondHz: Double? = null,
    )

    val batchRecipes: List<Recipe> = listOf(
        Recipe("01_a4_white", hz = 440.0, volume = 0.50f, white = 0.20f),
        Recipe("02_c4_hiss", hz = 261.63, volume = 0.48f, white = 0.08f, hiss = 0.14f),
        Recipe("03_e4_rumble", hz = 329.63, volume = 0.50f, white = 0.07f, rumble = 0.09f),
        Recipe("04_a3_hum", hz = 220.0, volume = 0.48f, white = 0.10f, hum = 0.06f),
        Recipe("05_melody_knock", hz = 392.0, volume = 0.46f, white = 0.10f, knock = true, secondHz = 523.25),
        Recipe("06_a5_air", hz = 880.0, volume = 0.40f, white = 0.10f, hiss = 0.16f),
        Recipe("07_quiet_lowsnr", hz = 349.23, volume = 0.26f, white = 0.14f, hiss = 0.08f),
        Recipe("08_fifth", hz = 440.0, volume = 0.42f, white = 0.12f, secondHz = 660.0),
    )

    fun cleanTone(): FloatArray = render(addNoise = false, phrase = false)

    fun noisyTone(): FloatArray = render(addNoise = true, phrase = false)

    fun cleanPhrase(): FloatArray = render(addNoise = false, phrase = true)

    fun noisyPhrase(): FloatArray = render(addNoise = true, phrase = true)

    fun batchClean(recipe: Recipe): FloatArray = renderBatch(recipe, noisy = false)

    fun batchNoisy(recipe: Recipe): FloatArray = renderBatch(recipe, noisy = true)

    fun writeMonoWav(file: File, samples: FloatArray, sampleRate: Int = SAMPLE_RATE) {
        val data = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray())
        data.putInt(36 + samples.size * 2)
        data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray())
        data.putInt(16)
        data.putShort(1)
        data.putShort(1)
        data.putInt(sampleRate)
        data.putInt(sampleRate * 2)
        data.putShort(2)
        data.putShort(16)
        data.put("data".toByteArray())
        data.putInt(samples.size * 2)
        for (s in samples) {
            data.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        file.parentFile?.mkdirs()
        file.writeBytes(data.array())
    }

    fun readMonoWav(file: File): Pair<FloatArray, Int> {
        val bytes = file.readBytes()
        val rate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceAtLeast(1)
        val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val frames = (bytes.size - 44) / (2 * channels)
        val samples = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            repeat(channels) { sum += pcm.short / 32768f }
            samples[i] = sum / channels
        }
        return samples to rate
    }

    fun fixtureDir(): File {
        var dir = File(".").canonicalFile
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) return File(dir, "testdata/mix-fixtures")
            dir = dir.parentFile ?: break
        }
        return File("testdata/mix-fixtures").canonicalFile
    }

    fun batchDir(): File = File(fixtureDir(), "batch")

    fun ensureFixtures(): File {
        val dir = fixtureDir()
        val files = listOf(
            "clean_test_signal.wav" to { cleanTone() },
            "noisy_test_signal.wav" to { noisyTone() },
            "clean_test_phrase.wav" to { cleanPhrase() },
            "noisy_test_phrase.wav" to { noisyPhrase() },
        )
        for ((name, samples) in files) {
            val file = File(dir, name)
            if (!file.isFile || file.length() < 44L) writeMonoWav(file, samples())
        }
        return dir
    }

    fun ensureBatch(): File {
        val dir = batchDir()
        for (recipe in batchRecipes) {
            writeMonoWav(File(dir, "${recipe.id}_clean.wav"), batchClean(recipe))
            writeMonoWav(File(dir, "${recipe.id}_noisy.wav"), batchNoisy(recipe))
        }
        return dir
    }

    private fun render(addNoise: Boolean, phrase: Boolean): FloatArray {
        val n = (SAMPLE_RATE * DURATION_SEC).toInt()
        val gap = SAMPLE_RATE
        val sustainEnd = n - gap
        val rng = Lcg(if (phrase) 441L else 440L)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val singing = !phrase || (i in gap until sustainEnd)
            var sample = if (singing) {
                sin(2.0 * PI * FREQUENCY_HZ * i / SAMPLE_RATE).toFloat() * VOLUME
            } else {
                0f
            }
            if (addNoise) sample += (rng.nextFloat() * 2f - 1f) * NOISE_LEVEL
            out[i] = sample.coerceIn(-1f, 1f)
        }
        return out
    }

    private fun renderBatch(recipe: Recipe, noisy: Boolean): FloatArray {
        val n = (SAMPLE_RATE * BATCH_DURATION_SEC).toInt()
        val gap = (SAMPLE_RATE * BATCH_GAP_SEC).toInt()
        val sustainEnd = n - gap
        val rng = Lcg(recipe.id.hashCode().toLong() and 0x7fff_ffffL)
        val out = FloatArray(n)
        val knockFrom = (SAMPLE_RATE * 0.22).toInt()
        val knockTo = (SAMPLE_RATE * 0.24).toInt()
        val split = gap + (sustainEnd - gap) / 2
        for (i in 0 until n) {
            val singing = i in gap until sustainEnd
            val hz = if (recipe.secondHz != null && i >= split) recipe.secondHz else recipe.hz
            val t = i / SAMPLE_RATE.toDouble()
            var sample = if (singing) sin(2.0 * PI * hz * t).toFloat() * recipe.volume else 0f
            if (noisy) {
                sample += (rng.nextFloat() * 2f - 1f) * recipe.white
                sample += (rng.nextFloat() * 2f - 1f) * recipe.hiss *
                    sin(2.0 * PI * 8_400.0 * t).toFloat()
                sample += recipe.rumble * sin(2.0 * PI * 58.0 * t).toFloat()
                sample += recipe.hum * sin(2.0 * PI * 50.0 * t).toFloat()
                if (recipe.knock && i in knockFrom..knockTo) sample += 0.32f
            }
            out[i] = sample.coerceIn(-1f, 1f)
        }
        return out
    }

    private class Lcg(seed: Long) {
        private var state = seed.or(1L)
        fun nextFloat(): Float {
            state = (state * 1_103_515_245L + 12_345L) and 0x7fff_ffffL
            return state / 2147483647f
        }
    }
}
