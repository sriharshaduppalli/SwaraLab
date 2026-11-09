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
 * Takes the reference song, dirties a vocal copy with hiss/rumble/hum,
 * then runs Mix & Save chips (Dry / Clean / Warm / Radio) the same way the app does.
 */
class NoisyReferenceMixSaveTest {
    @Test
    fun noisyReferenceTakeIsCleanOnEveryMixSaveChip() = runBlocking {
        val compare = File("C:/Users/sriha/Desktop/Applications/Music_app/SwaraLab/build/mix-compare")
        compare.mkdirs()
        val raw = File(compare, "raw_48k.wav")
        val root = Files.createTempDirectory("swaralab-noisy-mix").toFile()
        try {
            val (srcL, srcR, rate) = if (raw.isFile) readStereoWav(raw) else syntheticSong()
            val songSeconds = 8
            val songFrames = min(songSeconds * rate, min(srcL.size, srcR.size))
            val start = loudestStart(srcL, srcR, songFrames)
            val intro = (rate * 1.0).toInt()
            val frames = intro + songFrames
            val seconds = frames / rate
            val bedL = FloatArray(frames)
            val bedR = FloatArray(frames)
            val cleanVocal = FloatArray(frames)
            for (i in 0 until songFrames) {
                bedL[intro + i] = srcL[start + i] * 0.42f
                bedR[intro + i] = srcR[start + i] * 0.42f
                cleanVocal[intro + i] = (srcL[start + i] + srcR[start + i]) * 0.42f
            }
            val noisyVocal = addBoothNoise(cleanVocal, rate)
            writeMonoWav(File(compare, "noisy_take.wav"), noisyVocal, rate)
            writeStereoWav(File(compare, "noisy_bed.wav"), bedL, bedR, rate)

            val reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            var doc = reducer.create(SessionKind.SONG, "Noisy Mix Review")
            val sessionDir = File(root, doc.id).apply { mkdirs() }
            val backingRel = SessionLayout.backingFile("noisy-bed.wav")
            writeStereoWav(File(sessionDir, backingRel).also { it.parentFile?.mkdirs() }, bedL, bedR, rate)
            val backingId = doc.tracks.first { it.role == TrackRole.BACKING }.id
            doc = applied(reducer, doc, SessionCommand.ImportBacking(backingId, backingRel, seconds * 1000L, true, "Bed"))
            val takeRel = SessionLayout.takeWav("noisy-take")
            writeMonoWav(File(sessionDir, takeRel).also { it.parentFile?.mkdirs() }, noisyVocal, rate)
            val leadId = doc.tracks.first { it.role == TrackRole.LEAD }.id
            doc = applied(reducer, doc, SessionCommand.CommitTake(leadId, takeRel, seconds * 1000L, label = "Noisy take"))

            val engine = WavMixRenderEngine(root)
            val noisyGap = rms(noisyVocal, 0, intro)
            val noisyHiss = hiss(noisyVocal, 0, intro)
            val reports = mutableListOf<String>()
            for (preset in listOf(VocalPreset.RAW, VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO, VocalPreset.BRIGHT)) {
                val chained = applied(reducer, doc, SessionCommand.SetVocalChain(VocalChain.preset(preset)))
                val vocals = export(engine, root, chained, ExportKind.VOCALS)
                val mix = export(engine, root, chained, ExportKind.MIX)
                writeStereoWav(File(compare, "noisy_mix_${preset.name.lowercase()}_vocals.wav"), vocals.left, vocals.right, rate)
                writeStereoWav(File(compare, "noisy_mix_${preset.name.lowercase()}_master.wav"), mix.left, mix.right, rate)
                val gap = rms(vocals.left, 0, intro)
                val voice = rms(vocals.left, intro + rate / 6, intro + (rate * 2) / 3)
                val mixRms = rms(mix.left)
                val vocalHiss = hiss(vocals.left, 0, intro)
                val mixSec = vocals.left.size / rate.toFloat()
                reports += "${preset.name}: gap=${"%.5f".format(gap)} voice=${"%.4f".format(voice)} " +
                    "hiss=${"%.4f".format(vocalHiss)} mix=${"%.4f".format(mixRms)} sec=${"%.2f".format(mixSec)}"
                assertTrue("${preset.name} must keep song time, sec=$mixSec", mixSec in 8.4f..10.5f)
                assertTrue("${preset.name} voice should survive, voice=$voice", voice > 0.016f)
                assertTrue(
                    "${preset.name} should cut take hiss, noisy=$noisyGap mixed=$gap",
                    gap < noisyGap * 0.45f,
                )
                assertTrue(
                    "${preset.name} air hiss should drop, noisy=$noisyHiss mixed=$vocalHiss",
                    vocalHiss < noisyHiss * 0.72f,
                )
                assertTrue("${preset.name} Mix & Save should still have the bed, mix=$mixRms", mixRms > 0.02f)
            }
            println("noisyGap=${"%.5f".format(noisyGap)} noisyHiss=${"%.4f".format(noisyHiss)}\n" + reports.joinToString("\n"))
        } finally {
            root.deleteRecursively()
        }
    }

    private data class Stereo(val left: FloatArray, val right: FloatArray)

    private fun applied(reducer: SessionReducer, doc: SessionDocument, command: SessionCommand) =
        (reducer.apply(doc, command) as SessionEdit.Applied).document

    private suspend fun export(
        engine: WavMixRenderEngine,
        root: File,
        doc: SessionDocument,
        kind: ExportKind,
    ): Stereo {
        val result = engine.export(doc, ExportRequest(PublishTarget.MASTER, kind))
        assertTrue("$kind wrote, got $result", result is ExportResult.Written)
        val file = File(File(root, doc.id), (result as ExportResult.Written).relativePath)
        val (left, right, _) = readStereoWav(file)
        return Stereo(left, right)
    }

    private fun addBoothNoise(clean: FloatArray, rate: Int): FloatArray {
        val out = FloatArray(clean.size)
        for (i in clean.indices) {
            val t = i / rate.toDouble()
            val white = (((i * 1103515245 + 12345) and 0x7fff) / 32768.0 * 0.08 - 0.04)
            val hiss = 0.045 * sin(2.0 * PI * 8_400.0 * t) * (((i * 214013 + 2531011) and 0xff) / 255.0 - 0.5)
            val rumble = 0.035 * sin(2.0 * PI * 58.0 * t)
            val hum = 0.018 * sin(2.0 * PI * 50.0 * t)
            out[i] = (clean[i] + white + hiss + rumble + hum).toFloat().coerceIn(-1f, 1f)
        }
        return out
    }

    private fun loudestStart(left: FloatArray, right: FloatArray, window: Int): Int {
        val n = min(left.size, right.size)
        if (n <= window) return 0
        val hop = (window / 4).coerceAtLeast(1)
        var best = 0
        var bestE = -1.0
        var i = 0
        while (i + window <= n) {
            var e = 0.0
            var j = i
            while (j < i + window) {
                e += left[j] * left[j] + right[j] * right[j]
                j += 32
            }
            if (e > bestE) {
                bestE = e
                best = i
            }
            i += hop
        }
        return best
    }

    private fun syntheticSong(): Triple<FloatArray, FloatArray, Int> {
        val rate = 48_000
        val n = rate * 12
        val left = FloatArray(n)
        val right = FloatArray(n)
        for (i in 0 until n) {
            val t = i / rate.toDouble()
            left[i] = (0.22 * sin(2 * PI * 220.0 * t) + 0.10 * sin(2 * PI * 330.0 * t)).toFloat()
            right[i] = (0.18 * sin(2 * PI * 165.0 * t)).toFloat()
        }
        return Triple(left, right, rate)
    }

    private fun rms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val from = start.coerceIn(0, samples.lastIndex.coerceAtLeast(0))
        val to = end.coerceIn(from + 1, samples.size)
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    private fun hiss(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val from = start.coerceIn(0, (samples.size - 2).coerceAtLeast(0))
        val to = end.coerceIn(from + 2, samples.size)
        var sum = 0.0
        for (i in from + 1 until to) {
            val d = samples[i] - samples[i - 1]
            sum += d * d
        }
        return sqrt(sum / (to - from - 1)).toFloat()
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
