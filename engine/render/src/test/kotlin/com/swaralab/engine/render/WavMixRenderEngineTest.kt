package com.swaralab.engine.render

import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.time.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

class WavMixRenderEngineTest {
    @Test
    fun sampleBackingAndRecordedVocalMixWithCustomSlidersAndVerifyLiveGraph() = runBlocking {
        val projectsRoot = Files.createTempDirectory("swaralab-mix-test").toFile()
        try {
            val reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            var doc = reducer.create(SessionKind.SONG, "Sample Riyaz Song")
            val sessionDir = File(projectsRoot, doc.id).apply { mkdirs() }

            // 1. Write sample backing drone WAV (C3 + G3)
            val backingRel = SessionLayout.backingFile("drone.wav")
            val backingFile = File(sessionDir, backingRel).apply { parentFile?.mkdirs() }
            writeTestToneWav(backingFile, sampleRate = 48_000, freqHz = 130.81, durationMs = 1_500)

            val backingTrackId = doc.tracks.first { it.role == TrackRole.BACKING }.id
            doc = (reducer.apply(
                doc,
                SessionCommand.ImportBacking(
                    trackId = backingTrackId,
                    relativePath = backingRel,
                    durationMs = 1_500,
                    rightsConfirmed = true,
                    note = "Tanpura Drone",
                ),
            ) as SessionEdit.Applied).document

            // 2. Write sample vocal take WAV (A4 = 440 Hz) and verify live graph analyzer
            val takeRel = SessionLayout.takeWav("take-1")
            val takeFile = File(sessionDir, takeRel).apply { parentFile?.mkdirs() }
            val vocalSamples = writeTestToneWav(takeFile, sampleRate = 48_000, freqHz = 440.0, durationMs = 1_200)

            val metrics = VocalSignalAnalyzer.analyzeFrame(
                samples = vocalSamples.copyOfRange(0, 2048),
                length = 2048,
                sampleRate = 48_000,
                previous = VocalMetrics(),
            )
            assertEquals("A4", metrics.noteName)
            assertEquals("Dha", metrics.swaraName)
            assertTrue("Expected non-zero vocal mid level", metrics.midLevel > 0.2f)

            val leadTrackId = doc.tracks.first { it.role == TrackRole.LEAD }.id
            doc = (reducer.apply(
                doc,
                SessionCommand.CommitTake(
                    trackId = leadTrackId,
                    relativePath = takeRel,
                    durationMs = 1_200,
                    label = "Part 1 · Lead · Take 1",
                ),
            ) as SessionEdit.Applied).document

            // 3. Apply custom sliders (reverb, echo, compression, denoise, ducking) and sync offset
            doc = (reducer.apply(
                doc,
                SessionCommand.SetVocalChain(
                    VocalChain(
                        preset = VocalPreset.CUSTOM,
                        reverb = 0.45f,
                        delay = 0.25f,
                        compression = 0.40f,
                        denoise = 0.30f,
                        backingDuckDb = 3.0f,
                    ),
                ),
            ) as SessionEdit.Applied).document

            // 4. Mix backing + vocal into master WAV
            val engine = WavMixRenderEngine(projectsRoot)
            val result = engine.export(doc, ExportRequest(PublishTarget.MASTER))
            assertTrue("Expected ExportResult.Written, got $result", result is ExportResult.Written)
            val written = result as ExportResult.Written
            val exportedFile = File(sessionDir, written.relativePath)
            assertTrue("Exported WAV file must exist and have audio payload", exportedFile.isFile && exportedFile.length() > 48_000L)
        } finally {
            projectsRoot.deleteRecursively()
        }
    }

    private fun writeTestToneWav(
        file: File,
        sampleRate: Int,
        freqHz: Double,
        durationMs: Int,
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs) / 1000
        val shorts = ShortArray(totalSamples)
        val totalBytes = totalSamples * 2L
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((36L + totalBytes).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(totalBytes.toInt())
            }
            raf.write(header.array())
            val buf = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until totalSamples) {
                val sample = (sin(2.0 * PI * freqHz * i / sampleRate) * 14_000.0).toInt().toShort()
                shorts[i] = sample
                buf.putShort(sample)
            }
            raf.write(buf.array())
        }
        return shorts
    }
}
