package com.swaralab.engine.render

import com.swaralab.domain.audio.MixTestSignals
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Eight clean/noisy booth takes through Mix & Save chips plus timeline
 * edit controls (trim, fade, gain, mute, pan, sync, per-layer chain).
 */
class MixEditFeatureSweepTest {
    @Test
    fun batchTakesMixAndEditWithoutPumpingHiss() = runBlocking {
        val fixtures = MixTestSignals.ensureBatch()
        val repo = MixTestSignals.fixtureDir().parentFile?.parentFile ?: MixTestSignals.fixtureDir()
        val compare = File(repo, "build/mix-compare/batch").apply { mkdirs() }
        val root = Files.createTempDirectory("swaralab-mix-edit-sweep").toFile()
        val report = StringBuilder()
        try {
            val rate = MixTestSignals.SAMPLE_RATE
            val gapEnd = (rate * MixTestSignals.BATCH_GAP_SEC).toInt()
            val reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            val engine = WavMixRenderEngine(root)
            val chips = listOf(VocalPreset.RAW, VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO, VocalPreset.BRIGHT)

            for (recipe in MixTestSignals.batchRecipes) {
                val cleanFile = File(fixtures, "${recipe.id}_clean.wav")
                val noisyFile = File(fixtures, "${recipe.id}_noisy.wav")
                val (noisy, _) = MixTestSignals.readMonoWav(noisyFile)
                val durationMs = (noisy.size * 1000L) / rate
                val sourceGap = rms(noisy, 0, gapEnd)
                val sourceTone = rms(noisy, gapEnd + rate / 6, gapEnd + rate / 6 + rate / 2)

                val cleanDoc = sessionWithTake(reducer, root, cleanFile, "clean-${recipe.id}", durationMs)
                val noisyDoc = sessionWithTake(reducer, root, noisyFile, "noisy-${recipe.id}", durationMs)

                for (preset in chips) {
                    val cleanMix = export(engine, root, withChain(reducer, cleanDoc, preset), ExportKind.VOCALS)
                    val noisyMix = export(engine, root, withChain(reducer, noisyDoc, preset), ExportKind.VOCALS)
                    writeStereo(File(compare, "${recipe.id}_${preset.name.lowercase()}_clean.wav"), cleanMix, rate)
                    writeStereo(File(compare, "${recipe.id}_${preset.name.lowercase()}_noisy.wav"), noisyMix, rate)
                    val cleanTone = rms(cleanMix, gapEnd + rate / 6, gapEnd + rate / 6 + rate / 2)
                    val mixedGap = rms(noisyMix, 0, gapEnd)
                    val mixedTone = rms(noisyMix, gapEnd + rate / 6, gapEnd + rate / 6 + rate / 2)
                    report.appendLine(
                        "${recipe.id} ${preset.name}: srcGap=${"%.4f".format(sourceGap)} mixGap=${"%.4f".format(mixedGap)} " +
                            "srcTone=${"%.4f".format(sourceTone)} mixTone=${"%.4f".format(mixedTone)} cleanTone=${"%.4f".format(cleanTone)}",
                    )
                    assertTrue("${recipe.id} ${preset.name} clean note should survive, rms=$cleanTone", cleanTone > 0.06f)
                    if (preset != VocalPreset.RAW) {
                        assertTrue(
                            "${recipe.id} ${preset.name} should remove gap noise, src=$sourceGap mix=$mixedGap",
                            mixedGap < sourceGap * 0.52f,
                        )
                    }
                    assertTrue(
                        "${recipe.id} ${preset.name} should keep the sung note, src=$sourceTone mix=$mixedTone",
                        mixedTone > sourceTone * 0.26f && mixedTone > 0.022f,
                    )
                }
            }

            val recipe = MixTestSignals.batchRecipes.first()
            val take = File(fixtures, "${recipe.id}_noisy.wav")
            val (pcm, _) = MixTestSignals.readMonoWav(take)
            val durationMs = (pcm.size * 1000L) / rate
            val sourceGap = rms(pcm, 0, gapEnd)
            var doc = sessionWithBedAndTake(reducer, root, take, durationMs, rate)
            val leadId = doc.tracks.first { it.role == TrackRole.LEAD }.id
            val backingId = doc.tracks.first { it.role == TrackRole.BACKING }.id
            val clipId = doc.tracks.first { it.role == TrackRole.LEAD }.clips.single().id
            doc = withChain(reducer, doc, VocalPreset.CLOSE)

            val base = export(engine, root, doc, ExportKind.VOCALS)
            val mix = export(engine, root, doc, ExportKind.MIX)
            val vocals = export(engine, root, doc, ExportKind.VOCALS)
            val backing = export(engine, root, doc, ExportKind.BACKING)
            assertTrue("Mix should keep the bed, mix=${rms(mix)}", rms(mix) > 0.03f)
            assertTrue("Vocals stem should keep the take, v=${rms(vocals)}", rms(vocals) > 0.02f)
            assertTrue("Backing stem should keep the bed, b=${rms(backing)}", rms(backing) > 0.02f)

            val loud = export(engine, root, withMix(reducer, doc, leadId, gainDb = 6f), ExportKind.VOCALS)
            val quiet = export(engine, root, withMix(reducer, doc, leadId, gainDb = -12f), ExportKind.VOCALS)
            assertTrue("Track volume should move the take, +6=${rms(loud)} -12=${rms(quiet)}", rms(loud) > rms(quiet) * 1.5f)

            val mutedVoice = export(engine, root, withMix(reducer, doc, leadId, mute = true), ExportKind.MIX)
            val mutedBed = export(engine, root, withMix(reducer, doc, backingId, mute = true), ExportKind.MIX)
            assertTrue("Mute vocal vs mute bed should differ", abs(rms(mutedVoice) - rms(mutedBed)) > 0.004f)

            val (leftL, leftR) = stereoPair(engine, root, withMix(reducer, doc, leadId, pan = -1f))
            val (rightL, rightR) = stereoPair(engine, root, withMix(reducer, doc, leadId, pan = 1f))
            assertTrue("Pan left should bias L, L=${rms(leftL)} R=${rms(leftR)}", rms(leftL) > rms(leftR) * 1.2f)
            assertTrue("Pan right should bias R, L=${rms(rightL)} R=${rms(rightR)}", rms(rightR) > rms(rightL) * 1.2f)

            fun stereoWidth(pair: Pair<FloatArray, FloatArray>): Float {
                var side = 0.0
                var mid = 0.0
                val n = min(pair.first.size, pair.second.size)
                for (i in 0 until n) {
                    val m = (pair.first[i] + pair.second[i]) * 0.5f
                    val s = (pair.first[i] - pair.second[i]) * 0.5f
                    mid += m * m
                    side += s * s
                }
                return if (mid <= 1e-12) 0f else sqrt((side / mid).toFloat())
            }
            val rawWidth = stereoWidth(stereoPair(engine, root, withChain(reducer, doc, VocalPreset.RAW)))
            val hallWidth = stereoWidth(stereoPair(engine, root, withChain(reducer, doc, VocalPreset.STAGE)))
            assertTrue("Stage mix should be wider than Dry, dry=$rawWidth hall=$hallWidth", hallWidth > rawWidth * 1.15f)

            val mutedLead = withMix(reducer, doc, leadId, mute = true)
            val bedPanResult = engine.export(
                withMix(reducer, mutedLead, backingId, pan = 1f),
                ExportRequest(PublishTarget.MASTER, ExportKind.MIX),
            )
            assertTrue("panned bed wrote, got $bedPanResult", bedPanResult is ExportResult.Written)
            val bedFile = File(File(root, mutedLead.id), (bedPanResult as ExportResult.Written).relativePath)
            val (bedL, bedR, _) = readStereo(bedFile)
            assertTrue("Backing pan right should bias R, L=${rms(bedL)} R=${rms(bedR)}", rms(bedR) > rms(bedL) * 1.08f)

            val late = export(engine, root, applied(reducer, doc, SessionCommand.SetLatency(0.0, 80.0)), ExportKind.VOCALS)
            val shift = (onset(late, 0.03f) - onset(base, 0.03f)) * 1000f / rate
            assertTrue("Vocal sync +80 ms should delay the take, shift=$shift", shift in 50f..110f)

            val trimmed = applied(
                reducer,
                doc,
                SessionCommand.UpdateClip(clipId, sourceInMs = 500L, durationMs = durationMs - 500L, fadeInMs = 80L, fadeOutMs = 120L),
            )
            val trimmedMix = export(engine, root, withChain(reducer, trimmed, VocalPreset.CLOSE), ExportKind.VOCALS)
            val trimHead = rms(trimmedMix, 0, rate / 5)
            val trimTone = rms(trimmedMix, rate / 5, rate / 5 + rate / 3)
            assertTrue(
                "Trim should skip the noisy intro and start on the note, head=$trimHead tone=$trimTone",
                trimHead > 0.08f && trimTone > 0.08f,
            )

            val layerRadio = applied(
                reducer,
                doc,
                SessionCommand.SetTrackChain(leadId, VocalChain.preset(VocalPreset.RADIO)),
            )
            val layerMix = export(engine, root, layerRadio, ExportKind.VOCALS)
            val layerGap = rms(layerMix, 0, gapEnd)
            assertTrue("Per-layer Radio should still gate the gap, gap=$layerGap src=$sourceGap", layerGap < sourceGap * 0.52f)

            val master = engine.preview(doc.copy(publish = doc.publish.withTarget(PublishTarget.MASTER)), 3_000L)!!
            val reels = engine.preview(doc.copy(publish = doc.publish.withTarget(PublishTarget.REELS)), 3_000L)!!
            assertTrue("Hear the mix should render, master=${rms(master.left)} reels=${rms(reels.left)}", rms(master.left) > 0.02f && rms(reels.left) > 0.02f)

            File(compare, "sweep_report.txt").writeText(report.toString())
            println(report.toString())
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
        kind: SessionKind = SessionKind.PRACTICE,
    ): SessionDocument {
        var doc = reducer.create(kind, title)
        val sessionDir = File(root, doc.id).apply { mkdirs() }
        val takeRel = SessionLayout.takeWav(take.nameWithoutExtension)
        take.copyTo(File(sessionDir, takeRel).also { it.parentFile?.mkdirs() }, overwrite = true)
        val leadId = doc.tracks.first { it.role == TrackRole.LEAD }.id
        return applied(reducer, doc, SessionCommand.CommitTake(leadId, takeRel, durationMs, label = title))
    }

    private fun sessionWithBedAndTake(
        reducer: SessionReducer,
        root: File,
        take: File,
        durationMs: Long,
        rate: Int,
    ): SessionDocument {
        var doc = sessionWithTake(reducer, root, take, "edit-sweep", durationMs, kind = SessionKind.SONG)
        val sessionDir = File(root, doc.id)
        val n = ((durationMs * rate) / 1000L).toInt()
        val bedL = FloatArray(n) { i -> 0.12f * sin(2.0 * PI * 110.0 * i / rate).toFloat() }
        val bedR = bedL.copyOf()
        val backingRel = SessionLayout.backingFile("sweep-bed.wav")
        writeStereo(File(sessionDir, backingRel).also { it.parentFile?.mkdirs() }, bedL, rate, right = bedR)
        val backingId = doc.tracks.first { it.role == TrackRole.BACKING }.id
        return applied(reducer, doc, SessionCommand.ImportBacking(backingId, backingRel, durationMs, true, "Bed"))
    }

    private fun withChain(reducer: SessionReducer, doc: SessionDocument, preset: VocalPreset) =
        applied(reducer, doc, SessionCommand.SetVocalChain(VocalChain.preset(preset)))

    private fun withMix(
        reducer: SessionReducer,
        doc: SessionDocument,
        trackId: String,
        gainDb: Float = 0f,
        mute: Boolean = false,
        pan: Float = 0f,
    ) = applied(reducer, doc, SessionCommand.SetTrackMix(trackId, gainDb, 0f, mute, solo = false, pan = pan))

    private fun applied(reducer: SessionReducer, doc: SessionDocument, command: SessionCommand) =
        (reducer.apply(doc, command) as SessionEdit.Applied).document

    private suspend fun export(
        engine: WavMixRenderEngine,
        root: File,
        doc: SessionDocument,
        kind: ExportKind,
    ): FloatArray {
        val result = engine.export(doc, ExportRequest(PublishTarget.MASTER, kind))
        assertTrue("$kind wrote, got $result", result is ExportResult.Written)
        val file = File(File(root, doc.id), (result as ExportResult.Written).relativePath)
        return readStereo(file).first
    }

    private suspend fun stereoPair(
        engine: WavMixRenderEngine,
        root: File,
        doc: SessionDocument,
    ): Pair<FloatArray, FloatArray> {
        val result = engine.export(doc, ExportRequest(PublishTarget.MASTER, ExportKind.VOCALS))
        assertTrue("stereo wrote, got $result", result is ExportResult.Written)
        val file = File(File(root, doc.id), (result as ExportResult.Written).relativePath)
        val (l, r, _) = readStereo(file)
        return l to r
    }

    private fun onset(samples: FloatArray, threshold: Float): Int {
        for (i in samples.indices) if (abs(samples[i]) >= threshold) return i
        return samples.size
    }

    private fun rms(samples: FloatArray, start: Int = 0, end: Int = samples.size): Float {
        val from = start.coerceIn(0, samples.lastIndex.coerceAtLeast(0))
        val to = end.coerceIn(from + 1, samples.size)
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    private fun readStereo(file: File): Triple<FloatArray, FloatArray, Int> {
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

    private fun writeStereo(file: File, left: FloatArray, rate: Int, right: FloatArray = left) {
        val n = min(left.size, right.size)
        val data = ByteBuffer.allocate(44 + n * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray())
        data.putInt(36 + n * 4)
        data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray())
        data.putInt(16)
        data.putShort(1)
        data.putShort(2)
        data.putInt(rate)
        data.putInt(rate * 4)
        data.putShort(4)
        data.putShort(16)
        data.put("data".toByteArray())
        data.putInt(n * 4)
        for (i in 0 until n) {
            data.putShort((left[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            data.putShort((right[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        file.parentFile?.mkdirs()
        file.writeBytes(data.array())
    }
}
