package com.swaralab.domain.port

import com.swaralab.domain.audio.LiveMonitorEffect
import com.swaralab.domain.audio.LoopRegion
import com.swaralab.domain.audio.TrackProcessingConfig
import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.policy.MonitorDecision
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class SessionSummary(
    val id: String,
    val title: String,
    val kind: SessionKind,
    val updatedAtEpochMs: Long,
    val takeCount: Int = 0,
    val lastPracticeScore: Int = 0,
)

interface InvitePack {
    fun exportZip(sessionId: String, destination: java.io.File): Boolean

    fun importZip(source: java.io.File): SessionDocument?
}

interface SessionRepository {
    suspend fun save(document: SessionDocument)

    suspend fun load(id: String): SessionDocument?

    suspend fun list(): List<SessionSummary>

    suspend fun delete(id: String)
}

enum class EngineStatus {
    IDLE,
    ARMED,
    BLOCKED,
    COUNTING,
    RECORDING,
    PAUSED,
}

fun EngineStatus.isLive(): Boolean =
    this == EngineStatus.RECORDING || this == EngineStatus.COUNTING || this == EngineStatus.PAUSED

sealed interface AudioEngineError {
    data object DeviceBusy : AudioEngineError
    data object PermissionDenied : AudioEngineError
    data object UnsupportedSampleRate : AudioEngineError
    data class StreamFailed(val code: Int) : AudioEngineError
    data object DiskFull : AudioEngineError
    data object TrackDecodeFailed : AudioEngineError
}

fun AudioEngineError.userMessage(): String = when (this) {
    AudioEngineError.DeviceBusy -> "Another app is using the microphone. Close other audio apps, then retry."
    AudioEngineError.PermissionDenied -> "Microphone permission is required to record."
    AudioEngineError.UnsupportedSampleRate -> "This device would not open a 48 kHz stream."
    is AudioEngineError.StreamFailed -> "Audio interrupted. Resume or restart this pass."
    AudioEngineError.DiskFull -> "Not enough storage space. Free some space, then retry."
    AudioEngineError.TrackDecodeFailed -> "This audio file cannot be used. Pick another song from this phone."
}

enum class ErrorRecovery {
    RETRY,
    PERMISSION,
    PICK_TRACK,
    FREE_SPACE,
    RESTART,
}

fun AudioEngineError.recovery(): ErrorRecovery = when (this) {
    AudioEngineError.DeviceBusy, AudioEngineError.UnsupportedSampleRate -> ErrorRecovery.RETRY
    AudioEngineError.PermissionDenied -> ErrorRecovery.PERMISSION
    AudioEngineError.TrackDecodeFailed -> ErrorRecovery.PICK_TRACK
    AudioEngineError.DiskFull -> ErrorRecovery.FREE_SPACE
    is AudioEngineError.StreamFailed -> ErrorRecovery.RESTART
}

data class AudioEngineState(
    val status: EngineStatus = EngineStatus.IDLE,
    val sessionId: String? = null,
    val output: GearRoute = GearRoute.UNKNOWN,
    val decision: MonitorDecision? = null,
    val transportConnected: Boolean = false,
    val peak: Float = 0f,
    val elapsedMs: Long = 0L,
    val playingRelativePath: String? = null,
    val metrics: VocalMetrics = VocalMetrics(),
    val countInBeat: Int = 0,
    val liveTakeId: String? = null,
    val captureFailed: Boolean = false,
    val maxPeak: Float = 0f,
    val positionMs: Long = 0L,
    val voiceGain: Float = 1f,
    val trackGain: Float = 0.8f,
    val lastError: AudioEngineError? = null,
)

data class ArmRequest(
    val sessionId: String,
    val output: GearRoute,
    val sampleRate: Int = 48_000,
    val backingRelativePath: String? = null,
    val rootKeyName: String = "C",
    val tempoBpm: Double = 80.0,
    val countInEnabled: Boolean = true,
    val metronomeEnabled: Boolean = false,
    val punchStartMs: Long = 0L,
    val punchEndMs: Long = 0L,
    val preRollMs: Long = 0L,
    val playbackRate: Float = 1f,
    val keyShiftSemitones: Int = 0,
    val monitorEnabled: Boolean = false,
    val monitorReverb: Float = 0f,
    val monitorDelay: Float = 0f,
    val monitorPreset: VocalPreset? = null,
    val loopBacking: Boolean = false,
    val voiceGain: Float = 1f,
    val trackGain: Float = 0.8f,
)

sealed interface TakeControl {
    data class Started(val takeId: String) : TakeControl

    data class Refused(val decision: MonitorDecision) : TakeControl

    data object TransportUnavailable : TakeControl
}

data class FinishedTake(
    val relativePath: String,
    val durationMs: Long,
    val peak: Float,
    val takeId: String = relativePath.substringAfterLast('/').removeSuffix(".wav"),
    val videoRelativePath: String? = null,
    val score: Float? = null,
    val aacRelativePath: String? = null,
)

/**
 * Real-time boundary. Implementations may open Oboe streams. Callers stay on the use-case side.
 * The audio callback must not allocate, lock, or touch the session file. A writer thread flushes WAV.
 */
interface AudioEngine {
    val state: StateFlow<AudioEngineState>
    val errors: SharedFlow<AudioEngineError>

    suspend fun arm(request: ArmRequest): MonitorDecision

    suspend fun startTake(): TakeControl

    suspend fun stopTake(): FinishedTake?

    fun pauseTake() {}

    fun resumeTake() {}

    fun recover() {
        disarm()
    }

    /** Seek backing playhead. Ignored while counting. */
    fun seekTake(positionMs: Long) {}

    fun setLoopRegion(region: LoopRegion?, enabled: Boolean) {}

    /** Seek take playback (listen-back). */
    fun seekPlayback(positionMs: Long) {}

    fun setVoiceGain(gain: Float) {}

    fun setTrackGain(gain: Float) {}

    fun setLiveEffect(effect: LiveMonitorEffect) {}

    fun applyVocalChainMonitor(chain: VocalChain, voiceGain: Float = 1f, trackGain: Float = 0.8f) {}

    /** Next [startTake] appends dry PCM onto this session-relative WAV. */
    fun enableDraftMode(draftPath: String) {}

    fun getDraftPath(): String? = null

    suspend fun playClip(
        sessionId: String,
        relativePath: String,
        loop: Boolean = false,
        startMs: Long = 0L,
        durationMs: Long = 0L,
    ) {}

    suspend fun playWithBacking(
        sessionId: String,
        vocalRelativePath: String,
        vocalStartMs: Long = 0L,
        vocalDurationMs: Long = 0L,
        backingRelativePath: String? = null,
        backingStartMs: Long = 0L,
        loopBacking: Boolean = false,
    ) {
        playClip(sessionId, vocalRelativePath, loop = false, startMs = vocalStartMs, durationMs = vocalDurationMs)
    }

    suspend fun playPcm(
        sessionId: String,
        left: FloatArray,
        right: FloatArray = left,
        sampleRate: Int = 48_000,
        loop: Boolean = false,
    ) {}

    fun stopPlayback() {}

    fun disarm()

    /**
     * Offline key/tempo cache. Returns false if decode or write fails.
     * Live dry vocal is never rewritten here.
     */
    suspend fun prepareProcessedTrack(
        originalAbsolutePath: String,
        outputAbsolutePath: String,
        config: TrackProcessingConfig,
        onProgress: (Float) -> Unit = {},
    ): Boolean = false
}

enum class ExportKind {
    MIX,
    VOCALS,
    BACKING,
    COMPACT,
    VIDEO,
    LYRIC_VIDEO,
    STEMS,
}

data class StemSelection(
    val mix: Boolean = true,
    val vocals: Boolean = true,
    val backing: Boolean = true,
    val leadDry: Boolean = true,
    val layers: Boolean = true,
)

data class ExportRequest(
    val target: PublishTarget,
    val kind: ExportKind = ExportKind.MIX,
    val stems: StemSelection = StemSelection(),
)

sealed interface ExportResult {
    data class Written(
        val relativePath: String,
        val durationMs: Long,
        val videoRelativePath: String? = null,
    ) : ExportResult

    data class Waiting(val message: String) : ExportResult
}

data class MixPreview(
    val left: FloatArray,
    val right: FloatArray,
    val sampleRate: Int = 48_000,
)

/**
 * Offline mix and mux. Review playback and export share this graph so the file matches the preview.
 */
sealed interface InstrumentalResult {
    data class Written(val durationMs: Long, val mostlyMono: Boolean) : InstrumentalResult
    data class Failed(val message: String) : InstrumentalResult
}

interface RenderEngine {
    suspend fun export(
        document: SessionDocument,
        request: ExportRequest,
        onProgress: (Float) -> Unit = {},
    ): ExportResult

    suspend fun preview(document: SessionDocument, maxDurationMs: Long = 12_000L): MixPreview? = null

    /** Backing plus other layers for punch-in / live monitor. Excludes muted tracks. */
    suspend fun writeMonitorBed(
        document: SessionDocument,
        destination: java.io.File,
    ): Boolean = false

    suspend fun writeInstrumental(
        source: java.io.File,
        destination: java.io.File,
    ): InstrumentalResult = InstrumentalResult.Failed("Instrumental is not available.")

    /** AAC companion for sharing. Leaves the dry WAV in place. */
    suspend fun encodeCompanionAac(
        wav: java.io.File,
        destination: java.io.File,
    ): Boolean = false
}
