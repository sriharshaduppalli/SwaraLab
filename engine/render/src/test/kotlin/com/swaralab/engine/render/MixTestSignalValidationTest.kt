package com.swaralab.engine.render

import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ExportKind
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.time.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mix & Save validation on the checked-in clean/noisy A4 fixtures.
 * Regenerates testdata/mix-fixtures if the WAVs are missing.
 */
class MixTestSignalValidationTest {
    @Test
    fun cleanAndNoisyFixturesMixThroughEveryChip() = runBlocking {
        val fixtures = ensureFixtures()
        val repo = fixtures.parentFile?.parentFile ?: fixtures
        val compare = File(repo, "build/mix-compare").apply { mkdirs() }
        val root = Files.createTempDirectory("swaralab-a4-fixtures").toFile()
        try {
            val rate = 48_000
            val (clean, _) = readMonoWav(File(fixtures, "clean_test_signal.wav"))
            val (noisy, _) = readMonoWav(File(fixtures, "noisy_test_phrase.wav"))
            File(fixtures, "clean_test_signal.wav").copyTo(File(compare, "clean_test_signal.wav"), overwrite = true)
            File(fixtures, "noisy_test_signal.wav").copyTo(File(compare, "noisy_test_signal.wav"), overwrite = true)
            File(fixtures, "noisy_test_phrase.wav").copyTo(File(compare, "noisy_test_phrase.wav"), overwrite = true)

            val reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            val engine = WavMixRenderEngine(root)
            val seconds = min(clean.size, noisy.size) / rate
            val durationMs = seconds * 1000L

            val cleanDoc = sessionWithTake(reducer, root, File(fixtures, "clean_test_signal.wav"), "Clean A4", durationMs)
            val noisyDoc = sessionWithTake(reducer, root, File(fixtures, "noisy_test_phrase.wav"), "Noisy A4 phrase", durationMs)

            for (preset in listOf(VocalPreset.RAW, VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO, VocalPreset.BRIGHT)) {
                val cleanOut = exportVocals(engine, root, withChain(reducer, cleanDoc, preset))
                val noisyOut = exportVocals(engine, root, withChain(reducer, noisyDoc, preset))
                writeStereoWav(File(compare, "fixture_clean_${preset.name.lowercase()}.wav"), cleanOut, cleanOut, rate)
                writeStereoWav(File(compare, "fixture_noisy_${preset.name.lowercase()}.wav"), noisyOut, noisyOut, rate)
                val cleanTone = rms(cleanOut, rate, rate * 2)
                val noisyGap = rms(noisyOut, 0, rate / 2)
                val noisyTone = rms(noisyOut, rate + rate / 4, rate * 2)
                val sourceGap = rms(noisy, 0, rate / 2)
                assertTrue("${preset.name} clean A4 should survive, rms=$cleanTone", cleanTone > 0.10f)
                assertTrue("${preset.name} noisy gap should drop, source=$sourceGap mixed=$noisyGap", noisyGap < sourceGap * 0.50f)
                assertTrue("${preset.name} noisy tone should survive, rms=$noisyTone", noisyTone > 0.08f)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sessionWithTake(
        reducer: SessionReducer,
        root: File,
        take: File,
        title: String,
        durationMs: Long,
    ): SessionDocument {
        var doc = reducer.create(SessionKind.PRACTICE, title)
        val sessionDir = File(root, doc.id).apply { mkdirs() }
        val takeRel = SessionLayout.takeWav(take.nameWithoutExtension)
        take.copyTo(File(sessionDir, takeRel).also { it.parentFile?.mkdirs() }, overwrite = true)
        val leadId = doc.tracks.first { it.role == TrackRole.LEAD }.id
        doc = applied(reducer, doc, SessionCommand.CommitTake(leadId, takeRel, durationMs, label = title))
        return doc
    }

    private fun withChain(reducer: SessionReducer, doc: SessionDocument, preset: VocalPreset) =
        applied(reducer, doc, SessionCommand.SetVocalChain(VocalChain.preset(preset)))

    private fun applied(reducer: SessionReducer, doc: SessionDocument, command: SessionCommand) =
        (reducer.apply(doc, command) as SessionEdit.Applied).document

    private suspend fun exportVocals(engine: WavMixRenderEngine, root: File, doc: SessionDocument): FloatArray {
        val result = engine.export(doc, ExportRequest(PublishTarget.MASTER, ExportKind.VOCALS))
        assertTrue("vocals wrote, got $result", result is ExportResult.Written)
        val file = File(File(root, doc.id), (result as ExportResult.Written).relativePath)
        val (left, _, _) = readStereoWav(file)
        return left
    }

    private fun ensureFixtures(): File {
        var dir = File(".").canonicalFile
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) {
                val fixtures = File(dir, "testdata/mix-fixtures")
                writeIfMissing(File(fixtures, "clean_test_signal.wav"), tone(noise = false, phrase = false))
                writeIfMissing(File(fixtures, "noisy_test_signal.wav"), tone(noise = true, phrase = false))
                writeIfMissing(File(fixtures, "clean_test_phrase.wav"), tone(noise = false, phrase = true))
                writeIfMissing(File(fixtures, "noisy_test_phrase.wav"), tone(noise = true, phrase = true))
                return fixtures
            }
            dir = dir.parentFile ?: break
        }
        error("Could not find SwaraLab repo root for testdata/mix-fixtures")
    }

    private fun writeIfMissing(file: File, samples: FloatArray) {
        if (file.isFile && file.length() > 44L) return
        writeMonoWav(file, samples, 48_000)
    }

    private fun tone(noise: Boolean, phrase: Boolean): FloatArray {
        val rate = 48_000
        val n = rate * 5
        val gap = rate
        val rng = longArrayOf(if (phrase) 441L else 440L)
        fun next(): Float {
            rng[0] = (rng[0] * 1_103_515_245L + 12_345L) and 0x7fff_ffffL
            return rng[0] / 2147483647f
        }
        return FloatArray(n) { i ->
            val singing = !phrase || (i in gap until (n - gap))
            var sample = if (singing) sin(2.0 * PI * 440.0 * i / rate).toFloat() * 0.5f else 0f
            if (noise) sample += (next() * 2f - 1f) * 0.2f
            sample.coerceIn(-1f, 1f)
        }
    }

    private fun rms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val from = start.coerceIn(0, samples.lastIndex.coerceAtLeast(0))
        val to = end.coerceIn(from + 1, samples.size)
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    private fun readMonoWav(file: File): Pair<FloatArray, Int> {
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

    private fun readStereoWav(file: File): Triple<FloatArray, FloatArray, Int> {
        val bytes = file.readBytes()
        val rate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceAtLeast(1)
        val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val frames = (bytes.size - 44) / (2 * channels)
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (i in 0 until frames) {
            left[i] = pcm.short / 32768f
            right[i] = if (channels > 1) pcm.short / 32768f else left[i]
        }
        return Triple(left, right, rate)
    }

    private fun writeMonoWav(file: File, samples: FloatArray, rate: Int) {
        val data = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        header(data, rate, 1, samples.size * 2)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.parentFile?.mkdirs()
        file.writeBytes(data.array())
    }

    private fun writeStereoWav(file: File, left: FloatArray, right: FloatArray, rate: Int) {
        val n = min(left.size, right.size)
        val data = ByteBuffer.allocate(44 + n * 4).order(ByteOrder.LITTLE_ENDIAN)
        header(data, rate, 2, n * 4)
        for (i in 0 until n) {
            data.putShort((left[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            data.putShort((right[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        file.parentFile?.mkdirs()
        file.writeBytes(data.array())
    }

    private fun header(data: ByteBuffer, rate: Int, channels: Int, dataBytes: Int) {
        data.put("RIFF".toByteArray())
        data.putInt(36 + dataBytes)
        data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray())
        data.putInt(16)
        data.putShort(1)
        data.putShort(channels.toShort())
        data.putInt(rate)
        data.putInt(rate * channels * 2)
        data.putShort((channels * 2).toShort())
        data.putShort(16)
        data.put("data".toByteArray())
        data.putInt(dataBytes)
    }
}
