package com.swaralab.engine.render

import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.ScaleMode
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
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Booth rehearsal: a short song (pad + bass + sung line), room hiss,
 * rumble, and a knock. Mix each singer chip and a pitch-shifted voice.
 */
class SampleSongMixReviewTest {
    @Test
    fun sampleSongStaysCleanAcrossMixesAndVoiceChange() = runBlocking {
        val root = Files.createTempDirectory("swaralab-song-review").toFile()
        try {
            val reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            var doc = reducer.create(SessionKind.SONG, "Sample Booth Song")
            val sessionDir = File(root, doc.id).apply { mkdirs() }
            val sampleRate = 48_000
            val durationMs = 4_000

            val backingRel = SessionLayout.backingFile("sample-song.wav")
            writeStereoSongWav(File(sessionDir, backingRel).also { it.parentFile?.mkdirs() }, sampleRate, durationMs)
            val backingId = doc.tracks.first { it.role == TrackRole.BACKING }.id
            doc = applied(
                reducer,
                doc,
                SessionCommand.ImportBacking(backingId, backingRel, durationMs.toLong(), true, "Sample song"),
            )

            val takeRel = SessionLayout.takeWav("booth-take")
            writeNoisyVocalWav(File(sessionDir, takeRel).also { it.parentFile?.mkdirs() }, sampleRate, durationMs)
            val leadId = doc.tracks.first { it.role == TrackRole.LEAD }.id
            doc = applied(
                reducer,
                doc,
                SessionCommand.CommitTake(leadId, takeRel, durationMs.toLong(), label = "Lead take"),
            )

            val engine = WavMixRenderEngine(root)
            val reports = mutableListOf<String>()
            for (preset in listOf(VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO, VocalPreset.RAW)) {
                val vocals = mixAndRead(engine, reducer, doc, VocalChain.preset(preset), root, ExportKind.VOCALS)
                val master = mixAndRead(engine, reducer, doc, VocalChain.preset(preset), root, ExportKind.MIX)
                val gap = rms(vocals.first, 0, sampleRate / 5)
                val voice = rms(vocals.first, sampleRate + sampleRate / 8, sampleRate + sampleRate / 3)
                val knock = peak(vocals.first, (sampleRate * 0.39).toInt(), (sampleRate * 0.43).toInt())
                val music = rms(master.first, 0, sampleRate / 5)
                val mixSeconds = master.first.size / sampleRate.toFloat()
                reports += "${preset.name}: vocalGap=${"%.5f".format(gap)} voice=${"%.4f".format(voice)} knock=${"%.4f".format(knock)} music=${"%.4f".format(music)} mixSec=${"%.2f".format(mixSeconds)}"
                assertTrue(
                    "${preset.name} mix must stay near song length, not slow-motion, sec=$mixSeconds",
                    mixSeconds in 3.5f..5.5f,
                )
                assertTrue("${preset.name} voice should survive, voice=$voice", voice > 0.035f)
                assertTrue("${preset.name} vocal gap should be quiet, gap=$gap voice=$voice", gap < voice * 0.28f)
                val knockLimit = if (preset == VocalPreset.STAGE || preset == VocalPreset.RAW) 0.20f else 0.16f
                assertTrue("${preset.name} knock should be tamed, knock=$knock", knock < knockLimit)
                assertTrue("${preset.name} song bed should remain, music=$music", music > 0.04f)
            }

            val shifted = doc.copy(
                musical = MusicalSettings(keyName = "D", mode = ScaleMode.MAJOR, keyShiftSemitones = 2),
            )
            val voiceChange = VocalChain.preset(VocalPreset.CLOSE).copy(
                preset = VocalPreset.CLOSE,
                pitchCorrect = 0.7f,
                thicken = 0.4f,
            )
            val changed = mixAndRead(engine, reducer, shifted, voiceChange, root, ExportKind.VOCALS)
            val gap2 = rms(changed.first, 0, sampleRate / 5)
            val voice2 = rms(changed.first, sampleRate + sampleRate / 8, sampleRate + sampleRate / 3)
            reports += "VOICE_CHANGE +2st: vocalGap=${"%.5f".format(gap2)} voice=${"%.4f".format(voice2)}"
            assertTrue("Changed voice should survive, voice=$voice2", voice2 > 0.03f)
            assertTrue("Changed voice gap should stay quiet, gap=$gap2 voice=$voice2", gap2 < voice2 * 0.30f)

            println(reports.joinToString(separator = "\n"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun applied(
        reducer: SessionReducer,
        doc: com.swaralab.domain.model.SessionDocument,
        command: SessionCommand,
    ) = (reducer.apply(doc, command) as SessionEdit.Applied).document

    private suspend fun mixAndRead(
        engine: WavMixRenderEngine,
        reducer: SessionReducer,
        doc: com.swaralab.domain.model.SessionDocument,
        chain: VocalChain,
        projectsRoot: File,
        kind: ExportKind,
    ): Pair<FloatArray, FloatArray> {
        val aimed = applied(reducer, doc, SessionCommand.SetVocalChain(chain))
        val result = engine.export(aimed, ExportRequest(PublishTarget.MASTER, kind))
        assertTrue("mix wrote, got $result", result is ExportResult.Written)
        val file = File(File(projectsRoot, aimed.id), (result as ExportResult.Written).relativePath)
        return readStereoWav(file, 48_000)
    }

    private fun writeStereoSongWav(file: File, sampleRate: Int, durationMs: Int) {
        val n = (sampleRate * durationMs) / 1000
        val left = FloatArray(n)
        val right = FloatArray(n)
        val chords = doubleArrayOf(130.81, 164.81, 196.00, 164.81)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val chord = chords[(i * 4 / n).coerceAtMost(3)]
            val bass = 0.22 * sin(2.0 * PI * (chord / 2.0) * t)
            val pad = 0.12 * sin(2.0 * PI * chord * t) + 0.08 * sin(2.0 * PI * chord * 1.5 * t)
            left[i] = (bass + pad).toFloat()
            right[i] = (bass * 0.9 + pad * 1.1).toFloat()
        }
        writeStereoWav(file, sampleRate, left, right)
    }

    private fun writeNoisyVocalWav(file: File, sampleRate: Int, durationMs: Int) {
        val n = (sampleRate * durationMs) / 1000
        val shorts = ShortArray(n)
        val notes = doubleArrayOf(220.0, 246.94, 261.63, 293.66)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val hiss = (((i * 1103515245 + 12345) and 0x7fff) / 32768.0 * 0.11 - 0.055)
            val rumble = 0.05 * sin(2.0 * PI * 58.0 * t)
            val knock = if (i in (sampleRate * 0.4).toInt()..(sampleRate * 0.42).toInt()) 0.35 else 0.0
            val singing = i > sampleRate / 2
            val melody = if (singing) {
                val note = notes[((i - sampleRate / 2) * 4 / (n / 2)).coerceAtMost(3)]
                val vib = 1.0 + 0.008 * sin(2.0 * PI * 5.4 * t)
                0.34 * sin(2.0 * PI * note * vib * t)
            } else {
                0.0
            }
            val sample = (melody + hiss + rumble + knock).coerceIn(-1.0, 1.0)
            shorts[i] = (sample * 30000.0).toInt().toShort()
        }
        writeMonoWav(file, sampleRate, shorts)
    }

    private fun writeMonoWav(file: File, sampleRate: Int, shorts: ShortArray) {
        val bytes = shorts.size * 2L
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            raf.write(wavHeader(sampleRate, channels = 1, bytes))
            val buf = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            shorts.forEach { buf.putShort(it) }
            raf.write(buf.array())
        }
    }

    private fun writeStereoWav(file: File, sampleRate: Int, left: FloatArray, right: FloatArray) {
        val n = minOf(left.size, right.size)
        val bytes = n * 4L
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            raf.write(wavHeader(sampleRate, channels = 2, bytes))
            val buf = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) {
                buf.putShort((left[i].coerceIn(-1f, 1f) * 22000f).toInt().toShort())
                buf.putShort((right[i].coerceIn(-1f, 1f) * 22000f).toInt().toShort())
            }
            raf.write(buf.array())
        }
    }

    private fun wavHeader(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
        val block = (channels * 2)
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + dataBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * block)
            putShort(block.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes.toInt())
        }.array()
    }

    private fun readStereoWav(file: File, sampleRate: Int): Pair<FloatArray, FloatArray> {
        val bytes = file.readBytes()
        val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceAtLeast(1)
        val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val frames = (bytes.size - 44) / (2 * channels)
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (i in 0 until frames) {
            left[i] = pcm.short / 32768f
            right[i] = if (channels > 1) pcm.short / 32768f else left[i]
        }
        return left to right
    }

    private fun rms(samples: FloatArray, start: Int, end: Int): Float {
        val from = start.coerceIn(0, samples.lastIndex)
        val to = end.coerceIn(from + 1, samples.size)
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    private fun peak(samples: FloatArray, start: Int, end: Int): Float {
        var peak = 0f
        val from = start.coerceIn(0, samples.lastIndex)
        val to = end.coerceIn(from + 1, samples.size)
        for (i in from until to) peak = maxOf(peak, abs(samples[i]))
        return peak
    }
}
