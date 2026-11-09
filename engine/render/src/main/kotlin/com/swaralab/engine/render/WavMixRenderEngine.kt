package com.swaralab.engine.render

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.swaralab.domain.audio.PcmWarp
import com.swaralab.domain.audio.VocalCancel
import com.swaralab.domain.audio.WavPcm
import com.swaralab.domain.audio.VocalMixProcessor
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ClipState
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.port.ExportKind
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.InstrumentalResult
import com.swaralab.domain.port.MixPreview
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
    override suspend fun export(
        document: SessionDocument,
        request: ExportRequest,
        onProgress: (Float) -> Unit,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            val sessionFolder = File(projectsRoot, document.id)
            val targetSampleRate = 48_000
            onProgress(0.05f)

            val stereo = renderStereo(document, sessionFolder, targetSampleRate)
                ?: return@withContext ExportResult.Waiting(
                    "Record at least one vocal take on the Sing stage before mixing.",
                )
            onProgress(0.45f)

            val safeTitle = document.title
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "song" }
            val durationMs = ((stereo.left.size * 1000L) / targetSampleRate).coerceAtLeast(1L)
            val downmix = FloatArray(stereo.left.size) { i ->
                (stereo.left[i] + stereo.right[i]) * 0.5f
            }
            when (request.kind) {
                ExportKind.VOCALS -> {
                    val fileName = "$safeTitle-vocals.wav"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    writePcm16StereoWav(outFile, targetSampleRate, stereo.vocalLeft, stereo.vocalRight)
                    ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                }
                ExportKind.BACKING -> {
                    if (stereo.backingLeft == null) {
                        ExportResult.Waiting("Add a backing track before exporting the instrumental stem.")
                    } else {
                        val fileName = "$safeTitle-backing.wav"
                        val relativePath = SessionLayout.exportFile(fileName)
                        val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                        writePcm16StereoWav(
                            outFile,
                            targetSampleRate,
                            stereo.backingLeft,
                            stereo.backingRight ?: stereo.backingLeft,
                        )
                        ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                    }
                }
                ExportKind.COMPACT -> {
                    val fileName = "$safeTitle-${request.target.name.lowercase()}.m4a"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    if (CompactAudioEncoder.writeAacM4a(outFile, targetSampleRate, stereo.left, stereo.right)) {
                        ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                    } else {
                        ExportResult.Waiting("Could not encode compact audio on this device.")
                    }
                }
                ExportKind.VIDEO -> {
                    val fileName = "$safeTitle-${request.target.name.lowercase()}.mp4"
                    val relativePath = SessionLayout.exportFile(fileName)
                    val outFile = File(sessionFolder, relativePath).also { it.parentFile?.mkdirs() }
                    val cameraFile = cameraFileForMix(document, sessionFolder)
                    val audioSidecar = File(sessionFolder, SessionLayout.exportFile("$safeTitle-video-audio.m4a"))
                    val cameraMuxed = if (cameraFile.isFile &&
                        CompactAudioEncoder.writeAacM4a(audioSidecar, targetSampleRate, stereo.left, stereo.right)
                    ) {
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
                    if (LyricVideoRenderer.writeMp4(
                            outFile,
                            document,
                            stereo.left,
                            stereo.right,
                            targetSampleRate,
                        )
                    ) {
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
                    writePcm16StereoWav(outFile, targetSampleRate, stereo.left, stereo.right)
                    ExportResult.Written(relativePath = relativePath, durationMs = durationMs)
                }
                ExportKind.STEMS -> {
                    val stems = request.stems
                    val mixRel = SessionLayout.exportFile("$safeTitle-mix.wav")
                    if (stems.mix) {
                        writePcm16StereoWav(
                            File(sessionFolder, mixRel).also { it.parentFile?.mkdirs() },
                            targetSampleRate,
                            stereo.left,
                            stereo.right,
                        )
                    }
                    if (stems.vocals) {
                        writePcm16StereoWav(
                            File(sessionFolder, SessionLayout.exportFile("$safeTitle-vocals.wav")),
                            targetSampleRate,
                            stereo.vocalLeft,
                            stereo.vocalRight,
                        )
                    }
                    if (stems.backing && stereo.backingLeft != null) {
                        writePcm16StereoWav(
                            File(sessionFolder, SessionLayout.exportFile("$safeTitle-backing.wav")),
                            targetSampleRate,
                            stereo.backingLeft,
                            stereo.backingRight ?: stereo.backingLeft,
                        )
                    }
                    if (stems.leadDry) {
                        val lead = document.tracks.firstOrNull { it.role == TrackRole.LEAD }
                        if (lead != null) {
                            val dry = mixVocalTracks(
                                document,
                                sessionFolder,
                                targetSampleRate,
                                onlyTrackId = lead.id,
                                dry = true,
                            )
                            if (dry != null) {
                                writePcm16StereoWav(
                                    File(sessionFolder, SessionLayout.exportFile("$safeTitle-lead-dry.wav")),
                                    targetSampleRate,
                                    dry.first,
                                    dry.second,
                                )
                            }
                        }
                    }
                    if (stems.layers) {
                        writeLayerStems(document, sessionFolder, targetSampleRate, safeTitle)
                    }
                    val primary = when {
                        stems.mix -> mixRel
                        stems.vocals -> SessionLayout.exportFile("$safeTitle-vocals.wav")
                        stems.leadDry -> SessionLayout.exportFile("$safeTitle-lead-dry.wav")
                        else -> mixRel
                    }
                    ExportResult.Written(relativePath = primary, durationMs = durationMs)
                }
            }.also { onProgress(1f) }
        }

    override suspend fun preview(document: SessionDocument, maxDurationMs: Long): MixPreview? =
        withContext(Dispatchers.IO) {
            val sessionFolder = File(projectsRoot, document.id)
            val stereo = renderStereo(document, sessionFolder, 48_000, maxDurationMs) ?: return@withContext null
            MixPreview(left = stereo.left, right = stereo.right, sampleRate = 48_000)
        }

    override suspend fun writeMonitorBed(document: SessionDocument, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            val sessionFolder = File(projectsRoot, document.id)
            val stereo = renderStereo(document, sessionFolder, 48_000, monitor = true) ?: return@withContext false
            destination.parentFile?.mkdirs()
            writePcm16StereoWav(destination, 48_000, stereo.left, stereo.right)
            destination.isFile && destination.length() > 44L
        }

    override suspend fun writeInstrumental(source: File, destination: File): InstrumentalResult =
        withContext(Dispatchers.IO) {
            val stereo = decodeAudioFileToStereo48k(source, 48_000)
                ?: return@withContext InstrumentalResult.Failed("Could not read that song.")
            val made = VocalCancel.apply(stereo.first, stereo.second, 48_000)
            destination.parentFile?.mkdirs()
            writePcm16StereoWav(destination, 48_000, made.left, made.right)
            val durationMs = ((made.left.size * 1000L) / 48_000L).coerceAtLeast(1L)
            InstrumentalResult.Written(durationMs = durationMs, mostlyMono = made.mostlyMono)
        }

    private data class StereoMix(
        val left: FloatArray,
        val right: FloatArray,
        val vocalLeft: FloatArray,
        val vocalRight: FloatArray,
        val backingLeft: FloatArray?,
        val backingRight: FloatArray?,
    )

    private fun renderStereo(
        document: SessionDocument,
        sessionFolder: File,
        targetSampleRate: Int,
        maxDurationMs: Long = 0L,
        monitor: Boolean = false,
    ): StereoMix? {
        val mixed = mixVocalTracks(document, sessionFolder, targetSampleRate)
        var vocalL = mixed?.first ?: FloatArray(0)
        var vocalR = mixed?.second ?: FloatArray(0)
        if (maxDurationMs > 0L) {
            val frames = ((maxDurationMs * targetSampleRate) / 1000L).toInt().coerceAtLeast(1)
            if (vocalL.size > frames) vocalL = vocalL.copyOf(frames)
            if (vocalR.size > frames) vocalR = vocalR.copyOf(frames)
        }
        val backingTrack = document.tracks.firstOrNull { it.role == TrackRole.BACKING && !it.mute }
        val backingClip = backingTrack?.clips?.lastOrNull()
        var backingL: FloatArray? = null
        var backingR: FloatArray? = null
        if (backingTrack != null && backingClip != null) {
            val backingFile = File(sessionFolder, backingClip.sourceRelativePath)
            if (backingFile.isFile) {
                val decoded = decodeAudioFileToStereo48k(backingFile, targetSampleRate)
                if (decoded != null) {
                    val semitones = document.musical.keyShiftSemitones
                    val tempo = document.musical.playbackRate.coerceIn(0.5f, 1.5f)
                    val gain = dbToLinear(backingTrack.gainDb)
                    backingL = PcmWarp.shiftAndStretch(decoded.first, semitones, tempo)
                        .also { highPassInPlace(it, targetSampleRate) }
                    backingR = PcmWarp.shiftAndStretch(decoded.second, semitones, tempo)
                        .also { highPassInPlace(it, targetSampleRate) }
                    for (i in backingL.indices) backingL[i] *= gain
                    for (i in backingR.indices) backingR[i] *= gain
                    if (maxDurationMs > 0L) {
                        val frames = ((maxDurationMs * targetSampleRate) / 1000L).toInt()
                        if (backingL.size > frames) backingL = backingL.copyOf(frames)
                        if (backingR.size > frames) backingR = backingR.copyOf(frames)
                    }
                }
            }
        }

        val vocalFrames = min(vocalL.size, vocalR.size)
        val totalSamples = when {
            backingL != null && vocalFrames > 0 -> {
                max(vocalFrames, min(backingL.size, vocalFrames + targetSampleRate * 3))
            }
            backingL != null -> backingL.size
            else -> vocalFrames
        }
        if (totalSamples <= 0) return null
        val masterL = FloatArray(totalSamples)
        val masterR = FloatArray(totalSamples)
        val duckLinear = dbToLinear(-abs(document.chain.backingDuckDb))
        var env = 0f
        for (i in 0 until totalSamples) {
            val vL = if (i < vocalL.size) vocalL[i] else 0f
            val vR = if (i < vocalR.size) vocalR[i] else 0f
            val vMag = max(abs(vL), abs(vR))
            env = if (vMag > env) env + 0.012f * (vMag - env) else env * 0.9994f
            val duckAmount = if (env > 0.04f) ((env - 0.04f) * 7f).coerceIn(0f, 1f) else 0f
            val currentDuck = 1f - duckAmount * (1f - duckLinear)
            if (backingL != null && backingR != null && i < backingL.size && i < backingR.size) {
                masterL[i] = vL + backingL[i] * currentDuck
                masterR[i] = vR + backingR[i] * currentDuck
            } else {
                masterL[i] = vL
                masterR[i] = vR
            }
        }
        if (!monitor) {
            VocalMixProcessor.finishMasterStereo(
                masterL,
                masterR,
                targetLufs = document.publish.loudnessLufs,
                truePeakDbtp = document.publish.truePeakDbtp,
            )
        } else {
            for (i in masterL.indices) {
                masterL[i] = masterL[i].coerceIn(-1f, 1f)
                masterR[i] = masterR[i].coerceIn(-1f, 1f)
            }
        }
        return StereoMix(masterL, masterR, vocalL, vocalR, backingL, backingR)
    }

    private fun highPassInPlace(samples: FloatArray, sampleRate: Int) {
        val rc = 1.0 / (2.0 * Math.PI * 80.0)
        val dt = 1.0 / sampleRate
        val a = (rc / (rc + dt)).toFloat()
        var prevX = samples.firstOrNull() ?: return
        var prevY = 0f
        for (i in samples.indices) {
            val x = samples[i]
            val y = a * (prevY + x - prevX)
            samples[i] = y
            prevX = x
            prevY = y
        }
    }

    private fun mixVocalTracks(
        document: SessionDocument,
        sessionFolder: File,
        sampleRate: Int,
        onlyTrackId: String? = null,
        dry: Boolean = false,
    ): Pair<FloatArray, FloatArray>? {
        val anySolo = document.tracks.any { it.role != TrackRole.BACKING && it.solo && !it.mute }
        val placed = mutableListOf<Triple<Int, FloatArray, Pair<Float, Float>>>()
        var latestEnd = 0
        for (track in document.tracks) {
            if (onlyTrackId != null) {
                if (track.id != onlyTrackId) continue
            } else {
                if (track.role == TrackRole.BACKING || track.mute) continue
                if (anySolo && !track.solo) continue
            }
            if (track.role == TrackRole.BACKING) continue
            val trackGain = if (dry) 1f else dbToLinear(track.gainDb)
            val pan = if (dry) 0f else track.pan.coerceIn(-1f, 1f)
            val gL = trackGain * if (pan <= 0f) 1f else 1f - pan
            val gR = trackGain * if (pan >= 0f) 1f else 1f + pan
            val chosen = clipsForMix(track.clips)
            for (clip in chosen) {
                val file = File(sessionFolder, clip.sourceRelativePath)
                if (!file.isFile || file.length() <= 44L) continue
                val pcm = readPcm16MonoWav(file) ?: continue
                if (pcm.isEmpty()) continue
                val cropped = cropClip(pcm, sampleRate, clip)
                if (cropped.isEmpty()) continue
                val floats = FloatArray(cropped.size) { idx -> cropped[idx] / 32768f }
                if (!dry) {
                    val chain = track.chain ?: document.chain
                    VocalMixProcessor.applyChain(
                        floats,
                        sampleRate,
                        chain,
                        keyName = document.musical.keyName,
                        mode = document.musical.mode,
                    )
                }
                val start = ((clip.timelineStartMs * sampleRate) / 1000L).toInt().coerceAtLeast(0)
                placed += Triple(start, floats, gL to gR)
                latestEnd = max(latestEnd, start + floats.size)
            }
        }
        if (placed.isEmpty()) return null
        val shiftSamples = ((document.latency.compensationMs / 1000.0) * sampleRate).toInt()
        val n = (latestEnd + max(0, shiftSamples)).coerceAtLeast(1)
        val left = FloatArray(n)
        val right = FloatArray(n)
        for ((start, buf, gains) in placed) {
            for (i in buf.indices) {
                val destIdx = i + start + shiftSamples
                if (destIdx in left.indices) {
                    left[destIdx] += buf[i] * gains.first
                    right[destIdx] += buf[i] * gains.second
                }
            }
        }
        return left to right
    }

    private fun writeLayerStems(
        document: SessionDocument,
        sessionFolder: File,
        sampleRate: Int,
        safeTitle: String,
    ) {
        for (track in document.tracks) {
            if (track.role == TrackRole.BACKING || track.clips.isEmpty()) continue
            val layer = mixVocalTracks(document, sessionFolder, sampleRate, onlyTrackId = track.id) ?: continue
            val slug = track.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { track.role.name.lowercase() }
            writePcm16StereoWav(
                File(sessionFolder, SessionLayout.exportFile("$safeTitle-$slug.wav")),
                sampleRate,
                layer.first,
                layer.second,
            )
        }
    }

    private fun clipsForMix(clips: List<ClipState>): List<ClipState> {
        val live = clips.filter { it.active }
        val preferred = live.filter { it.preferred }
        return preferred.ifEmpty { live }
    }

    private fun cameraFileForMix(document: SessionDocument, sessionFolder: File): File {
        val voiceClips = document.tracks
            .filter { it.role != TrackRole.BACKING }
            .flatMap { clipsForMix(it.clips) }
        val bound = voiceClips.mapNotNull { it.videoRelativePath }
            .map { File(sessionFolder, it) }
            .firstOrNull { it.isFile }
        if (bound != null) return bound
        val legacy = File(sessionFolder, SessionLayout.performanceVideo())
        if (legacy.isFile) return legacy
        return sessionFolder.resolve(SessionLayout.VIDEO)
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: legacy
    }

    private fun cropClip(pcm: ShortArray, sampleRate: Int, clip: ClipState): ShortArray {
        val start = ((clip.sourceInMs * sampleRate) / 1000L).toInt().coerceIn(0, pcm.size)
        val wanted = ((clip.durationMs * sampleRate) / 1000L).toInt().coerceAtLeast(1)
        val end = (start + wanted).coerceAtMost(pcm.size)
        if (end <= start) return ShortArray(0)
        val cropped = pcm.copyOfRange(start, end)
        val fadeIn = ((sampleRate * clip.fadeInMs) / 1000L).toInt().coerceIn(1, (cropped.size / 3).coerceAtLeast(1))
        val fadeOut = ((sampleRate * clip.fadeOutMs) / 1000L).toInt().coerceIn(1, (cropped.size / 3).coerceAtLeast(1))
        for (i in 0 until fadeIn) {
            cropped[i] = (cropped[i] * (i / fadeIn.toFloat())).toInt().toShort()
        }
        for (i in 0 until fadeOut) {
            val g = i / fadeOut.toFloat()
            cropped[cropped.lastIndex - i] = (cropped[cropped.lastIndex - i] * g).toInt().toShort()
        }
        return cropped
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

    private fun readPcm16MonoWav(file: File): ShortArray? {
        val wav = readRiffWav(file) ?: return null
        val left = wav.left
        val right = wav.right
        val rate = wav.sampleRate
        val mono = FloatArray(left.size) { i ->
            if (i < right.size) (left[i] + right[i]) * 0.5f else left[i]
        }
        val atTarget = if (rate == 48_000 || rate <= 0) mono else resample(mono, rate / 48_000f)
        return ShortArray(atTarget.size) { i ->
            (atTarget[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }

    private data class RiffWav(
        val left: FloatArray,
        val right: FloatArray,
        val sampleRate: Int,
    )

    private fun readRiffWav(file: File): RiffWav? = runCatching {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var offset = 12
        var channels = 1
        var sampleRate = 48_000
        var bits = 16
        var audioFormat = 1
        var dataStart = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val body = offset + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                val fmt = ByteBuffer.wrap(bytes, body, min(size, bytes.size - body)).order(ByteOrder.LITTLE_ENDIAN)
                audioFormat = fmt.short.toInt() and 0xFFFF
                channels = fmt.short.toInt().coerceAtLeast(1)
                sampleRate = fmt.int.coerceAtLeast(1)
                fmt.int
                fmt.short
                bits = fmt.short.toInt() and 0xFFFF
            } else if (id == "data") {
                dataStart = body
                dataSize = size.coerceAtMost(bytes.size - body)
                break
            }
            offset = body + size + (size and 1)
        }
        if (dataStart < 0 || dataSize < 2) return null
        val bytesPerSample = (bits / 8).coerceAtLeast(1)
        val frameBytes = bytesPerSample * channels
        if (frameBytes <= 0) return null
        val frames = dataSize / frameBytes
        if (frames <= 0) return null
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        var i = dataStart
        for (f in 0 until frames) {
            val l = pcmSample(bytes, i, bits, audioFormat)
            val r = if (channels > 1) pcmSample(bytes, i + bytesPerSample, bits, audioFormat) else l
            left[f] = l
            right[f] = r
            i += frameBytes
        }
        RiffWav(left, right, sampleRate)
    }.getOrNull()

    private fun pcmSample(bytes: ByteArray, index: Int, bits: Int, audioFormat: Int): Float {
        return when {
            audioFormat == 3 && bits == 32 && index + 3 < bytes.size ->
                ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.LITTLE_ENDIAN).float
            bits >= 24 && index + 2 < bytes.size -> {
                val v = (bytes[index].toInt() and 0xFF) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                    (bytes[index + 2].toInt() shl 16)
                val signed = if (v and 0x800000 != 0) v or -0x1000000 else v
                signed / 8388608f
            }
            index + 1 < bytes.size -> {
                val v = (bytes[index].toInt() and 0xFF) or (bytes[index + 1].toInt() shl 8)
                v.toShort() / 32768f
            }
            else -> 0f
        }
    }

    private fun decodeAudioFileToStereo48k(file: File, targetRate: Int): Pair<FloatArray, FloatArray>? {
        readRiffWav(file)?.let { wav ->
            return if (wav.sampleRate == targetRate || wav.sampleRate <= 0) {
                wav.left to wav.right
            } else {
                resample(wav.left, wav.sampleRate.toFloat() / targetRate) to
                    resample(wav.right, wav.sampleRate.toFloat() / targetRate)
            }
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
            val leftSrc = FloatArray(frames)
            val rightSrc = FloatArray(frames)
            for (f in 0 until frames) {
                val l = shortBuf.get(f * channels) / 32768f
                val r = if (channels > 1) shortBuf.get(f * channels + 1) / 32768f else l
                leftSrc[f] = l
                rightSrc[f] = r
            }

            if (sourceRate == targetRate || sourceRate <= 0) {
                leftSrc to rightSrc
            } else {
                resample(leftSrc, sourceRate.toFloat() / targetRate) to
                    resample(rightSrc, sourceRate.toFloat() / targetRate)
            }
        }.getOrNull()
    }

    private fun writePcm16StereoWav(file: File, sampleRate: Int, left: FloatArray, right: FloatArray) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            val frames = min(left.size, right.size)
            val totalAudioBytes = frames * 4L
            val channels = 2
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
            for (i in 0 until frames) {
                if (chunk.remaining() < 4) {
                    raf.write(chunk.array(), 0, chunk.position())
                    chunk.clear()
                }
                val l = (left[i].coerceIn(-1f, 1f) * 32767f).toInt()
                val r = (right[i].coerceIn(-1f, 1f) * 32767f).toInt()
                chunk.putShort(min(Short.MAX_VALUE.toInt(), max(Short.MIN_VALUE.toInt(), l)).toShort())
                chunk.putShort(min(Short.MAX_VALUE.toInt(), max(Short.MIN_VALUE.toInt(), r)).toShort())
            }
            if (chunk.position() > 0) {
                raf.write(chunk.array(), 0, chunk.position())
            }
        }
    }

    override suspend fun encodeCompanionAac(wav: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        val pcm = WavPcm.readMono(wav) ?: return@withContext false
        CompactAudioEncoder.writeAacM4a(destination, pcm.sampleRate, pcm.samples)
    }
}
