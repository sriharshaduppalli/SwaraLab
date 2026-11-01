package com.swaralab.domain.usecase

import com.swaralab.domain.command.RejectReason
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricAlignment
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.LyricSheet
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.model.recordingSnapshot
import com.swaralab.domain.model.withTarget
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary

class CreateSession(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(kind: SessionKind, title: String): SessionDocument {
        var document = reducer.create(kind, title)
        if (kind == SessionKind.PRACTICE) {
            val warmupLines = listOf(
                LyricLine(id = "warmup-1", text = "Sa (C4 · 262 Hz) — Steady lower tonic", startMs = 0L),
                LyricLine(id = "warmup-2", text = "Re (D4 · 294 Hz) — Step up smoothly", startMs = 2_000L),
                LyricLine(id = "warmup-3", text = "Ga (E4 · 330 Hz) — Open major third", startMs = 4_000L),
                LyricLine(id = "warmup-4", text = "Ma (F4 · 349 Hz) — Gentle fourth", startMs = 6_000L),
                LyricLine(id = "warmup-5", text = "Pa (G4 · 392 Hz) — Strong fifth resonance", startMs = 8_000L),
                LyricLine(id = "warmup-6", text = "Dha (A4 · 440 Hz) — Clear sixth", startMs = 10_000L),
                LyricLine(id = "warmup-7", text = "Ni (B4 · 494 Hz) — Leading seventh", startMs = 12_000L),
                LyricLine(id = "warmup-8", text = "Sa' (C5 · 523 Hz) — Upper octave tonic", startMs = 14_000L),
            )
            val sheet = LyricSheet(
                rawText = warmupLines.joinToString("\n") { it.text },
                lines = warmupLines,
                alignment = LyricAlignment.LINE,
            )
            val edit = reducer.apply(document, SessionCommand.SetLyrics(sheet))
            if (edit is SessionEdit.Applied) document = edit.document
        } else if (kind == SessionKind.VIDEO) {
            val edit = reducer.apply(
                document,
                SessionCommand.SetPublish(document.publish.withTarget(PublishTarget.REELS)),
            )
            if (edit is SessionEdit.Applied) document = edit.document
        }
        sessions.save(document)
        return document
    }
}

class ListSessions(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(): List<SessionSummary> = sessions.list()
}

class DeleteSession(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String) {
        sessions.delete(id)
    }
}

class LoadSession(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String): SessionDocument? = sessions.load(id)
}

class RenameSession(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, title: String): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.Rename(title))
    }
}

class UpdateGear(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, route: GearRoute): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetGear(route.recordingSnapshot()))
    }
}

class UpdateVocalPreset(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, preset: VocalPreset): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetVocalChain(VocalChain.preset(preset)))
    }
}

class UpdateVocalChain(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        id: String,
        reverb: Float,
        delay: Float,
        compression: Float,
        denoise: Float,
        backingDuckDb: Float,
    ): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val updated = document.chain.copy(
            preset = VocalPreset.CUSTOM,
            reverb = reverb.coerceIn(0f, 1f),
            delay = delay.coerceIn(0f, 1f),
            compression = compression.coerceIn(0f, 1f),
            denoise = denoise.coerceIn(0f, 1f),
            backingDuckDb = backingDuckDb.coerceIn(0f, 12f),
        )
        return applyAndSave(reducer, sessions, document, SessionCommand.SetVocalChain(updated))
    }
}

class UpdateTrackMix(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        trackId: String,
        gainDb: Float,
        mute: Boolean,
    ): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val track = document.tracks.firstOrNull { it.id == trackId }
            ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        return applyAndSave(
            reducer,
            sessions,
            document,
            SessionCommand.SetTrackMix(
                trackId = trackId,
                gainDb = gainDb.coerceIn(-24f, 12f),
                monitorGainDb = track.monitorGainDb,
                mute = mute,
                solo = track.solo,
            ),
        )
    }
}

class UpdateVocalSyncOffset(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        compensationMs: Double,
    ): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val measured = document.latency.measuredRoundTripMs ?: 0.0
        return applyAndSave(
            reducer,
            sessions,
            document,
            SessionCommand.SetLatency(
                measuredRoundTripMs = measured.coerceAtLeast(0.0),
                compensationMs = compensationMs.coerceIn(-300.0, 300.0),
            ),
        )
    }
}

class UpdateLyrics(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(id: String, text: String): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val parsed = parseLyricLines(text, ids)
        val hasTimedLines = parsed.any { it.startMs != null }
        val normalizedRaw = parsed.joinToString("\n") { it.text }
        val sheet = LyricSheet(
            rawText = normalizedRaw,
            lines = parsed,
            alignment = when {
                parsed.isEmpty() -> LyricAlignment.NONE
                hasTimedLines -> LyricAlignment.LINE
                else -> LyricAlignment.MANUAL
            },
        )
        return applyAndSave(reducer, sessions, document, SessionCommand.SetLyrics(sheet))
    }
}

class AddVoicePart(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, role: TrackRole, name: String): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.AddTrack(role, name))
    }
}

class CommitRecordedTake(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        trackId: String?,
        take: FinishedTake,
    ): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val voiceTracks = document.tracks.filter { it.role != TrackRole.BACKING }
        val targetTrack = voiceTracks.firstOrNull { it.id == trackId }
            ?: voiceTracks.firstOrNull()
            ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        val takeNumber = targetTrack.clips.size + 1
        return applyAndSave(
            reducer,
            sessions,
            document,
            SessionCommand.CommitTake(
                trackId = targetTrack.id,
                relativePath = take.relativePath,
                durationMs = take.durationMs.coerceAtLeast(1L),
                label = "${targetTrack.name} · Take $takeNumber",
            ),
        )
    }
}

class DeleteRecordedClip(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, clipId: String): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.DeleteClip(clipId))
    }
}

class ImportBackingTrack(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        relativePath: String,
        durationMs: Long,
        label: String,
    ): SessionEdit {
        var document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        if (document.tracks.none { it.role == TrackRole.BACKING }) {
            val added = reducer.apply(document, SessionCommand.AddTrack(TrackRole.BACKING, "Backing"))
            if (added is SessionEdit.Applied) {
                document = added.document
            }
        }
        val backingTrack = document.tracks.firstOrNull { it.role == TrackRole.BACKING }
            ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        val cleared = document.copy(
            tracks = document.tracks.map { track ->
                if (track.role == TrackRole.BACKING) track.copy(clips = emptyList()) else track
            },
        )
        return applyAndSave(
            reducer,
            sessions,
            cleared,
            SessionCommand.ImportBacking(
                trackId = backingTrack.id,
                relativePath = relativePath,
                durationMs = durationMs.coerceAtLeast(1_000L),
                rightsConfirmed = true,
                note = label,
            ),
        )
    }
}

class UpdatePublishSettings(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, target: PublishTarget): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val settings = document.publish.withTarget(target)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetPublish(settings))
    }
}

private val lrcTimestampRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)$""")
private val lrcMetaRegex = Regex("""^\[[a-zA-Z]+:.*]$""")

internal fun parseLyricLines(raw: String, ids: IdGenerator): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    for (rawLine in raw.replace("\uFEFF", "").lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || lrcMetaRegex.matches(trimmed)) continue
        val match = lrcTimestampRegex.matchEntire(trimmed)
        if (match != null) {
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionRaw = match.groupValues[3]
            val millis = when (fractionRaw.length) {
                0 -> 0L
                1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                else -> fractionRaw.take(3).toLongOrNull() ?: 0L
            }
            val body = match.groupValues[4].trim()
            if (body.isNotEmpty()) {
                val startMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                lines += LyricLine(id = ids.newId(), text = body, startMs = startMs)
            }
        } else {
            lines += LyricLine(id = ids.newId(), text = trimmed)
        }
    }
    return lines
}

private suspend fun applyAndSave(
    reducer: SessionReducer,
    sessions: SessionRepository,
    document: SessionDocument,
    command: SessionCommand,
): SessionEdit {
    val edit = reducer.apply(document, command)
    if (edit is SessionEdit.Applied) sessions.save(edit.document)
    return edit
}
