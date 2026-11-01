package com.swaralab.domain.model

import com.swaralab.domain.format.SessionFormat
import kotlinx.serialization.Serializable

@Serializable
enum class SessionKind {
    PRACTICE,
    SONG,
    VIDEO,
}

fun defaultTitle(kind: SessionKind): String = when (kind) {
    SessionKind.PRACTICE -> "Practice"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

@Serializable
enum class ScaleMode {
    MAJOR,
    MINOR,
}

@Serializable
data class MusicalSettings(
    val tempoBpm: Double? = null,
    val keyName: String? = null,
    val mode: ScaleMode? = null,
    val timeSignature: String = "4/4",
    val rangeProfileId: String? = null,
)

@Serializable
enum class GearRoute {
    UNKNOWN,
    BUILTIN_MIC,
    WIRED,
    USB,
    BLUETOOTH,
    SPEAKER,
}

@Serializable
data class GearSnapshot(
    val input: GearRoute = GearRoute.UNKNOWN,
    val output: GearRoute = GearRoute.UNKNOWN,
)

fun GearRoute.recordingSnapshot(): GearSnapshot = when (this) {
    GearRoute.SPEAKER -> GearSnapshot(input = GearRoute.BUILTIN_MIC, output = GearRoute.SPEAKER)
    GearRoute.BLUETOOTH -> GearSnapshot(input = GearRoute.BUILTIN_MIC, output = GearRoute.BLUETOOTH)
    GearRoute.UNKNOWN -> GearSnapshot()
    else -> GearSnapshot(input = this, output = this)
}

@Serializable
data class LatencyCalibration(
    val measuredRoundTripMs: Double? = null,
    val compensationMs: Double = 0.0,
    val calibratedAtEpochMs: Long? = null,
)

@Serializable
data class RightsAttestation(
    val backingImported: Boolean,
    val singerConfirmedRights: Boolean,
    val note: String = "",
    val attestedAtEpochMs: Long,
)

@Serializable
enum class TrackRole {
    BACKING,
    LEAD,
    DOUBLE,
    HARMONY,
    ADLIB,
    GUIDE,
    MIDI_SKETCH,
    METRONOME,
}

@Serializable
data class ClipState(
    val id: String,
    val sourceRelativePath: String,
    val timelineStartMs: Long,
    val sourceInMs: Long,
    val durationMs: Long,
    val label: String = "",
)

@Serializable
data class TrackState(
    val id: String,
    val role: TrackRole,
    val name: String,
    val gainDb: Float = 0f,
    val monitorGainDb: Float = 0f,
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val clips: List<ClipState> = emptyList(),
)

@Serializable
enum class LyricAlignment {
    NONE,
    MANUAL,
    LINE,
    WORD,
}

@Serializable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

@Serializable
data class LyricLine(
    val id: String,
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
)

@Serializable
data class LyricSheet(
    val rawText: String = "",
    val lines: List<LyricLine> = emptyList(),
    val alignment: LyricAlignment = LyricAlignment.NONE,
)

@Serializable
data class SectionMarker(
    val id: String,
    val name: String,
    val startMs: Long,
    val endMs: Long? = null,
)

@Serializable
enum class VocalPreset {
    RAW,
    CLOSE,
    STAGE,
    RADIO,
    CUSTOM,
}

@Serializable
data class VocalChain(
    val preset: VocalPreset = VocalPreset.RAW,
    val enabled: Boolean = true,
    val denoise: Float = 0f,
    val breathReduction: Float = 0f,
    val deEss: Float = 0f,
    val compression: Float = 0f,
    val rider: Float = 0f,
    val backingDuckDb: Float = 0f,
    val reverb: Float = 0f,
    val delay: Float = 0f,
    val eqLowDb: Float = 0f,
    val eqPresenceDb: Float = 0f,
    val eqAirDb: Float = 0f,
    val outputGainDb: Float = 0f,
) {
    companion object {
        fun preset(preset: VocalPreset): VocalChain = when (preset) {
            VocalPreset.RAW -> VocalChain(preset = VocalPreset.RAW)
            VocalPreset.CLOSE -> VocalChain(
                preset = VocalPreset.CLOSE,
                denoise = 0.45f,
                deEss = 0.3f,
                compression = 0.35f,
                reverb = 0.12f,
            )
            VocalPreset.STAGE -> VocalChain(
                preset = VocalPreset.STAGE,
                denoise = 0.3f,
                compression = 0.4f,
                reverb = 0.45f,
                delay = 0.15f,
            )
            VocalPreset.RADIO -> VocalChain(
                preset = VocalPreset.RADIO,
                denoise = 0.55f,
                deEss = 0.45f,
                compression = 0.6f,
                rider = 0.4f,
                backingDuckDb = 3f,
                reverb = 0.2f,
            )
            VocalPreset.CUSTOM -> VocalChain(preset = VocalPreset.CUSTOM)
        }
    }
}

@Serializable
enum class PublishTarget {
    MASTER,
    REELS,
    SHORTS,
    WHATSAPP,
    SQUARE,
    LANDSCAPE,
}

@Serializable
enum class FrameFormat {
    VERTICAL,
    SQUARE,
    LANDSCAPE,
}

@Serializable
data class PublishSettings(
    val target: PublishTarget = PublishTarget.MASTER,
    val loudnessLufs: Float = -14f,
    val truePeakDbtp: Float = -1f,
    val frame: FrameFormat = FrameFormat.VERTICAL,
    val burnLyrics: Boolean = true,
    val includeCredits: Boolean = true,
)

fun PublishSettings.withTarget(target: PublishTarget): PublishSettings {
    val frame = when (target) {
        PublishTarget.SQUARE -> FrameFormat.SQUARE
        PublishTarget.LANDSCAPE -> FrameFormat.LANDSCAPE
        else -> FrameFormat.VERTICAL
    }
    return copy(
        target = target,
        frame = frame,
        loudnessLufs = -14f,
        truePeakDbtp = -1f,
    )
}

data class PixelInsets(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
)

object SafeFrames {
    const val MASTER_WIDTH = 1080
    const val MASTER_HEIGHT = 1920

    fun forTarget(target: PublishTarget): PixelInsets = when (target) {
        PublishTarget.REELS -> PixelInsets(top = 220, bottom = 420, left = 60, right = 140)
        PublishTarget.SHORTS -> PixelInsets(top = 160, bottom = 400, left = 60, right = 150)
        PublishTarget.WHATSAPP -> PixelInsets(top = 120, bottom = 280, left = 48, right = 48)
        PublishTarget.SQUARE -> PixelInsets(top = 48, bottom = 48, left = 48, right = 48)
        PublishTarget.LANDSCAPE -> PixelInsets(top = 60, bottom = 100, left = 48, right = 48)
        PublishTarget.MASTER -> PixelInsets(top = 160, bottom = 400, left = 60, right = 150)
    }

    fun fits(insets: PixelInsets, width: Int = MASTER_WIDTH, height: Int = MASTER_HEIGHT): Boolean {
        return insets.top >= 0 &&
            insets.bottom >= 0 &&
            insets.left >= 0 &&
            insets.right >= 0 &&
            insets.top + insets.bottom < height &&
            insets.left + insets.right < width
    }
}

@Serializable
data class SongwriterNotes(
    val memoRelativePath: String? = null,
    val midiRelativePath: String? = null,
)

/**
 * Snapshot of one performance. Media bytes live beside this file in the session folder.
 * The folder is the source of truth. A database, if added later, only indexes these files.
 */
@Serializable
data class SessionDocument(
    val format: String = SessionFormat.NAME,
    val version: Int = SessionFormat.VERSION,
    val id: String,
    val title: String,
    val kind: SessionKind,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val musical: MusicalSettings = MusicalSettings(),
    val gear: GearSnapshot = GearSnapshot(),
    val latency: LatencyCalibration = LatencyCalibration(),
    val rights: RightsAttestation? = null,
    val tracks: List<TrackState> = emptyList(),
    val lyrics: LyricSheet = LyricSheet(),
    val markers: List<SectionMarker> = emptyList(),
    val chain: VocalChain = VocalChain(),
    val publish: PublishSettings = PublishSettings(),
    val songwriter: SongwriterNotes = SongwriterNotes(),
)
