package com.swaralab.engine.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.audio.LiveCaptureCleaner
import com.swaralab.domain.audio.LiveMonitorChain
import com.swaralab.domain.audio.LiveMonitorEffect
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
import com.swaralab.domain.port.AudioEngineError
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.port.isLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
    private val errorEvents = MutableSharedFlow<AudioEngineError>(extraBufferCapacity = 8)
    private val transport = Mutex()
    override val state: StateFlow<AudioEngineState> = states.asStateFlow()
    override val errors: SharedFlow<AudioEngineError> = errorEvents.asSharedFlow()

    @Volatile
    private var recordingJob: Job? = null

    @Volatile
    private var playbackMeterJob: Job? = null
    private var playbackTickJob: Job? = null

    @Volatile
    private var activeTake: ActiveRecording? = null

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var backingPlayer: MediaPlayer? = null

    @Volatile
    private var playbackStopJob: Job? = null

    @Volatile
    private var pcmTrack: AudioTrack? = null

    @Volatile
    private var pcmPlayJob: Job? = null

    @Volatile
    private var armedRequest: ArmRequest? = null

    @Volatile
    private var liveChain = LiveMonitorChain()

    @Volatile
    private var capturePaused = false

    @Volatile
    private var pauseStartedMs = 0L

    @Volatile
    private var pauseAccumMs = 0L

    @Volatile
    private var countInJob: Job? = null

    @Volatile
    private var clickTrack: AudioTrack? = null

    @Volatile
    private var monitorTrack: AudioTrack? = null

    @Volatile
    private var echoCanceler: AcousticEchoCanceler? = null

    @Volatile
    private var draftModePath: String? = null

    private data class ActiveRecording(
        val sessionId: String,
        val takeId: String,
        val relativePath: String,
        val wavFile: File,
        val sampleRate: Int,
        var startedAtMs: Long,
        val appendExisting: Boolean = false,
        @Volatile var maxPeak: Float = 0f,
        @Volatile var totalAudioBytes: Long = 0L,
    )

    override suspend fun arm(request: ArmRequest): MonitorDecision {
        armedRequest = request
        liveChain = liveChain.copy(
            voiceGain = request.voiceGain.coerceIn(0f, 2f),
            trackGain = request.trackGain.coerceIn(0f, 2f),
            reverb = request.monitorReverb.coerceIn(0f, 1f),
            delay = request.monitorDelay.coerceIn(0f, 1f),
        )
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
                    positionMs = request.punchStartMs,
                    voiceGain = liveChain.voiceGain,
                    trackGain = liveChain.trackGain,
                )
            }
        }
        return decision
    }

    @SuppressLint("MissingPermission")
    override suspend fun startTake(): TakeControl = withContext(Dispatchers.IO) {
        transport.withLock {
        val current = states.value
        if (current.status.isLive() || recordingJob?.isActive == true) {
            val runningId = activeTake?.takeId ?: return@withLock TakeControl.Started("counting")
            return@withLock TakeControl.Started(runningId)
        }
        stopPlayback()
        countInJob?.cancel()
        val sessionId = current.sessionId ?: return@withLock TakeControl.TransportUnavailable
        val decision = current.decision ?: MonitorDecision.ALLOW
        if (decision == MonitorDecision.BLOCK) {
            return@withLock TakeControl.Refused(decision)
        }
        val request = armedRequest
        val takeId = ids.newId()
        val relativePath = SessionLayout.draftWav()
        val wavFile = File(File(projectsRoot, sessionId), relativePath)
        wavFile.parentFile?.mkdirs()
        val sampleRate = 48_000
        val recover = draftModePath != null
        val append = recover && wavFile.isFile && wavFile.length() > 44L
        val active = ActiveRecording(
            sessionId = sessionId,
            takeId = takeId,
            relativePath = relativePath,
            wavFile = wavFile,
            sampleRate = sampleRate,
            startedAtMs = System.currentTimeMillis(),
            appendExisting = append,
            totalAudioBytes = if (append) (wavFile.length() - 44L).coerceAtLeast(0L) else 0L,
        )
        activeTake = active
        capturePaused = false
        pauseStartedMs = 0L
        pauseAccumMs = 0L

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
                    liveTakeId = takeId,
                    captureFailed = false,
                    maxPeak = 0f,
                )
            }
            countInJob = scope.launch {
                val bedJob = async { loadBackingBed(request, sessionId, sampleRate) }
                for (beat in countInBeats downTo 1) {
                    if (!isActive) return@launch
                    states.update { state ->
                        state.copy(
                            status = EngineStatus.COUNTING,
                            countInBeat = beat,
                            positionMs = request?.punchStartMs ?: 0L,
                        )
                    }
                    playClick(sampleRate, accent = beat == countInBeats)
                    delay(beatMs)
                }
                if (isActive) {
                    val opened = runCatching { openCapture(sampleRate, request) }.getOrElse { err ->
                        emitError(mapOpenError(err))
                        null
                    }
                    if (opened != null) {
                        beginCapture(opened, active, request, bedJob.await())
                    } else {
                        activeTake = null
                        emitError(AudioEngineError.DeviceBusy)
                        states.update {
                            it.copy(
                                status = EngineStatus.ARMED,
                                countInBeat = 0,
                                liveTakeId = null,
                                captureFailed = true,
                                lastError = AudioEngineError.DeviceBusy,
                            )
                        }
                    }
                }
            }
            return@withLock TakeControl.Started(takeId)
        }

        val capture = runCatching { openCapture(sampleRate, request) }.getOrElse { err ->
            emitError(mapOpenError(err))
            null
        } ?: run {
            activeTake = null
            emitError(AudioEngineError.DeviceBusy)
            return@withLock TakeControl.TransportUnavailable
        }
        beginCapture(capture, active, request, loadBackingBed(request, sessionId, sampleRate))
        TakeControl.Started(takeId)
        }
    }

    override fun pauseTake() {
        if (states.value.status != EngineStatus.RECORDING) return
        capturePaused = true
        pauseStartedMs = System.currentTimeMillis()
        OboeMic.setPaused(true)
        runCatching { mediaPlayer?.pause() }
        states.update { it.copy(status = EngineStatus.PAUSED) }
    }

    override fun resumeTake() {
        if (states.value.status != EngineStatus.PAUSED) return
        if (pauseStartedMs > 0L) {
            pauseAccumMs += System.currentTimeMillis() - pauseStartedMs
            pauseStartedMs = 0L
        }
        capturePaused = false
        OboeMic.setPaused(false)
        runCatching { mediaPlayer?.start() }
        states.update { it.copy(status = EngineStatus.RECORDING) }
    }

    override fun recover() {
        OboeMic.stop()
        capturePaused = false
        disarm()
        val request = armedRequest
        if (request != null) {
            states.update {
                it.copy(
                    status = EngineStatus.ARMED,
                    sessionId = request.sessionId,
                    output = request.output,
                    lastError = null,
                    captureFailed = false,
                    transportConnected = true,
                )
            }
        }
    }

    private fun mapOpenError(err: Throwable): AudioEngineError = when {
        err is SecurityException -> AudioEngineError.PermissionDenied
        err.message?.contains("permission", ignoreCase = true) == true -> AudioEngineError.PermissionDenied
        err.message?.contains("busy", ignoreCase = true) == true -> AudioEngineError.DeviceBusy
        else -> AudioEngineError.DeviceBusy
    }

    private fun emitError(error: AudioEngineError) {
        errorEvents.tryEmit(error)
        states.update { it.copy(lastError = error) }
    }

    override fun seekTake(positionMs: Long) {
        val status = states.value.status
        if (status == EngineStatus.RECORDING || status == EngineStatus.COUNTING) return
        val ms = positionMs.coerceAtLeast(0L)
        armedRequest = armedRequest?.copy(punchStartMs = ms)
        states.update { it.copy(positionMs = ms) }
        OboeMic.seek(((ms * 48_000L) / 1000L).toInt())
    }

    override fun seekPlayback(positionMs: Long) {
        val ms = positionMs.coerceAtLeast(0L)
        runCatching { mediaPlayer?.seekTo(ms.toInt()) }
        states.update { it.copy(positionMs = ms) }
    }

    override fun setVoiceGain(gain: Float) {
        liveChain = liveChain.copy(voiceGain = gain.coerceIn(0f, 2f))
        pushMonitor()
        states.update { it.copy(voiceGain = liveChain.voiceGain) }
    }

    override fun setTrackGain(gain: Float) {
        liveChain = liveChain.copy(trackGain = gain.coerceIn(0f, 2f))
        pushMonitor()
        states.update { it.copy(trackGain = liveChain.trackGain) }
    }

    override fun setLiveEffect(effect: LiveMonitorEffect) {
        liveChain = liveChain.apply(effect)
        pushMonitor()
        when (effect) {
            is LiveMonitorEffect.Reverb -> OboeMic.setEffect(2, floatArrayOf(effect.roomSize, effect.damp))
            is LiveMonitorEffect.Eq -> OboeMic.setEffect(3, floatArrayOf(effect.low, effect.mid, effect.high))
            is LiveMonitorEffect.Gate -> OboeMic.setEffect(4, floatArrayOf(effect.threshold))
            is LiveMonitorEffect.Compress -> OboeMic.setEffect(5, floatArrayOf(effect.amount))
            is LiveMonitorEffect.Pitch -> OboeMic.setEffect(3, floatArrayOf(1f, 1.15f, 1.4f))
            LiveMonitorEffect.None -> OboeMic.setEffect(0, floatArrayOf())
        }
    }

    override fun enableDraftMode(draftPath: String) {
        draftModePath = draftPath.ifBlank { SessionLayout.draftWav() }
    }

    override fun getDraftPath(): String? = draftModePath

    private fun pushMonitor() {
        OboeMic.setMonitor(liveChain.reverb, liveChain.delay, liveChain.voiceGain, liveChain.trackGain)
    }

    private fun loadBackingBed(request: ArmRequest?, sessionId: String, sampleRate: Int): ShortArray? {
        val relative = request?.backingRelativePath ?: return null
        val file = File(File(projectsRoot, sessionId), relative)
        if (!file.isFile) return null
        val pitch = Math.pow(2.0, (request.keyShiftSemitones) / 12.0).toFloat()
        val speed = request.playbackRate.coerceIn(0.7f, 1.35f)
        return BackingPcm.loadMono48k(file, sampleRate)?.let { BackingPcm.stretch(it, speed * pitch) }
    }

    private fun wantsVoicePerformance(request: ArmRequest?): Boolean =
        !request?.backingRelativePath.isNullOrBlank() ||
            request?.metronomeEnabled == true ||
            request?.monitorEnabled == true

    private fun openCapture(sampleRate: Int, request: ArmRequest?): MicCapture? {
        val voice = wantsVoicePerformance(request)
        val mode = OboeMic.start(sampleRate, voice, monitor = request?.monitorEnabled == true)
        if (mode > 0) {
            return object : MicCapture {
                override val liveMix: Boolean = mode == 2
                override fun read(dest: ShortArray, length: Int): Int = OboeMic.read(dest, length)
                override fun release() = OboeMic.stop()
            }
        }
        return openAudioRecordCapture(sampleRate, request)
    }

    @SuppressLint("MissingPermission")
    private fun openAudioRecordCapture(sampleRate: Int, request: ArmRequest?): MicCapture? {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) return null
        val bufferSize = max(minBuffer * 2, 8192)
        val wantsEchoCancel = wantsVoicePerformance(request)
        val sources = buildList {
            if (wantsEchoCancel && Build.VERSION.SDK_INT >= 31) {
                add(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
            }
            add(MediaRecorder.AudioSource.MIC)
        }
        for (source in sources) {
            val recorder = try {
                AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                )
            } catch (_: Throwable) {
                null
            } ?: continue
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                continue
            }
            val started = try {
                recorder.startRecording()
                attachEchoCancel(recorder, wantsEchoCancel)
                true
            } catch (_: Throwable) {
                releaseCaptureEffects()
                recorder.release()
                false
            }
            if (!started) continue
            return object : MicCapture {
                override val liveMix: Boolean = false
                override fun read(dest: ShortArray, length: Int): Int = recorder.read(dest, 0, length)
                override fun release() {
                    releaseCaptureEffects()
                    runCatching { recorder.stop() }
                    recorder.release()
                }
            }
        }
        return null
    }

    private fun attachEchoCancel(recorder: AudioRecord, enabled: Boolean) {
        releaseCaptureEffects()
        if (!enabled || !AcousticEchoCanceler.isAvailable()) return
        echoCanceler = runCatching {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.also { it.enabled = true }
        }.getOrNull()
    }

    private fun releaseCaptureEffects() {
        val canceler = echoCanceler
        echoCanceler = null
        if (canceler != null) {
            runCatching { canceler.enabled = false }
            runCatching { canceler.release() }
        }
    }

    private fun clicksStayInHeadphones(request: ArmRequest?): Boolean {
        val output = request?.output ?: return false
        return output == GearRoute.WIRED || output == GearRoute.USB
    }

    private fun beginCapture(
        capture: MicCapture,
        active: ActiveRecording,
        request: ArmRequest?,
        preloadedBed: ShortArray? = null,
    ) {
        val backingRelative = request?.backingRelativePath
        val backingFile = backingRelative?.let { File(File(projectsRoot, active.sessionId), it) }
        val liveMix = capture.liveMix
        var backingInOboe = false
        if (liveMix) {
            val bed = preloadedBed ?: if (backingFile?.isFile == true) {
                loadBackingBed(request, active.sessionId, active.sampleRate)
            } else {
                null
            }
            if (bed != null) {
                val startFrame = ((request?.punchStartMs ?: 0L) * active.sampleRate / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                OboeMic.setBacking(bed, request?.loopBacking == true, startFrame)
                backingInOboe = true
            } else if (backingFile?.isFile == true) {
                emitError(AudioEngineError.TrackDecodeFailed)
            }
        }
        if (!backingInOboe && backingFile?.isFile == true) {
            runCatching {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(backingFile.absolutePath)
                    isLooping = request?.loopBacking == true
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

        states.update {
            it.copy(
                status = EngineStatus.RECORDING,
                countInBeat = 0,
                peak = 0f,
                elapsedMs = 0L,
                playingRelativePath = null,
                metrics = VocalMetrics(),
                liveTakeId = active.takeId,
                captureFailed = false,
                maxPeak = 0f,
            )
        }
        val priorMs = if (active.appendExisting) {
            (active.totalAudioBytes * 1000L) / (active.sampleRate * 2L).coerceAtLeast(1L)
        } else {
            0L
        }
        active.startedAtMs = System.currentTimeMillis() - priorMs

        val metronomeEnabled = request?.metronomeEnabled == true
        val tempoBpm = (request?.tempoBpm ?: 80.0).coerceIn(40.0, 200.0)
        val beatSamples = ((active.sampleRate * 60.0) / tempoBpm).toInt().coerceAtLeast(1)
        val rootSemi = MusicalKeys.semitoneOf(request?.rootKeyName)
        val sampleRate = active.sampleRate
        val wavFile = active.wavFile
        val monitorEnabled = request?.monitorEnabled == true
        val monitorPreset = request?.monitorPreset
        val monitorReverb = ((request?.monitorReverb ?: 0f) + when (monitorPreset) {
            VocalPreset.STAGE -> 0.28f
            VocalPreset.CLOSE -> 0.10f
            VocalPreset.RADIO -> 0.08f
            else -> 0f
        }).coerceIn(0f, 1f)
        val monitorDelay = ((request?.monitorDelay ?: 0f) + when (monitorPreset) {
            VocalPreset.STAGE -> 0.18f
            VocalPreset.CLOSE -> 0.06f
            VocalPreset.RADIO -> 0.04f
            else -> 0f
        }).coerceIn(0f, 1f)
        liveChain = liveChain.copy(
            reverb = monitorReverb,
            delay = monitorDelay,
            voiceGain = (request?.voiceGain ?: liveChain.voiceGain).coerceIn(0f, 2f),
            trackGain = (request?.trackGain ?: liveChain.trackGain).coerceIn(0f, 2f),
            gate = when (monitorPreset) {
                VocalPreset.RADIO -> 0.04f
                VocalPreset.CLOSE -> 0.03f
                else -> 0.02f
            },
            compress = when (monitorPreset) {
                VocalPreset.RADIO -> 0.45f
                VocalPreset.CLOSE -> 0.28f
                VocalPreset.STAGE -> 0.16f
                else -> 0f
            },
            eqLow = when (monitorPreset) {
                VocalPreset.RADIO -> 0.75f
                VocalPreset.CLOSE -> 0.9f
                else -> 1f
            },
            eqHigh = when (monitorPreset) {
                VocalPreset.STAGE, VocalPreset.RADIO -> 1.25f
                else -> 1f
            },
        )
        if (liveMix) {
            pushMonitor()
            OboeMic.setEffect(4, floatArrayOf(liveChain.gate))
            OboeMic.setEffect(3, floatArrayOf(liveChain.eqLow, liveChain.eqMid, liveChain.eqHigh))
            OboeMic.setEffect(5, floatArrayOf(liveChain.compress))
        } else if (monitorEnabled) {
            openMonitorTrack(sampleRate)
        }

        recordingJob = scope.launch {
            RandomAccessFile(wavFile, "rw").use { raf ->
                if (active.appendExisting && raf.length() > 44L) {
                    raf.seek(raf.length())
                } else {
                    raf.setLength(0L)
                    writeWavHeader(raf, sampleRate, 0L)
                }
                val shorts = ShortArray(2048)
                val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                val delayLine = FloatArray(((sampleRate * 0.18).toInt()).coerceAtLeast(1))
                val monitorState = intArrayOf(0)
                val reverbState = floatArrayOf(0f, 0f, 0f, 0f)
                val liveCleaner = LiveCaptureCleaner(sampleRate)
                val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.MODEL.contains("sdk", ignoreCase = true) ||
                    Build.HARDWARE.contains("ranchu", ignoreCase = true)
                val scaleFreqs = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
                var sampleCursor = 0L
                var lastUiUpdateMs = 0L
                var lastHeaderMs = 0L
                var emptyReads = 0
                var restartedOnce = false
                try {
                    while (isActive) {
                        val read = capture.read(shorts, shorts.size)
                        if (read <= 0 && capture.liveMix && !OboeMic.isRunning() && !restartedOnce) {
                            emptyReads += 1
                            if (emptyReads > 12) {
                                restartedOnce = true
                                emitError(AudioEngineError.StreamFailed(-1))
                            }
                        } else if (read > 0) {
                            emptyReads = 0
                        }
                        if (capturePaused) {
                            delay(20)
                            continue
                        }
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
                            liveCleaner.process(shorts, read)
                            val previousCursor = sampleCursor
                            sampleCursor += read.toLong()
                            if (
                                metronomeEnabled &&
                                clicksStayInHeadphones(request) &&
                                previousCursor / beatSamples != sampleCursor / beatSamples
                            ) {
                                playClick(sampleRate, accent = (sampleCursor / beatSamples) % 4L == 0L)
                            }
                            for (i in 0 until read) {
                                val sample = shorts[i]
                                byteBuffer.putShort(sample)
                                val magnitude = abs(sample.toInt())
                                if (magnitude > peakSample) peakSample = magnitude
                            }
                            try {
                                raf.write(byteBuffer.array(), 0, read * 2)
                            } catch (io: IOException) {
                                emitError(AudioEngineError.DiskFull)
                                break
                            }
                            active.totalAudioBytes += (read * 2).toLong()
                            if (monitorEnabled && !liveMix) {
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
                            if (nowMs - lastHeaderMs >= 2_000L) {
                                lastHeaderMs = nowMs
                                val here = raf.filePointer
                                writeWavHeader(raf, sampleRate, active.totalAudioBytes)
                                raf.seek(here)
                            }
                            if (nowMs - lastUiUpdateMs >= 95L) {
                                lastUiUpdateMs = nowMs
                                val extraPause = if (capturePaused && pauseStartedMs > 0L) {
                                    nowMs - pauseStartedMs
                                } else {
                                    0L
                                }
                                val elapsed = (nowMs - active.startedAtMs - pauseAccumMs - extraPause).coerceAtLeast(0L)
                                val positionMs = (request?.punchStartMs ?: 0L) + elapsed
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
                                        positionMs = positionMs,
                                        metrics = updatedMetrics,
                                        liveTakeId = active.takeId,
                                        maxPeak = active.maxPeak,
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    capture.release()
                    releaseCaptureEffects()
                    writeWavHeader(raf, sampleRate, active.totalAudioBytes)
                }
            }
        }
    }

    override suspend fun stopTake(): FinishedTake? = withContext(Dispatchers.IO) {
        transport.withLock {
        capturePaused = false
        OboeMic.setPaused(false)
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
        releaseCaptureEffects()
        states.update {
            it.copy(
                status = EngineStatus.ARMED,
                peak = 0f,
                elapsedMs = 0L,
                countInBeat = 0,
                liveTakeId = null,
            )
        }
        if (active == null || !active.wavFile.isFile || active.totalAudioBytes <= 0L) {
            return@withLock null
        }
        val durationMs = ((active.totalAudioBytes * 1000L) / (active.sampleRate * 2L)).coerceAtLeast(1L)
        val destRelative = SessionLayout.takeWav(active.takeId)
        val destFile = File(File(projectsRoot, active.sessionId), destRelative)
        destFile.parentFile?.mkdirs()
        val stored = if (active.wavFile.absolutePath != destFile.absolutePath) {
            val moved = active.wavFile.renameTo(destFile) || runCatching {
                active.wavFile.copyTo(destFile, overwrite = true)
                active.wavFile.delete()
                destFile.isFile
            }.getOrDefault(false)
            if (moved) destRelative else active.relativePath
        } else {
            active.relativePath
        }
        draftModePath = null
        FinishedTake(
            relativePath = stored,
            durationMs = durationMs,
            peak = active.maxPeak,
            takeId = active.takeId,
        )
        }
    }

    override suspend fun playClip(
        sessionId: String,
        relativePath: String,
        loop: Boolean,
        startMs: Long,
        durationMs: Long,
    ): Unit = withContext(Dispatchers.IO) {
        if (states.value.playingRelativePath == relativePath && backingPlayer == null) {
            stopPlayback()
            return@withContext
        }
        openPlayer(
            sessionId = sessionId,
            relativePath = relativePath,
            loop = loop,
            startMs = startMs,
            durationMs = durationMs,
            asBacking = false,
        )
    }

    override suspend fun playWithBacking(
        sessionId: String,
        vocalRelativePath: String,
        vocalStartMs: Long,
        vocalDurationMs: Long,
        backingRelativePath: String?,
        backingStartMs: Long,
        loopBacking: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        if (states.value.playingRelativePath == vocalRelativePath) {
            stopPlayback()
            return@withContext
        }
        openPlayer(
            sessionId = sessionId,
            relativePath = vocalRelativePath,
            loop = false,
            startMs = vocalStartMs,
            durationMs = vocalDurationMs,
            asBacking = false,
        )
        if (!backingRelativePath.isNullOrBlank()) {
            openPlayer(
                sessionId = sessionId,
                relativePath = backingRelativePath,
                loop = loopBacking,
                startMs = backingStartMs,
                durationMs = 0L,
                asBacking = true,
            )
        }
    }

    override suspend fun playPcm(
        sessionId: String,
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int,
        loop: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        if (states.value.playingRelativePath == "preview-mix") {
            stopPlayback()
            return@withContext
        }
        stopPlayback()
        val frames = minOf(left.size, right.size)
        if (frames < 64) return@withContext
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return@withContext
        val track = AudioTrack.Builder()
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
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuffer, sampleRate * 4))
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED || track.channelCount != 2) {
            track.release()
            return@withContext
        }
        if (track.sampleRate != sampleRate && track.sampleRate > 0) {
            runCatching {
                track.playbackParams = track.playbackParams.setSpeed(
                    sampleRate.toFloat() / track.sampleRate.toFloat(),
                )
            }
        }
        pcmTrack = track
        track.play()
        states.update { it.copy(playingRelativePath = "preview-mix") }
        pcmPlayJob = scope.launch {
            val chunk = ShortArray(2048)
            do {
                var offset = 0
                while (isActive && pcmTrack === track && offset < frames) {
                    val count = minOf(1024, frames - offset)
                    var w = 0
                    for (i in 0 until count) {
                        chunk[w++] = (left[offset + i].coerceIn(-1f, 1f) * 22000f).toInt()
                            .coerceIn(-32767, 32767).toShort()
                        chunk[w++] = (right[offset + i].coerceIn(-1f, 1f) * 22000f).toInt()
                            .coerceIn(-32767, 32767).toShort()
                    }
                    track.write(chunk, 0, w)
                    offset += count
                }
            } while (loop && isActive && pcmTrack === track)
            if (pcmTrack === track) stopPlayback()
        }
    }

    private fun openPlayer(
        sessionId: String,
        relativePath: String,
        loop: Boolean,
        startMs: Long,
        durationMs: Long,
        asBacking: Boolean,
    ) {
        if (!asBacking) {
            stopPlayback()
        } else {
            releaseBackingPlayer()
        }
        val file = File(File(projectsRoot, sessionId), relativePath)
        if (!file.isFile) return
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = loop
                if (!loop && !asBacking) {
                    setOnCompletionListener { stopPlayback() }
                }
                prepare()
                val seek = startMs.toInt().coerceAtLeast(0)
                if (seek > 0) seekTo(seek)
                start()
            }
            if (asBacking) {
                backingPlayer = player
            } else {
                mediaPlayer = player
                states.update {
                    it.copy(
                        playingRelativePath = relativePath,
                        metrics = VocalMetrics(),
                    )
                }
                if (durationMs > 0L && !loop) {
                    playbackStopJob?.cancel()
                    playbackStopJob = scope.launch {
                        delay(durationMs)
                        if (mediaPlayer === player) stopPlayback()
                    }
                }
                startPlaybackClock(player)
                if (file.extension.equals("wav", ignoreCase = true) && file.length() > 44L) {
                    startWavPlaybackMeter(file, player)
                }
            }
        }
    }

    private fun startPlaybackClock(player: MediaPlayer) {
        playbackTickJob?.cancel()
        playbackTickJob = scope.launch {
            while (isActive && mediaPlayer === player) {
                val pos = runCatching {
                    if (player.isPlaying) player.currentPosition.toLong() else -1L
                }.getOrDefault(-1L)
                if (pos < 0L) break
                states.update { it.copy(positionMs = pos) }
                delay(80L)
            }
        }
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
        playbackStopJob?.cancel()
        playbackStopJob = null
        playbackMeterJob?.cancel()
        playbackMeterJob = null
        playbackTickJob?.cancel()
        playbackTickJob = null
        pcmPlayJob?.cancel()
        pcmPlayJob = null
        val pcm = pcmTrack
        pcmTrack = null
        if (pcm != null) {
            runCatching { pcm.stop() }
            runCatching { pcm.release() }
        }
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        releaseBackingPlayer()
        states.update { it.copy(playingRelativePath = null, positionMs = 0L) }
    }

    private fun releaseBackingPlayer() {
        val player = backingPlayer
        backingPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
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
        releaseCaptureEffects()
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
        val hp = 0.986f
        var prevX = reverbState.getOrElse(1) { 0f }
        var prevY = reverbState.getOrElse(2) { 0f }
        var noiseEnv = reverbState.getOrElse(3) { 0f }
        for (i in 0 until length) {
            val incoming = shorts[i] / 32768f
            val dry = hp * (prevY + incoming - prevX)
            prevX = incoming
            prevY = dry
            val mag = abs(dry)
            noiseEnv = if (mag > noiseEnv) noiseEnv + 0.22f * (mag - noiseEnv) else noiseEnv * 0.996f
            val gated = if (noiseEnv < 0.016f) dry * (noiseEnv / 0.016f) else dry
            val delayed = delayLine[idx]
            delayLine[idx] = gated + delayed * 0.25f
            idx = (idx + 1) % delayLine.size
            rev = rev * 0.92f + gated * 0.08f
            val mixed = (gated + delayed * delayMix + rev * reverbMix).coerceIn(-1f, 1f)
            wet.putShort((mixed * 22000f).toInt().coerceIn(-32767, 32767).toShort())
        }
        if (reverbState.size >= 4) {
            reverbState[1] = prevX
            reverbState[2] = prevY
            reverbState[3] = noiseEnv
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
