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
import com.swaralab.domain.model.withTarget
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
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mix & Save booth: same graph as the app, driven by the decoded raw song.
 * Covers track volumes, vocal sync, Hear the mix, Mix backing & vocals,
 * stems, and target-format loudness.
 */
class MixSaveOptionsSampleTest {
    @Test
    fun mixSaveControlsWorkOnSampleRawSong() = runBlocking {
        val raw = File("C:/Users/sriha/Desktop/Applications/Music_app/SwaraLab/build/mix-compare/raw_48k.wav")
        val root = Files.createTempDirectory("swaralab-mix-save").toFile()
        val fixture = if (raw.isFile) raw else File(root, "tone.wav").also { writeToneSong(it) }
        val (rawL, rawR, rate) = readStereoWav(fixture)
        val seconds = 8
        val frames = seconds * rate
        val outDir = (if (raw.isFile) raw.parentFile else root)
        try {
            val reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            var doc = reducer.create(SessionKind.SONG, "Sample Raw Mix Save")
            val sessionDir = File(root, doc.id).apply { mkdirs() }

            val backingRel = SessionLayout.backingFile("raw-bed.wav")
            writeStereoSlice(File(sessionDir, backingRel).also { it.parentFile?.mkdirs() }, rawL, rawR, rate, frames, bed = 0.42f)
            val backingId = doc.tracks.first { it.role == TrackRole.BACKING }.id
            doc = applied(reducer, doc, SessionCommand.ImportBacking(backingId, backingRel, seconds * 1000L, true, "Raw bed"))

            val takeRel = SessionLayout.takeWav("raw-take")
            writeMonoSlice(File(sessionDir, takeRel).also { it.parentFile?.mkdirs() }, rawL, rawR, rate, frames)
            val leadId = doc.tracks.first { it.role == TrackRole.LEAD }.id
            doc = applied(reducer, doc, SessionCommand.CommitTake(leadId, takeRel, seconds * 1000L, label = "Raw take"))
            doc = applied(reducer, doc, SessionCommand.SetVocalChain(VocalChain.preset(VocalPreset.CLOSE)))

            val engine = WavMixRenderEngine(root)

            val heard = engine.preview(doc, 12_000L)
            assertTrue("Hear the mix should render PCM", heard != null && heard.left.isNotEmpty())
            val hearSec = heard!!.left.size / rate.toFloat()
            assertTrue("Hear the mix must stay real-time, not slow-motion, sec=$hearSec", hearSec in 7.5f..8.6f)
            writeStereoSlice(File(outDir, "hear_mix_loop.wav"), heard.left, heard.right, rate, heard.left.size)

            val mix = exportWav(engine, root, doc, ExportKind.MIX)
            val vocals = exportWav(engine, root, doc, ExportKind.VOCALS)
            val backing = exportWav(engine, root, doc, ExportKind.BACKING)
            writeStereoSlice(File(outDir, "mixsave_master.wav"), mix.left, mix.right, rate, mix.left.size)
            writeStereoSlice(File(outDir, "mixsave_vocals.wav"), vocals.left, vocals.right, rate, vocals.left.size)
            writeStereoSlice(File(outDir, "mixsave_backing.wav"), backing.left, backing.right, rate, backing.left.size)
            val mixRms = rms(mix.left)
            val vocalRms = rms(vocals.left)
            val backingRms = rms(backing.left)
            assertTrue("Mix backing & vocals should keep the song, mix=$mixRms", mixRms > 0.04f)
            assertTrue("Vocals stem should keep the raw take, vocal=$vocalRms", vocalRms > 0.03f)
            assertTrue("Backing stem should keep the bed, backing=$backingRms", backingRms > 0.02f)
            assertTrue("Master mix should be louder than vocals alone, mix=$mixRms vocal=$vocalRms", mixRms > vocalRms * 1.05f)

            val loudVocal = exportWav(engine, root, withGain(reducer, doc, leadId, 6f), ExportKind.VOCALS)
            val quietVocal = exportWav(engine, root, withGain(reducer, doc, leadId, -12f), ExportKind.VOCALS)
            val loudVocalRms = rms(loudVocal.left)
            val quietVocalRms = rms(quietVocal.left)
            assertTrue(
                "Track volume should move the vocal, +6dB=$loudVocalRms -12dB=$quietVocalRms",
                loudVocalRms > quietVocalRms * 1.6f,
            )

            val mutedBed = exportWav(engine, root, withMute(reducer, doc, backingId, mute = true), ExportKind.MIX)
            val mutedVoice = exportWav(engine, root, withMute(reducer, doc, leadId, mute = true), ExportKind.MIX)
            val mutedBedRms = rms(mutedBed.left)
            val mutedVoiceRms = rms(mutedVoice.left)
            assertTrue("Mute backing should still render a take, mutedBed=$mutedBedRms", mutedBedRms > 0.02f)
            assertTrue("Mute vocal should still render a bed, mutedVoice=$mutedVoiceRms", mutedVoiceRms > 0.02f)
            assertTrue(
                "Mute backing and mute vocal should not sound the same, mutedBed=$mutedBedRms mutedVoice=$mutedVoiceRms",
                abs(mutedBedRms - mutedVoiceRms) > 0.004f,
            )

            val late = exportWav(engine, root, withSync(reducer, doc, 120.0), ExportKind.VOCALS)
            val onTimeOnset = onset(vocals.left, 0.02f)
            val lateOnset = onset(late.left, 0.02f)
            val shiftMs = (lateOnset - onTimeOnset) * 1000f / rate
            assertTrue("Vocal sync +120 ms should delay the take, shift=${shiftMs}ms", shiftMs in 80f..160f)
            assertTrue("Synced vocal should still arrive, onset=$lateOnset", lateOnset < late.left.size / 2)

            val masterHear = engine.preview(doc.copy(publish = doc.publish.withTarget(PublishTarget.MASTER)), 8_000L)!!
            val reelsHear = engine.preview(doc.copy(publish = doc.publish.withTarget(PublishTarget.REELS)), 8_000L)!!
            val whatsHear = engine.preview(doc.copy(publish = doc.publish.withTarget(PublishTarget.WHATSAPP)), 8_000L)!!
            val shortsHear = engine.preview(doc.copy(publish = doc.publish.withTarget(PublishTarget.SHORTS)), 8_000L)!!
            val masterRms = rms(masterHear.left)
            val reelsRms = rms(reelsHear.left)
            val whatsRms = rms(whatsHear.left)
            val shortsRms = rms(shortsHear.left)
            assertTrue("Share targets should all render, master=$masterRms reels=$reelsRms shorts=$shortsRms wa=$whatsRms",
                masterRms > 0.02f && reelsRms > 0.02f && shortsRms > 0.02f && whatsRms > 0.02f)
            assertTrue("Shorts should stay near Reels, shorts=$shortsRms reels=$reelsRms",
                abs(shortsRms - reelsRms) < (reelsRms * 0.15f).coerceAtLeast(0.01f))

            println(
                """
                hearSec=$hearSec
                mix=$mixRms vocals=$vocalRms backing=$backingRms
                vocal+6=$loudVocalRms vocal-12=$quietVocalRms
                muteBacking=$mutedBedRms muteVocal=$mutedVoiceRms
                syncShiftMs=$shiftMs
                target master=$masterRms reels=$reelsRms shorts=$shortsRms whatsapp=$whatsRms
                """.trimIndent(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun applied(reducer: SessionReducer, doc: SessionDocument, command: SessionCommand) =
        (reducer.apply(doc, command) as SessionEdit.Applied).document

    private fun withGain(reducer: SessionReducer, doc: SessionDocument, trackId: String, gainDb: Float) =
        applied(reducer, doc, SessionCommand.SetTrackMix(trackId, gainDb, 0f, mute = false, solo = false))

    private fun withMute(reducer: SessionReducer, doc: SessionDocument, trackId: String, mute: Boolean) =
        applied(reducer, doc, SessionCommand.SetTrackMix(trackId, 0f, 0f, mute = mute, solo = false))

    private fun withSync(reducer: SessionReducer, doc: SessionDocument, compensationMs: Double) =
        applied(reducer, doc, SessionCommand.SetLatency(0.0, compensationMs))

    private suspend fun exportWav(
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

    private data class Stereo(val left: FloatArray, val right: FloatArray)

    private fun onset(samples: FloatArray, threshold: Float): Int {
        for (i in samples.indices) {
            if (abs(samples[i]) >= threshold) return i
        }
        return samples.size
    }

    private fun rms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val from = start.coerceIn(0, samples.lastIndex.coerceAtLeast(0))
        val to = end.coerceIn(from + 1, samples.size)
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        return sqrt(sum / (to - from)).toFloat()
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

    private fun writeToneSong(file: File) {
        val rate = 48_000
        val frames = rate * 8
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (i in 0 until frames) {
            val t = i / rate.toDouble()
            left[i] = (0.28 * sin(2 * PI * 220.0 * t) + 0.12 * sin(2 * PI * 330.0 * t)).toFloat()
            right[i] = (0.22 * sin(2 * PI * 165.0 * t)).toFloat()
        }
        writeStereoSlice(file, left, right, rate, frames)
    }

    private fun writeMonoSlice(file: File, left: FloatArray, right: FloatArray, rate: Int, frames: Int) {
        val n = minOf(frames, left.size, right.size)
        val data = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        header(data, rate, channels = 1, dataBytes = n * 2)
        for (i in 0 until n) {
            val m = ((left[i] + right[i]) * 0.5f).coerceIn(-1f, 1f)
            data.putShort((m * 32767f).toInt().toShort())
        }
        file.writeBytes(data.array())
    }

    private fun writeStereoSlice(
        file: File,
        left: FloatArray,
        right: FloatArray,
        rate: Int,
        frames: Int,
        bed: Float = 1f,
    ) {
        val n = minOf(frames, left.size, right.size)
        val data = ByteBuffer.allocate(44 + n * 4).order(ByteOrder.LITTLE_ENDIAN)
        header(data, rate, channels = 2, dataBytes = n * 4)
        for (i in 0 until n) {
            data.putShort((left[i].coerceIn(-1f, 1f) * bed * 32767f).toInt().toShort())
            data.putShort((right[i].coerceIn(-1f, 1f) * bed * 32767f).toInt().toShort())
        }
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
