package com.swaralab.engine.render

import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Mixes all recorded vocal takes in the session, applies the selected vocal preset
 * (Studio, Hall, Bright, or Natural), normalizes peak level, and writes the mixed WAV
 * into `exports/{target}.wav`.
 */
class WavMixRenderEngine(
    private val projectsRoot: File,
) : RenderEngine {
    override suspend fun export(document: SessionDocument, request: ExportRequest): ExportResult =
        withContext(Dispatchers.IO) {
            val sessionFolder = File(projectsRoot, document.id)
            val voiceClips = document.tracks
                .filter { it.role != TrackRole.BACKING && !it.mute }
                .flatMap { it.clips }

            val wavFiles = voiceClips
                .map { File(sessionFolder, it.sourceRelativePath) }
                .filter { it.isFile && it.length() > 44L }

            if (wavFiles.isEmpty()) {
                return@withContext ExportResult.Waiting(
                    "Record at least one vocal take on the Sing stage before exporting.",
                )
            }

            val sampleRate = 48_000
            val decodedTracks = wavFiles.mapNotNull { readPcm16Mono(it) }.filter { it.isNotEmpty() }
            if (decodedTracks.isEmpty()) {
                return@withContext ExportResult.Waiting(
                    "No valid vocal audio found in this song yet.",
                )
            }

            val maxSamples = decodedTracks.maxOf { it.size }
            val mixed = FloatArray(maxSamples)
            for (samples in decodedTracks) {
                for (i in samples.indices) {
                    mixed[i] += samples[i] / 32768f
                }
            }

            applyVocalPreset(mixed, sampleRate, document.chain.preset)
            normalizePeak(mixed, targetPeak = 0.89f)

            val safeTitle = document.title
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "song" }
            val fileName = "$safeTitle-${request.target.name.lowercase()}.wav"
            val relativePath = SessionLayout.exportFile(fileName)
            val outFile = File(sessionFolder, relativePath)
            outFile.parentFile?.mkdirs()

            writePcm16MonoWav(outFile, sampleRate, mixed)
            val durationMs = ((mixed.size * 1000L) / sampleRate).coerceAtLeast(1L)
            ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
        }

    private fun readPcm16Mono(file: File): ShortArray? = runCatching {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return null
        val payloadBytes = bytes.size - 44
        val count = payloadBytes / 2
        val shorts = ShortArray(count)
        ByteBuffer.wrap(bytes, 44, count * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        shorts
    }.getOrNull()

    private fun applyVocalPreset(samples: FloatArray, sampleRate: Int, preset: VocalPreset) {
        when (preset) {
            VocalPreset.RAW, VocalPreset.CUSTOM -> Unit
            VocalPreset.CLOSE -> {
                // Warm gentle soft-knee compression + subtle booth reflection
                val delaySamples = (sampleRate * 0.035).toInt().coerceAtLeast(1)
                for (i in samples.indices) {
                    val x = samples[i] * 1.15f
                    val compressed = x / (1f + 0.25f * abs(x))
                    val echo = if (i >= delaySamples) samples[i - delaySamples] * 0.12f else 0f
                    samples[i] = compressed + echo
                }
            }
            VocalPreset.STAGE -> {
                // Hall multi-tap reverb tail
                val tap1 = (sampleRate * 0.065).toInt().coerceAtLeast(1)
                val tap2 = (sampleRate * 0.130).toInt().coerceAtLeast(1)
                for (i in samples.indices) {
                    val r1 = if (i >= tap1) samples[i - tap1] * 0.28f else 0f
                    val r2 = if (i >= tap2) samples[i - tap2] * 0.18f else 0f
                    samples[i] = samples[i] + r1 + r2
                }
            }
            VocalPreset.RADIO -> {
                // Bright presence boost + tighter leveling
                var prev = 0f
                for (i in samples.indices) {
                    val current = samples[i]
                    val high = current - prev
                    prev = current
                    val boosted = (current + high * 0.35f) * 1.25f
                    samples[i] = boosted / (1f + 0.35f * abs(boosted))
                }
            }
        }
    }

    private fun normalizePeak(samples: FloatArray, targetPeak: Float) {
        var peak = 0f
        for (sample in samples) {
            val mag = abs(sample)
            if (mag > peak) peak = mag
        }
        if (peak <= 1e-4f) return
        val gain = (targetPeak / peak).coerceIn(0.25f, 4.0f)
        for (i in samples.indices) {
            samples[i] = (samples[i] * gain).coerceIn(-1f, 1f)
        }
    }

    private fun writePcm16MonoWav(file: File, sampleRate: Int, samples: FloatArray) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            val totalAudioBytes = samples.size * 2L
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val chunkSize = (36L + totalAudioBytes).coerceAtMost(0xFFFFFFFFL).toInt()
            val subChunk2Size = totalAudioBytes.coerceAtMost(0xFFFFFFFFL).toInt()

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(chunkSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign)
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(subChunk2Size)
            }
            raf.write(header.array())

            val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                if (chunk.remaining() < 2) {
                    raf.write(chunk.array(), 0, chunk.position())
                    chunk.clear()
                }
                val pcm = (sample.coerceIn(-1f, 1f) * 32767f).toInt()
                chunk.putShort(min(Short.MAX_VALUE.toInt(), max(Short.MIN_VALUE.toInt(), pcm)).toShort())
            }
            if (chunk.position() > 0) {
                raf.write(chunk.array(), 0, chunk.position())
            }
        }
    }
}
