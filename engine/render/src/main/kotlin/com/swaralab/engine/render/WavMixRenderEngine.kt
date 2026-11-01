package com.swaralab.engine.render

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ClipState
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ExportKind
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Mixes all unmuted vocal parts with the imported backing track (MP3/M4A/WAV/OGG),
 * applies vocal sync offset (`latency.compensationMs`), per-track volume (`gainDb`),
 * continuous vocal chain DSP (`denoise`, `compression`, `reverb`, `delay`),
 * automatic backing ducking (`backingDuckDb`), and peak normalization.
 */
class WavMixRenderEngine(
    private val projectsRoot: File,
) : RenderEngine {
    override suspend fun export(document: SessionDocument, request: ExportRequest): ExportResult =
        withContext(Dispatchers.IO) {
            val sessionFolder = File(projectsRoot, document.id)
            val targetSampleRate = 48_000

            val vocalMix = mixVocalTracks(document, sessionFolder, targetSampleRate)
            if (vocalMix == null) {
                return@withContext ExportResult.Waiting(
                    "Record at least one vocal take on the Sing stage before mixing.",
                )
            }

            // 2. Apply VocalChain DSP (Denoise, Compression, Presence, Reverb, Echo/Delay)
            applyVocalChain(vocalMix, targetSampleRate, document.chain)

            // 3. Decode backing track if present and unmuted
            val backingTrack = document.tracks.firstOrNull { it.role == TrackRole.BACKING && !it.mute }
            val backingClip = backingTrack?.clips?.lastOrNull()
            val backingSamples: FloatArray? = if (backingTrack != null && backingClip != null) {
                val backingFile = File(sessionFolder, backingClip.sourceRelativePath)
                if (backingFile.isFile) {
                    val gain = dbToLinear(backingTrack.gainDb)
                    val decoded = decodeAudioFileToMono48k(backingFile, targetSampleRate)
                    val rate = document.musical.playbackRate.coerceIn(0.7f, 1.35f)
                    val pitch = 2.0.pow(document.musical.keyShiftSemitones / 12.0).toFloat()
                    val stretched = if (decoded != null) {
                        resample(decoded, rate * pitch)
                    } else {
                        null
                    }
                    stretched?.also { arr ->
                        for (i in arr.indices) {
                            arr[i] *= gain
                        }
                    }
                } else {
                    null
                }
            } else {
                null
            }

            // 4. Combine backing + vocals (with smooth backing ducking when voice is active)
            val totalSamples = if (backingSamples != null) {
                // Keep full vocal length plus up to 3 seconds of backing tail after vocals finish
                val maxTail = min(backingSamples.size, vocalMix.size + targetSampleRate * 3)
                max(vocalMix.size, maxTail)
            } else {
                vocalMix.size
            }

            val master = FloatArray(totalSamples)
            val duckLinear = dbToLinear(-abs(document.chain.backingDuckDb))
            var env = 0f

            for (i in 0 until totalSamples) {
                val v = if (i < vocalMix.size) vocalMix[i] else 0f
                val vMag = abs(v)
                env = if (vMag > env) env + 0.01f * (vMag - env) else env * 0.9992f
                val b = if (backingSamples != null && i < backingSamples.size) {
                    val duckAmount = (env * 8f).coerceIn(0f, 1f)
                    val currentDuck = 1f - duckAmount * (1f - duckLinear)
                    backingSamples[i] * currentDuck
                } else {
                    0f
                }
                master[i] = v + b
            }

            normalizePeak(master, targetPeak = 0.89f)

            val safeTitle = document.title
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "song" }
            val durationMs = ((master.size * 1000L) / targetSampleRate).coerceAtLeast(1L)
            when (request.kind) {
                ExportKind.VOCALS -> {
                    val fileName = "$safeTitle-vocals.wav"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    writePcm16MonoWav(outFile, targetSampleRate, vocalMix)
                    ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                }
                ExportKind.BACKING -> {
                    if (backingSamples == null) {
                        ExportResult.Waiting("Add a backing track before exporting the instrumental stem.")
                    } else {
                        val fileName = "$safeTitle-backing.wav"
                        val relativePath = SessionLayout.exportFile(fileName)
                        val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                        writePcm16MonoWav(outFile, targetSampleRate, backingSamples)
                        ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                    }
                }
                ExportKind.COMPACT -> {
                    val fileName = "$safeTitle-${request.target.name.lowercase()}.m4a"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    if (CompactAudioEncoder.writeAacM4a(outFile, targetSampleRate, master)) {
                        ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                    } else {
                        ExportResult.Waiting("Could not encode compact audio on this device.")
                    }
                }
                ExportKind.VIDEO -> {
                    val fileName = "$safeTitle-${request.target.name.lowercase()}.mp4"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    val cameraFile = File(sessionFolder, SessionLayout.performanceVideo())
                    val audioSidecar = File(sessionFolder, SessionLayout.exportFile("$safeTitle-video-audio.m4a"))
                    val cameraMuxed = if (cameraFile.isFile && CompactAudioEncoder.writeAacM4a(audioSidecar, targetSampleRate, master)) {
                        CameraMixMuxer.mux(cameraFile, audioSidecar, outFile)
                    } else {
                        false
                    }
                    audioSidecar.delete()
                    if (cameraMuxed) {
                        ExportResult.Written(
                            relativePath = relativePath,
                            durationMs = durationMs,
                            videoRelativePath = relativePath,
                        )
                    } else {
                        ExportResult.Waiting("Sing a camera take on the Video stage first, or export a lyric card.")
                    }
                }
                ExportKind.LYRIC_VIDEO -> {
                    val fileName = "$safeTitle-lyric-card.mp4"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    if (LyricVideoRenderer.writeMp4(outFile, document, master, targetSampleRate)) {
                        ExportResult.Written(
                            relativePath = relativePath,
                            durationMs = durationMs,
                            videoRelativePath = relativePath,
                        )
                    } else {
                        ExportResult.Waiting("Could not render the lyric card on this device.")
                    }
                }
                ExportKind.MIX -> {
                    val fileName = "$safeTitle-${request.target.name.lowercase()}.wav"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    writePcm16MonoWav(outFile, targetSampleRate, master)
                    ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                }
            }
        }

    private fun mixVocalTracks(
        document: SessionDocument,
        sessionFolder: File,
        sampleRate: Int,
    ): FloatArray? {
        val placed = mutableListOf<Pair<Int, FloatArray>>()
        var latestEnd = 0
        for (track in document.tracks) {
            if (track.role == TrackRole.BACKING || track.mute) continue
            val trackGain = dbToLinear(track.gainDb)
            val chosen = clipsForMix(track.clips)
            for (clip in chosen) {
                val file = File(sessionFolder, clip.sourceRelativePath)
                if (!file.isFile || file.length() <= 44L) continue
                val pcm = readPcm16MonoWav(file) ?: continue
                if (pcm.isEmpty()) continue
                val cropped = cropClip(pcm, sampleRate, clip)
                if (cropped.isEmpty()) continue
                val floats = FloatArray(cropped.size) { idx -> (cropped[idx] / 32768f) * trackGain }
                val start = ((clip.timelineStartMs * sampleRate) / 1000L).toInt().coerceAtLeast(0)
                placed += start to floats
                latestEnd = max(latestEnd, start + floats.size)
            }
        }
        if (placed.isEmpty()) return null
        val shiftSamples = ((document.latency.compensationMs / 1000.0) * sampleRate).toInt()
        val vocalMix = FloatArray((latestEnd + max(0, shiftSamples)).coerceAtLeast(1))
        for ((start, buf) in placed) {
            for (i in buf.indices) {
                val destIdx = i + start + shiftSamples
                if (destIdx in vocalMix.indices) vocalMix[destIdx] += buf[i]
            }
        }
        return vocalMix
    }

    private fun clipsForMix(clips: List<ClipState>): List<ClipState> {
        val preferred = clips.filter { it.preferred }
        return preferred.ifEmpty { clips.takeLast(1) }
    }

    private fun cropClip(pcm: ShortArray, sampleRate: Int, clip: ClipState): ShortArray {
        val start = ((clip.sourceInMs * sampleRate) / 1000L).toInt().coerceIn(0, pcm.size)
        val wanted = ((clip.durationMs * sampleRate) / 1000L).toInt().coerceAtLeast(1)
        val end = (start + wanted).coerceAtMost(pcm.size)
        if (end <= start) return ShortArray(0)
        return pcm.copyOfRange(start, end)
    }

    private fun resample(input: FloatArray, rate: Float): FloatArray {
        val safeRate = rate.coerceIn(0.5f, 2.0f)
        if (kotlin.math.abs(safeRate - 1f) < 0.001f) return input
        val outFrames = (input.size / safeRate).toInt().coerceAtLeast(1)
        val output = FloatArray(outFrames)
        for (i in 0 until outFrames) {
            val srcPos = i * safeRate
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = srcPos - idx0
            output[i] = input[idx0] * (1f - frac) + input[idx1] * frac
        }
        return output
    }

    private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    private fun readPcm16MonoWav(file: File): ShortArray? = runCatching {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return null
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        if (riff != "RIFF") return null
        val payloadBytes = bytes.size - 44
        val count = payloadBytes / 2
        val shorts = ShortArray(count)
        ByteBuffer.wrap(bytes, 44, count * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        shorts
    }.getOrNull()

    private fun decodeAudioFileToMono48k(file: File, targetRate: Int): FloatArray? {
        // Fast path if the backing file is already a PCM WAV
        readPcm16MonoWav(file)?.let { shorts ->
            return FloatArray(shorts.size) { i -> shorts[i] / 32768f }
        }
        return runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) {
                extractor.release()
                return null
            }
            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sourceRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                targetRate
            }
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            // Cap decoding at 6 minutes max to protect memory
            val maxDecodedBytes = sourceRate * channels * 2 * 360

            while (!outputDone && pcmOut.size() < maxDecodedBytes) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sourceRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val rawBytes = pcmOut.toByteArray()
            val totalShorts = rawBytes.size / 2
            val frames = totalShorts / channels
            if (frames <= 0) return null

            val shortBuf = ByteBuffer.wrap(rawBytes, 0, frames * channels * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
            val monoSource = FloatArray(frames)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += shortBuf.get(f * channels + c) / 32768f
                }
                monoSource[f] = sum / channels
            }

            if (sourceRate == targetRate || sourceRate <= 0) {
                monoSource
            } else {
                val outFrames = ((frames.toLong() * targetRate) / sourceRate).toInt().coerceAtLeast(1)
                val resampled = FloatArray(outFrames)
                val ratio = sourceRate.toDouble() / targetRate.toDouble()
                for (i in 0 until outFrames) {
                    val srcPos = i * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, frames - 1)
                    val idx1 = (idx0 + 1).coerceIn(0, frames - 1)
                    val frac = (srcPos - idx0).toFloat()
                    resampled[i] = monoSource[idx0] * (1f - frac) + monoSource[idx1] * frac
                }
                resampled
            }
        }.getOrNull()
    }

    private fun applyVocalChain(samples: FloatArray, sampleRate: Int, chain: VocalChain) {
        if (!chain.enabled) return
        val denoiseAmount = chain.denoise.coerceIn(0f, 1f)
        val compAmount = chain.compression.coerceIn(0f, 1f)
        val reverbAmount = chain.reverb.coerceIn(0f, 1f)
        val delayAmount = chain.delay.coerceIn(0f, 1f)
        val brightBoost = if (chain.preset == VocalPreset.RADIO) 0.35f else 0f

        // 1. Soft noise gate + compression + presence boost
        val gateThreshold = denoiseAmount * 0.025f
        var prev = 0f
        for (i in samples.indices) {
            var x = samples[i]
            if (gateThreshold > 0f && abs(x) < gateThreshold) {
                val ratio = (abs(x) / gateThreshold).coerceIn(0f, 1f)
                x *= ratio * ratio
            }
            if (brightBoost > 0f) {
                val high = x - prev
                prev = x
                x += high * brightBoost
            } else {
                prev = x
            }
            if (compAmount > 0f) {
                val drive = 1f + compAmount * 0.65f
                val driven = x * drive
                x = driven / (1f + compAmount * 0.55f * abs(driven))
            }
            samples[i] = x
        }

        // 2. Echo / Delay (180 ms slap/echo)
        if (delayAmount > 0.01f) {
            val delaySamples = (sampleRate * 0.180).toInt().coerceAtLeast(1)
            val wet = delayAmount * 0.45f
            for (i in delaySamples until samples.size) {
                samples[i] = samples[i] + samples[i - delaySamples] * wet
            }
        }

        // 3. Multi-tap Hall/Studio Reverb
        if (reverbAmount > 0.01f) {
            val tap1 = (sampleRate * 0.045).toInt().coerceAtLeast(1)
            val tap2 = (sampleRate * 0.095).toInt().coerceAtLeast(1)
            val tap3 = (sampleRate * 0.155).toInt().coerceAtLeast(1)
            val w1 = reverbAmount * 0.32f
            val w2 = reverbAmount * 0.22f
            val w3 = reverbAmount * 0.14f
            for (i in samples.indices) {
                val r1 = if (i >= tap1) samples[i - tap1] * w1 else 0f
                val r2 = if (i >= tap2) samples[i - tap2] * w2 else 0f
                val r3 = if (i >= tap3) samples[i - tap3] * w3 else 0f
                samples[i] = samples[i] + r1 + r2 + r3
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
