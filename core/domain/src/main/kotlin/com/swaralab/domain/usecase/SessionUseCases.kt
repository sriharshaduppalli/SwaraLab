package com.swaralab.domain.usecase

import com.swaralab.domain.command.RejectReason
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.audio.MusicalKeys
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricAlignment
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.LyricSheet
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.PracticeStats
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.ScaleMode
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
            val warmupLines = warmupLinesForKey("C")
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

class StartSession(
    private val createSession: CreateSession,
    private val listSessions: ListSessions,
) {
    suspend operator fun invoke(kind: SessionKind): String {
        val recentEmpty = listSessions().firstOrNull { summary ->
            summary.kind == kind &&
                summary.takeCount == 0 &&
                System.currentTimeMillis() - summary.updatedAtEpochMs < 24 * 60 * 60 * 1000L
        }
        if (recentEmpty != null) return recentEmpty.id
        val clock = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT),
        )
        val name = when (kind) {
            SessionKind.PRACTICE -> "Practice"
            SessionKind.SONG -> "Song"
            SessionKind.VIDEO -> "Video"
        }
        return createSession(kind, "$name · $clock").id
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
        deEss: Float? = null,
        tone: Float? = null,
        pitchCorrect: Float? = null,
        fadeOut: Float? = null,
        thicken: Float? = null,
        effectAmount: Float? = null,
    ): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val updated = document.chain.copy(
            preset = VocalPreset.CUSTOM,
            reverb = reverb.coerceIn(0f, 1f),
            delay = delay.coerceIn(0f, 1f),
            compression = compression.coerceIn(0f, 1f),
            denoise = denoise.coerceIn(0f, 1f),
            backingDuckDb = backingDuckDb.coerceIn(0f, 12f),
            deEss = (deEss ?: document.chain.deEss).coerceIn(0f, 1f),
            tone = (tone ?: document.chain.tone).coerceIn(-1f, 1f),
            pitchCorrect = (pitchCorrect ?: document.chain.pitchCorrect).coerceIn(0f, 1f),
            fadeOut = (fadeOut ?: document.chain.fadeOut).coerceIn(0f, 1f),
            thicken = (thicken ?: document.chain.thicken).coerceIn(0f, 1f),
            effectAmount = (effectAmount ?: document.chain.effectAmount).coerceIn(0f, 1f),
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
        solo: Boolean? = null,
        pan: Float? = null,
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
                solo = solo ?: track.solo,
                pan = (pan ?: track.pan).coerceIn(-1f, 1f),
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
        timelineStartMs: Long = 0L,
        punchEndMs: Long = 0L,
    ): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val voiceTracks = document.tracks.filter { it.role != TrackRole.BACKING }
        val targetTrack = voiceTracks.firstOrNull { it.id == trackId }
            ?: voiceTracks.firstOrNull()
            ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        val takeNumber = targetTrack.clips.size + 1
        val punchMs = timelineStartMs.coerceAtLeast(0L)
        val windowEnd = punchEndMs.takeIf { it > punchMs } ?: 0L
        val punchLabel = if (punchMs > 0L) " · punch" else ""
        var working = document
        val previous = targetTrack.clips.lastOrNull { it.preferred && it.active }
            ?: targetTrack.clips.lastOrNull { it.active }
        var tail: Triple<String, Long, Long>? = null
        var tailSourceDuration = 0L
        if (punchMs > 0L && previous != null) {
            val keepMs = (punchMs - previous.timelineStartMs).coerceAtLeast(0L)
            val originalEnd = previous.timelineStartMs + previous.durationMs
            if (windowEnd > punchMs && originalEnd > windowEnd) {
                tailSourceDuration = previous.usableSourceDurationMs
                tail = Triple(
                    previous.sourceRelativePath,
                    previous.sourceInMs + (windowEnd - previous.timelineStartMs).coerceAtLeast(0L),
                    originalEnd - windowEnd,
                )
            }
            val parked = applyAndSave(
                reducer,
                sessions,
                working,
                SessionCommand.UpdateClip(clipId = previous.id, active = false, preferred = false),
            )
            if (parked is SessionEdit.Applied) {
                working = parked.document
            }
            if (keepMs >= 250L) {
                val head = applyAndSave(
                    reducer,
                    sessions,
                    working,
                    SessionCommand.CommitTake(
                        trackId = targetTrack.id,
                        relativePath = previous.sourceRelativePath,
                        durationMs = keepMs.coerceAtMost(previous.durationMs),
                        timelineStartMs = previous.timelineStartMs,
                        label = "${targetTrack.name} · kept head",
                        keepPreviousPreferred = true,
                        sourceInMs = previous.sourceInMs,
                        sourceDurationMs = previous.usableSourceDurationMs,
                    ),
                )
                if (head is SessionEdit.Applied) {
                    working = head.document
                }
            }
        }
        val punched = applyAndSave(
            reducer,
            sessions,
            working,
            SessionCommand.CommitTake(
                trackId = targetTrack.id,
                relativePath = take.relativePath,
                durationMs = take.durationMs.coerceAtLeast(1L),
                timelineStartMs = punchMs,
                label = "${targetTrack.name} · Take $takeNumber$punchLabel",
                keepPreviousPreferred = punchMs > 0L,
                videoRelativePath = take.videoRelativePath,
            ),
        )
        if (punched !is SessionEdit.Applied) return punched
        working = punched.document
        val leftover = tail ?: return punched
        if (leftover.third < 250L) return punched
        return applyAndSave(
            reducer,
            sessions,
            working,
            SessionCommand.CommitTake(
                trackId = targetTrack.id,
                relativePath = leftover.first,
                durationMs = leftover.third,
                timelineStartMs = windowEnd,
                label = "${targetTrack.name} · kept tail",
                keepPreviousPreferred = true,
                sourceInMs = leftover.second,
                sourceDurationMs = tailSourceDuration,
            ),
        )
    }
}

class TrimRecordedClip(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        clipId: String,
        sourceInMs: Long,
        durationMs: Long,
    ): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(
            reducer,
            sessions,
            document,
            SessionCommand.TrimClip(clipId, sourceInMs, durationMs),
        )
    }
}

class UpdateRecordedClip(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        clipId: String,
        timelineStartMs: Long? = null,
        fadeInMs: Long? = null,
        fadeOutMs: Long? = null,
        active: Boolean? = null,
        preferred: Boolean? = null,
        sourceInMs: Long? = null,
        durationMs: Long? = null,
    ): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(
            reducer,
            sessions,
            document,
            SessionCommand.UpdateClip(
                clipId = clipId,
                timelineStartMs = timelineStartMs,
                sourceInMs = sourceInMs,
                durationMs = durationMs,
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs,
                active = active,
                preferred = preferred,
            ),
        )
    }
}

class UpdateTrackChain(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, trackId: String, chain: VocalChain?): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetTrackChain(trackId, chain))
    }
}

class SplitRecordedClip(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, clipId: String, atSourceMs: Long): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SplitClip(clipId, atSourceMs))
    }
}

class PreferRecordedClip(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, clipId: String, preferred: Boolean): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.PreferClip(clipId, preferred))
    }
}

class UpdateMusicalSettings(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, settings: MusicalSettings): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val edit = applyAndSave(reducer, sessions, document, SessionCommand.SetMusical(settings))
        val userWroteLyrics = document.lyrics.lines.any { !it.id.startsWith("warmup-") }
        if (edit is SessionEdit.Applied && edit.document.kind == SessionKind.PRACTICE && !userWroteLyrics) {
            val key = settings.keyName ?: "C"
            val warmupLines = warmupLinesForKey(key, settings.mode)
            val sheet = LyricSheet(
                rawText = warmupLines.joinToString("\n") { it.text },
                lines = warmupLines,
                alignment = LyricAlignment.LINE,
            )
            return applyAndSave(reducer, sessions, edit.document, SessionCommand.SetLyrics(sheet))
        }
        return edit
    }
}

class UpdatePracticeStats(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, stats: PracticeStats): SessionEdit {
        val document = sessions.load(sessionId) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetPractice(stats))
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

class RestoreSession(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(document: SessionDocument) {
        sessions.save(document)
    }
}

data class CleanTakesResult(
    val removedFiles: Int,
    val removedClips: Int,
)

/** Drop parked clips whose WAV is unused by any live clip, then delete those files. */
class CleanUnusedTakes(
    private val sessions: SessionRepository,
    private val catalog: com.swaralab.domain.port.TakeCatalog? = null,
) {
    suspend operator fun invoke(sessionId: String): CleanTakesResult {
        val document = sessions.load(sessionId) ?: return CleanTakesResult(0, 0)
        val liveSources = document.tracks
            .flatMap { it.clips }
            .filter { it.active }
            .map { it.sourceRelativePath }
            .toSet()
        var removedClips = 0
        val compacted = document.copy(
            tracks = document.tracks.map { track ->
                track.copy(
                    clips = track.clips.filter { clip ->
                        if (clip.active || clip.sourceRelativePath in liveSources) {
                            true
                        } else {
                            removedClips += 1
                            false
                        }
                    },
                )
            },
        )
        sessions.save(compacted)
        val removedFiles = catalog?.pruneTakes(sessionId, liveSources) ?: 0
        return CleanTakesResult(removedFiles = removedFiles, removedClips = removedClips)
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
        rightsConfirmed: Boolean = true,
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
                rightsConfirmed = rightsConfirmed,
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

internal fun warmupLinesForKey(keyName: String, mode: ScaleMode? = null): List<LyricLine> {
    val cues = listOf(
        "Steady lower tonic",
        "Step up smoothly",
        "Hold this swara",
        "Keep the phrase even",
        "Strong fifth resonance",
        "Clear next step",
        "Leading tone",
        "Upper octave tonic",
    )
    return MusicalKeys.scaleForKey(keyName, mode).mapIndexed { index, note ->
        LyricLine(
            id = "warmup-${index + 1}",
            text = "${note.swara} (${note.westernNote} · ${note.hz.toInt()} Hz) — ${cues.getOrElse(index) { "Hold this swara" }}",
            startMs = index * 2_000L,
        )
    }
}

fun encodeLyricLines(lines: List<LyricLine>): String {
    if (lines.isEmpty()) return ""
    val timed = lines.any { it.startMs != null }
    return lines.joinToString("\n") { line ->
        val text = line.text.trim()
        if (text.isEmpty()) return@joinToString ""
        if (!timed) {
            text
        } else {
            val start = line.startMs?.coerceAtLeast(0L) ?: 0L
            val minutes = start / 60_000L
            val seconds = (start % 60_000L) / 1_000L
            val hundredths = (start % 1_000L) / 10L
            "[%02d:%02d.%02d] %s".format(minutes, seconds, hundredths, text)
        }
    }.lines().filter { it.isNotBlank() }.joinToString("\n")
}

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
