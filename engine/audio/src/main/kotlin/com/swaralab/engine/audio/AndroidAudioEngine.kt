package com.swaralab.engine.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.policy.MonitorPolicy
import com.swaralab.domain.policy.MonitorPurpose
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.TakeControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Live Android microphone capture and take playback.
 * Writes 16-bit mono PCM WAV into the session's `media/takes/{takeId}.wav` folder
 * and streams real-time vocal pitch, note, Swara, and 3-band EQ metrics.
 */
class AndroidAudioEngine(
    private val projectsRoot: File,
    private val ids: IdGenerator = UuidIdGenerator(),
) : AudioEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = MutableStateFlow(AudioEngineState(transportConnected = true))
    override val state: StateFlow<AudioEngineState> = states.asStateFlow()

    @Volatile
    private var recordingJob: Job? = null

    @Volatile
    private var playbackMeterJob: Job? = null

    @Volatile
    private var activeTake: ActiveRecording? = null

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var armedBackingRelativePath: String? = null

    private data class ActiveRecording(
        val sessionId: String,
        val takeId: String,
        val relativePath: String,
        val wavFile: File,
        val sampleRate: Int,
        val startedAtMs: Long,
        @Volatile var maxPeak: Float = 0f,
        @Volatile var totalAudioBytes: Long = 0L,
    )

    override suspend fun arm(request: ArmRequest): MonitorDecision {
        armedBackingRelativePath = request.backingRelativePath
        val decision = if (request.backingRelativePath != null) {
            MonitorPolicy.evaluate(request.output, MonitorPurpose.RECORD)
        } else {
            val policy = MonitorPolicy.evaluate(request.output, MonitorPurpose.RECORD)
            if (policy == MonitorDecision.BLOCK) MonitorDecision.WARN else policy
        }
        if (states.value.status != EngineStatus.RECORDING) {
            states.update {
                it.copy(
                    status = if (decision == MonitorDecision.BLOCK) EngineStatus.BLOCKED else EngineStatus.ARMED,
                    sessionId = request.sessionId,
                    output = request.output,
                    decision = decision,
                    transportConnected = true,
                )
            }
        }
        return decision
    }

    @SuppressLint("MissingPermission")
    override suspend fun startTake(): TakeControl = withContext(Dispatchers.IO) {
        stopPlayback()
        val current = states.value
        val sessionId = current.sessionId ?: return@withContext TakeControl.TransportUnavailable
        val decision = current.decision ?: MonitorDecision.ALLOW
        if (decision == MonitorDecision.BLOCK) {
            return@withContext TakeControl.Refused(decision)
        }
        if (recordingJob?.isActive == true) {
            val runningId = activeTake?.takeId ?: return@withContext TakeControl.TransportUnavailable
            return@withContext TakeControl.Started(runningId)
        }

        val sampleRate = 48_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) {
            return@withContext TakeControl.TransportUnavailable
        }
        val bufferSize = max(minBuffer * 2, 8192)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (_: Throwable) {
            return@withContext TakeControl.TransportUnavailable
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext TakeControl.TransportUnavailable
        }

        val takeId = ids.newId()
        val relativePath = SessionLayout.takeWav(takeId)
        val wavFile = File(File(projectsRoot, sessionId), relativePath)
        wavFile.parentFile?.mkdirs()

        val active = ActiveRecording(
            sessionId = sessionId,
            takeId = takeId,
            relativePath = relativePath,
            wavFile = wavFile,
            sampleRate = sampleRate,
            startedAtMs = System.currentTimeMillis(),
        )
        activeTake = active

        try {
            recorder.startRecording()
        } catch (_: Throwable) {
            recorder.release()
            activeTake = null
            return@withContext TakeControl.TransportUnavailable
        }

        val backingRelative = armedBackingRelativePath
        if (backingRelative != null) {
            val backingFile = File(File(projectsRoot, sessionId), backingRelative)
            if (backingFile.isFile) {
                runCatching {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(backingFile.absolutePath)
                        prepare()
                        start()
                    }
                }
            }
        }

        states.update {
            it.copy(
                status = EngineStatus.RECORDING,
                peak = 0f,
                elapsedMs = 0L,
                playingRelativePath = null,
                metrics = VocalMetrics(),
            )
        }

        recordingJob = scope.launch {
            RandomAccessFile(wavFile, "rw").use { raf ->
                raf.setLength(0L)
                writeWavHeader(raf, sampleRate, 0L)
                val shorts = ShortArray(2048)
                val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                try {
                    while (isActive) {
                        val read = recorder.read(shorts, 0, shorts.size)
                        if (read > 0) {
                            byteBuffer.clear()
                            var peakSample = 0
                            for (i in 0 until read) {
                                val sample = shorts[i]
                                byteBuffer.putShort(sample)
                                val magnitude = abs(sample.toInt())
                                if (magnitude > peakSample) peakSample = magnitude
                            }
                            raf.write(byteBuffer.array(), 0, read * 2)
                            active.totalAudioBytes += (read * 2).toLong()
                            val normalizedPeak = (peakSample / 32768f).coerceIn(0f, 1f)
                            if (normalizedPeak > active.maxPeak) {
                                active.maxPeak = normalizedPeak
                            }
                            val elapsed = (System.currentTimeMillis() - active.startedAtMs).coerceAtLeast(0L)
                            val updatedMetrics = VocalSignalAnalyzer.analyzeFrame(
                                samples = shorts,
                                length = read,
                                sampleRate = sampleRate,
                                previous = states.value.metrics,
                            )
                            states.update { state ->
                                state.copy(
                                    status = EngineStatus.RECORDING,
                                    peak = normalizedPeak,
                                    elapsedMs = elapsed,
                                    metrics = updatedMetrics,
                                )
                            }
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    writeWavHeader(raf, sampleRate, active.totalAudioBytes)
                }
            }
        }

        TakeControl.Started(takeId)
    }

    override suspend fun stopTake(): FinishedTake? = withContext(Dispatchers.IO) {
        stopPlayback()
        val job = recordingJob
        val active = activeTake
        recordingJob = null
        activeTake = null
        if (job != null) {
            job.cancelAndJoin()
        }
        states.update {
            it.copy(
                status = EngineStatus.ARMED,
                peak = 0f,
                elapsedMs = 0L,
            )
        }
        if (active == null || !active.wavFile.isFile || active.totalAudioBytes <= 0L) {
            return@withContext null
        }
        val durationMs = ((active.totalAudioBytes * 1000L) / (active.sampleRate * 2L)).coerceAtLeast(1L)
        FinishedTake(
            relativePath = active.relativePath,
            durationMs = durationMs,
            peak = active.maxPeak,
        )
    }

    override suspend fun playClip(sessionId: String, relativePath: String): Unit = withContext(Dispatchers.IO) {
        if (states.value.playingRelativePath == relativePath) {
            stopPlayback()
            return@withContext
        }
        stopPlayback()
        val file = File(File(projectsRoot, sessionId), relativePath)
        if (!file.isFile) return@withContext
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                }
                prepare()
                start()
            }
            mediaPlayer = player
            states.update {
                it.copy(
                    playingRelativePath = relativePath,
                    metrics = VocalMetrics(),
                )
            }
            if (file.extension.equals("wav", ignoreCase = true) && file.length() > 44L) {
                startWavPlaybackMeter(file, player)
            }
        }
        Unit
    }

    private fun startWavPlaybackMeter(wavFile: File, player: MediaPlayer) {
        playbackMeterJob?.cancel()
        playbackMeterJob = scope.launch {
            val bytes = runCatching { wavFile.readBytes() }.getOrNull() ?: return@launch
            if (bytes.size <= 44) return@launch
            val sampleRate = 48_000
            val totalSamples = (bytes.size - 44) / 2
            val allShorts = ShortArray(totalSamples)
            ByteBuffer.wrap(bytes, 44, totalSamples * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(allShorts)

            val frameSize = 2048
            val frameBuffer = ShortArray(frameSize)
            while (isActive && mediaPlayer === player) {
                val posMs = runCatching {
                    if (player.isPlaying) player.currentPosition else -1
                }.getOrDefault(-1)
                if (posMs < 0) break
                val startSample = ((posMs.toLong() * sampleRate) / 1000L).toInt().coerceIn(0, max(0, totalSamples - 1))
                val count = (totalSamples - startSample).coerceIn(0, frameSize)
                if (count > 64) {
                    System.arraycopy(allShorts, startSample, frameBuffer, 0, count)
                    val updatedMetrics = VocalSignalAnalyzer.analyzeFrame(
                        samples = frameBuffer,
                        length = count,
                        sampleRate = sampleRate,
                        previous = states.value.metrics,
                    )
                    states.update { it.copy(metrics = updatedMetrics) }
                }
                delay(45L)
            }
        }
    }

    override fun stopPlayback() {
        playbackMeterJob?.cancel()
        playbackMeterJob = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        states.update { it.copy(playingRelativePath = null) }
    }

    override fun disarm() {
        stopPlayback()
        val job = recordingJob
        recordingJob = null
        activeTake = null
        job?.cancel()
        states.value = AudioEngineState(transportConnected = true)
    }

    private fun writeWavHeader(raf: RandomAccessFile, sampleRate: Int, totalAudioBytes: Long) {
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
        raf.seek(0L)
        raf.write(header.array())
    }
}
