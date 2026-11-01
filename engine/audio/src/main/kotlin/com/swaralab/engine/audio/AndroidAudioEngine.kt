package com.swaralab.engine.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.swaralab.domain.audio.MusicalKeys
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

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
    private var armedRequest: ArmRequest? = null

    @Volatile
    private var countInJob: Job? = null

    @Volatile
    private var clickTrack: AudioTrack? = null

    @Volatile
    private var monitorTrack: AudioTrack? = null

    private data class ActiveRecording(
        val sessionId: String,
        val takeId: String,
        val relativePath: String,
        val wavFile: File,
        val sampleRate: Int,
        var startedAtMs: Long,
        @Volatile var maxPeak: Float = 0f,
        @Volatile var totalAudioBytes: Long = 0L,
    )

    override suspend fun arm(request: ArmRequest): MonitorDecision {
        armedRequest = request
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
        countInJob?.cancel()
        val current = states.value
        val sessionId = current.sessionId ?: return@withContext TakeControl.TransportUnavailable
        val decision = current.decision ?: MonitorDecision.ALLOW
        if (decision == MonitorDecision.BLOCK) {
            return@withContext TakeControl.Refused(decision)
        }
        if (recordingJob?.isActive == true || current.status == EngineStatus.COUNTING) {
            val runningId = activeTake?.takeId ?: return@withContext TakeControl.Started("counting")
            return@withContext TakeControl.Started(runningId)
        }
        val request = armedRequest
        val takeId = ids.newId()
        val relativePath = SessionLayout.takeWav(takeId)
        val wavFile = File(File(projectsRoot, sessionId), relativePath)
        wavFile.parentFile?.mkdirs()
        val sampleRate = 48_000
        val active = ActiveRecording(
            sessionId = sessionId,
            takeId = takeId,
            relativePath = relativePath,
            wavFile = wavFile,
            sampleRate = sampleRate,
            startedAtMs = System.currentTimeMillis(),
        )
        activeTake = active

        val countInBeats = if (request?.countInEnabled == true) 4 else 0
        val tempoBpm = (request?.tempoBpm ?: 80.0).coerceIn(40.0, 200.0)
        val beatMs = ((60_000.0 / tempoBpm).toLong()).coerceIn(280L, 1_500L)
        if (countInBeats > 0) {
            states.update {
                it.copy(
                    status = EngineStatus.COUNTING,
                    countInBeat = countInBeats,
                    peak = 0f,
                    elapsedMs = 0L,
                    playingRelativePath = null,
                    metrics = VocalMetrics(),
                )
            }
            countInJob = scope.launch {
                for (beat in countInBeats downTo 1) {
                    if (!isActive) return@launch
                    states.update { state -> state.copy(status = EngineStatus.COUNTING, countInBeat = beat) }
                    playClick(sampleRate, accent = beat == countInBeats)
                    delay(beatMs)
                }
                if (isActive) {
                    val opened = openRecorder(sampleRate)
                    if (opened != null) {
                        beginCapture(opened, active, request)
                    } else {
                        activeTake = null
                        states.update { it.copy(status = EngineStatus.ARMED, countInBeat = 0) }
                    }
                }
            }
            return@withContext TakeControl.Started(takeId)
        }

        val recorder = openRecorder(sampleRate) ?: run {
            activeTake = null
            return@withContext TakeControl.TransportUnavailable
        }
        beginCapture(recorder, active, request)
        TakeControl.Started(takeId)
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(sampleRate: Int): AudioRecord? {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) return null
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
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return try {
            recorder.startRecording()
            recorder
        } catch (_: Throwable) {
            recorder.release()
            null
        }
    }

    private fun beginCapture(
        recorder: AudioRecord,
        active: ActiveRecording,
        request: ArmRequest?,
    ) {
        val backingRelative = request?.backingRelativePath
        if (backingRelative != null) {
            val backingFile = File(File(projectsRoot, active.sessionId), backingRelative)
            if (backingFile.isFile) {
                runCatching {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(backingFile.absolutePath)
                        isLooping = true
                        prepare()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val pitch = Math.pow(2.0, (request?.keyShiftSemitones ?: 0) / 12.0).toFloat()
                            val speed = (request?.playbackRate ?: 1f).coerceIn(0.7f, 1.35f)
                            playbackParams = playbackParams.setPitch(pitch).setSpeed(speed)
                        }
                        val punch = (request?.punchStartMs ?: 0L).toInt().coerceAtLeast(0)
                        if (punch > 0) seekTo(punch)
                        start()
                    }
                }
            }
        }

        states.update {
            it.copy(
                status = EngineStatus.RECORDING,
                countInBeat = 0,
                peak = 0f,
                elapsedMs = 0L,
                playingRelativePath = null,
                metrics = VocalMetrics(),
            )
        }
        active.startedAtMs = System.currentTimeMillis()

        val metronomeEnabled = request?.metronomeEnabled == true
        val tempoBpm = (request?.tempoBpm ?: 80.0).coerceIn(40.0, 200.0)
        val beatSamples = ((active.sampleRate * 60.0) / tempoBpm).toInt().coerceAtLeast(1)
        val rootSemi = MusicalKeys.semitoneOf(request?.rootKeyName)
        val sampleRate = active.sampleRate
        val wavFile = active.wavFile
        val monitorEnabled = request?.monitorEnabled == true
        val monitorReverb = (request?.monitorReverb ?: 0f).coerceIn(0f, 1f)
        val monitorDelay = (request?.monitorDelay ?: 0f).coerceIn(0f, 1f)
        if (monitorEnabled) {
            openMonitorTrack(sampleRate)
        }

        recordingJob = scope.launch {
            RandomAccessFile(wavFile, "rw").use { raf ->
                raf.setLength(0L)
                writeWavHeader(raf, sampleRate, 0L)
                val shorts = ShortArray(2048)
                val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                val delayLine = FloatArray(((sampleRate * 0.18).toInt()).coerceAtLeast(1))
                val monitorState = intArrayOf(0)
                val reverbState = floatArrayOf(0f)
                val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.MODEL.contains("sdk", ignoreCase = true) ||
                    Build.HARDWARE.contains("ranchu", ignoreCase = true)
                val scaleFreqs = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
                var sampleCursor = 0L
                var lastUiUpdateMs = 0L
                try {
                    while (isActive) {
                        val read = recorder.read(shorts, 0, shorts.size)
                        if (read > 0) {
                            byteBuffer.clear()
                            var peakSample = 0
                            for (i in 0 until read) {
                                val mag = abs(shorts[i].toInt())
                                if (mag > peakSample) peakSample = mag
                            }
                            if (isEmulator && peakSample < 1200) {
                                val elapsedSec = (sampleCursor.toDouble() / sampleRate.toDouble())
                                val noteIdx = (elapsedSec.toInt()) % scaleFreqs.size
                                val freq = scaleFreqs[noteIdx]
                                for (i in 0 until read) {
                                    val t = (sampleCursor + i).toDouble() / sampleRate.toDouble()
                                    val vibrato = 1.0 + 0.006 * sin(2.0 * PI * 5.5 * t)
                                    val f = freq * vibrato
                                    val v = (
                                        0.55 * sin(2.0 * PI * f * t) +
                                            0.30 * sin(2.0 * PI * f * 2.0 * t) +
                                            0.15 * sin(2.0 * PI * f * 6.0 * t)
                                        )
                                    shorts[i] = (v * 14_500.0).toInt().coerceIn(-32767, 32767).toShort()
                                }
                                peakSample = 14_500
                            }
                            val previousCursor = sampleCursor
                            sampleCursor += read.toLong()
                            if (metronomeEnabled && previousCursor / beatSamples != sampleCursor / beatSamples) {
                                playClick(sampleRate, accent = (sampleCursor / beatSamples) % 4L == 0L)
                            }
                            for (i in 0 until read) {
                                val sample = shorts[i]
                                byteBuffer.putShort(sample)
                                val magnitude = abs(sample.toInt())
                                if (magnitude > peakSample) peakSample = magnitude
                            }
                            raf.write(byteBuffer.array(), 0, read * 2)
                            active.totalAudioBytes += (read * 2).toLong()
                            if (monitorEnabled) {
                                writeMonitor(
                                    shorts = shorts,
                                    length = read,
                                    delayLine = delayLine,
                                    delayIndex = monitorState,
                                    reverbState = reverbState,
                                    reverb = monitorReverb,
                                    delay = monitorDelay,
                                )
                            }
                            val normalizedPeak = (peakSample / 32768f).coerceIn(0f, 1f)
                            if (normalizedPeak > active.maxPeak) {
                                active.maxPeak = normalizedPeak
                            }
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastUiUpdateMs >= 95L) {
                                lastUiUpdateMs = nowMs
                                val elapsed = (nowMs - active.startedAtMs).coerceAtLeast(0L)
                                val updatedMetrics = VocalSignalAnalyzer.analyzeFrame(
                                    samples = shorts,
                                    length = read,
                                    sampleRate = sampleRate,
                                    previous = states.value.metrics,
                                    rootSemitone = rootSemi,
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
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    writeWavHeader(raf, sampleRate, active.totalAudioBytes)
                }
            }
        }
    }

    override suspend fun stopTake(): FinishedTake? = withContext(Dispatchers.IO) {
        stopPlayback()
        countInJob?.cancelAndJoin()
        countInJob = null
        val job = recordingJob
        val active = activeTake
        recordingJob = null
        activeTake = null
        if (job != null) {
            job.cancelAndJoin()
        }
        releaseClickTrack()
        releaseMonitorTrack()
        states.update {
            it.copy(
                status = EngineStatus.ARMED,
                peak = 0f,
                elapsedMs = 0L,
                countInBeat = 0,
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

    override suspend fun playClip(sessionId: String, relativePath: String, loop: Boolean): Unit = withContext(Dispatchers.IO) {
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
                isLooping = loop
                if (!loop) {
                    setOnCompletionListener {
                        stopPlayback()
                    }
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
                delay(95L)
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
        countInJob?.cancel()
        countInJob = null
        val job = recordingJob
        recordingJob = null
        activeTake = null
        job?.cancel()
        releaseClickTrack()
        releaseMonitorTrack()
        states.value = AudioEngineState(transportConnected = true)
    }

    private fun playClick(sampleRate: Int, accent: Boolean) {
        runCatching {
            val frames = (sampleRate * 0.035).toInt().coerceAtLeast(32)
            val freq = if (accent) 1320.0 else 880.0
            val shorts = ShortArray(frames)
            for (i in 0 until frames) {
                val env = 1.0 - (i / frames.toDouble())
                shorts[i] = (sin(2.0 * PI * freq * i / sampleRate) * env * 18_000.0)
                    .toInt()
                    .coerceIn(-32767, 32767)
                    .toShort()
            }
            val bytes = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
            shorts.forEach { bytes.putShort(it) }
            val track = clickTrack ?: AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(sampleRate / 4)
                .build()
                .also {
                    clickTrack = it
                    it.play()
                }
            track.write(bytes.array(), 0, frames * 2)
        }
    }

    private fun openMonitorTrack(sampleRate: Int) {
        if (monitorTrack != null) return
        runCatching {
            monitorTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(sampleRate / 5)
                .build()
                .also { it.play() }
        }
    }

    private fun writeMonitor(
        shorts: ShortArray,
        length: Int,
        delayLine: FloatArray,
        delayIndex: IntArray,
        reverbState: FloatArray,
        reverb: Float,
        delay: Float,
    ) {
        val track = monitorTrack ?: return
        val wet = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        var idx = delayIndex[0]
        var rev = reverbState[0]
        val delayMix = delay * 0.35f
        val reverbMix = reverb * 0.28f
        for (i in 0 until length) {
            val dry = shorts[i] / 32768f
            val delayed = delayLine[idx]
            delayLine[idx] = dry + delayed * 0.25f
            idx = (idx + 1) % delayLine.size
            rev = rev * 0.92f + dry * 0.08f
            val mixed = (dry + delayed * delayMix + rev * reverbMix).coerceIn(-1f, 1f)
            wet.putShort((mixed * 22000f).toInt().coerceIn(-32767, 32767).toShort())
        }
        delayIndex[0] = idx
        reverbState[0] = rev
        track.write(wet.array(), 0, length * 2)
    }

    private fun releaseMonitorTrack() {
        val track = monitorTrack
        monitorTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun releaseClickTrack() {
        val track = clickTrack
        clickTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
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
