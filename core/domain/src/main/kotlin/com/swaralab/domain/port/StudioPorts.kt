package com.swaralab.domain.port

import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.policy.MonitorDecision
import kotlinx.coroutines.flow.StateFlow

data class SessionSummary(
    val id: String,
    val title: String,
    val kind: SessionKind,
    val updatedAtEpochMs: Long,
)

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
    RECORDING,
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
)

data class ArmRequest(
    val sessionId: String,
    val output: GearRoute,
    val sampleRate: Int = 48_000,
    val backingRelativePath: String? = null,
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
)

/**
 * Real-time boundary. Implementations may open Oboe streams. Callers stay on the use-case side.
 * The audio callback must not allocate, lock, or touch the session file. A writer thread flushes WAV.
 */
interface AudioEngine {
    val state: StateFlow<AudioEngineState>

    suspend fun arm(request: ArmRequest): MonitorDecision

    suspend fun startTake(): TakeControl

    suspend fun stopTake(): FinishedTake?

    suspend fun playClip(sessionId: String, relativePath: String) {}

    fun stopPlayback() {}

    fun disarm()
}

data class ExportRequest(
    val target: PublishTarget,
)

sealed interface ExportResult {
    data class Written(
        val relativePath: String,
        val durationMs: Long,
    ) : ExportResult

    data class Waiting(val message: String) : ExportResult
}

/**
 * Offline mix and mux. Review playback and export share this graph so the file matches the preview.
 */
interface RenderEngine {
    suspend fun export(document: SessionDocument, request: ExportRequest): ExportResult
}
