package com.swaralab.domain.audio

import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.port.EngineStatus

/** Imported file on this phone. No community catalog. */
enum class TrackSource {
    LOCAL_IMPORT,
    PUBLIC_DOMAIN,
}

data class ImportedTrack(
    val id: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long,
    val localRelativePath: String,
    val source: TrackSource = TrackSource.LOCAL_IMPORT,
    val hasLyrics: Boolean = false,
)

data class RecordingDraft(
    val track: ImportedTrack?,
    val lyrics: List<LyricLine>,
    val currentPositionMs: Long = 0L,
    val voiceGain: Float = 1.0f,
    val trackGain: Float = 0.8f,
)

sealed class LiveMonitorEffect {
    data class Reverb(val roomSize: Float, val damp: Float = 0.4f) : LiveMonitorEffect()
    data class Eq(val low: Float, val mid: Float, val high: Float) : LiveMonitorEffect()
    data class Gate(val threshold: Float) : LiveMonitorEffect()
    data class Compress(val amount: Float) : LiveMonitorEffect()
    /** Headphone lift only. Does not pitch-shift the dry WAV. */
    data class Pitch(val semitones: Float) : LiveMonitorEffect()
    data object None : LiveMonitorEffect()
}

data class RecoverableDraft(
    val relativePath: String,
    val durationMs: Long,
)

data class LiveMonitorChain(
    val gate: Float = 0.02f,
    val eqLow: Float = 1f,
    val eqMid: Float = 1f,
    val eqHigh: Float = 1f,
    val reverb: Float = 0f,
    val delay: Float = 0f,
    val compress: Float = 0f,
    val voiceGain: Float = 1f,
    val trackGain: Float = 0.8f,
) {
    fun apply(effect: LiveMonitorEffect): LiveMonitorChain = when (effect) {
        is LiveMonitorEffect.Reverb -> copy(reverb = effect.roomSize.coerceIn(0f, 1f), delay = effect.damp.coerceIn(0f, 1f))
        is LiveMonitorEffect.Eq -> copy(
            eqLow = effect.low.coerceIn(0.25f, 4f),
            eqMid = effect.mid.coerceIn(0.25f, 4f),
            eqHigh = effect.high.coerceIn(0.25f, 4f),
        )
        is LiveMonitorEffect.Gate -> copy(gate = effect.threshold.coerceIn(0f, 0.2f))
        is LiveMonitorEffect.Compress -> copy(compress = effect.amount.coerceIn(0f, 1f))
        is LiveMonitorEffect.Pitch -> copy(eqMid = 1.15f, eqHigh = 1.4f)
        LiveMonitorEffect.None -> copy(reverb = 0f, delay = 0f, compress = 0f, gate = 0.02f, eqLow = 1f, eqMid = 1f, eqHigh = 1f)
    }
}

sealed interface StageTakeState {
    data object Idle : StageTakeState
    data class Ready(val draft: RecordingDraft) : StageTakeState
    data class Countdown(val beatsLeft: Int, val draft: RecordingDraft) : StageTakeState
    data class Recording(
        val positionMs: Long,
        val durationMs: Long,
        val lyricIndex: Int,
        val voiceLevel: Float,
    ) : StageTakeState
    data class Paused(val positionMs: Long, val draft: RecordingDraft) : StageTakeState
    data class Review(
        val dryVocalPath: String,
        val durationMs: Long,
        val score: Float? = null,
    ) : StageTakeState
    data class Error(val message: String) : StageTakeState
}

object TimedLyrics {
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int =
        LyricSync.findCurrentLineIndex(lines, positionMs).coerceAtLeast(0)
}

object StageTake {
    fun of(
        status: EngineStatus,
        countInBeat: Int,
        positionMs: Long,
        durationMs: Long,
        peak: Float,
        lyricIndex: Int,
        draft: RecordingDraft,
        dryVocalPath: String? = null,
        reviewDurationMs: Long = 0L,
        error: String? = null,
        captureFailed: Boolean = false,
    ): StageTakeState {
        if (!error.isNullOrBlank()) return StageTakeState.Error(error)
        if (captureFailed) return StageTakeState.Error("Could not open the microphone on this device.")
        return when (status) {
            EngineStatus.COUNTING -> StageTakeState.Countdown(countInBeat.coerceAtLeast(1), draft)
            EngineStatus.PAUSED -> StageTakeState.Paused(positionMs, draft)
            EngineStatus.RECORDING -> StageTakeState.Recording(
                positionMs = positionMs,
                durationMs = durationMs,
                lyricIndex = lyricIndex,
                voiceLevel = peak,
            )
            EngineStatus.BLOCKED -> StageTakeState.Error("Use headphones so the backing track does not bleed into the mic.")
            EngineStatus.IDLE,
            EngineStatus.ARMED,
            -> if (dryVocalPath != null) {
                StageTakeState.Review(dryVocalPath, reviewDurationMs)
            } else {
                StageTakeState.Ready(draft)
            }
        }
    }

    fun canSeek(state: StageTakeState): Boolean =
        state is StageTakeState.Ready || state is StageTakeState.Idle || state is StageTakeState.Paused
}
