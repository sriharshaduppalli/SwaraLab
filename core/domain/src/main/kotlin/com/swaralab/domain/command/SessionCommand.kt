package com.swaralab.domain.command

import com.swaralab.domain.model.GearSnapshot
import com.swaralab.domain.model.LyricSheet
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.PracticeStats
import com.swaralab.domain.model.PublishSettings
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SongwriterNotes
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain

sealed interface SessionCommand {
    data class Rename(val title: String) : SessionCommand

    data class SetGear(val gear: GearSnapshot) : SessionCommand

    data class SetLatency(
        val measuredRoundTripMs: Double,
        val compensationMs: Double,
    ) : SessionCommand

    data class ImportBacking(
        val trackId: String,
        val relativePath: String,
        val durationMs: Long,
        val rightsConfirmed: Boolean,
        val note: String = "",
    ) : SessionCommand

    data class CommitTake(
        val trackId: String,
        val relativePath: String,
        val durationMs: Long,
        val timelineStartMs: Long = 0,
        val label: String = "Take",
        val keepPreviousPreferred: Boolean = false,
        val videoRelativePath: String? = null,
        val sourceInMs: Long = 0L,
        val sourceDurationMs: Long = 0L,
    ) : SessionCommand

    data class DeleteClip(
        val clipId: String,
    ) : SessionCommand

    data class TrimClip(
        val clipId: String,
        val sourceInMs: Long,
        val durationMs: Long,
    ) : SessionCommand

    data class PreferClip(
        val clipId: String,
        val preferred: Boolean,
    ) : SessionCommand

    data class SetPractice(
        val stats: PracticeStats,
    ) : SessionCommand

    data class SetTrackMix(
        val trackId: String,
        val gainDb: Float,
        val monitorGainDb: Float,
        val mute: Boolean,
        val solo: Boolean,
        val pan: Float = 0f,
    ) : SessionCommand

    data class SplitClip(
        val clipId: String,
        val atSourceMs: Long,
    ) : SessionCommand

    data class UpdateClip(
        val clipId: String,
        val timelineStartMs: Long? = null,
        val sourceInMs: Long? = null,
        val durationMs: Long? = null,
        val fadeInMs: Long? = null,
        val fadeOutMs: Long? = null,
        val active: Boolean? = null,
        val preferred: Boolean? = null,
    ) : SessionCommand

    data class SetTrackChain(
        val trackId: String,
        val chain: VocalChain?,
    ) : SessionCommand

    data class AddTrack(val role: TrackRole, val name: String) : SessionCommand

    data class SetVocalChain(val chain: VocalChain) : SessionCommand

    data class SetLyrics(val sheet: LyricSheet) : SessionCommand

    data class AddMarker(
        val name: String,
        val startMs: Long,
        val endMs: Long? = null,
    ) : SessionCommand

    data class SetMusical(val settings: MusicalSettings) : SessionCommand

    data class SetPublish(val settings: PublishSettings) : SessionCommand

    data class SetSongwriter(val notes: SongwriterNotes) : SessionCommand
}

enum class RejectReason {
    UNKNOWN_SESSION,
    UNKNOWN_TRACK,
    RIGHTS_REQUIRED,
    UNSAFE_PATH,
    EMPTY_CLIP,
    INVALID_LATENCY,
    BLANK_MARKER,
    INVALID_RANGE,
    BLANK_NAME,
}

sealed interface SessionEdit {
    data class Applied(val document: SessionDocument) : SessionEdit

    data class Rejected(val reason: RejectReason) : SessionEdit
}
