package com.swaralab.domain.command

import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ClipState
import com.swaralab.domain.model.LatencyCalibration
import com.swaralab.domain.model.RightsAttestation
import com.swaralab.domain.model.SectionMarker
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.TrackState
import com.swaralab.domain.model.defaultTitle
import com.swaralab.domain.time.Clock

class SessionReducer(
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun create(kind: SessionKind, title: String): SessionDocument {
        val now = clock.nowEpochMs()
        val id = ids.newId()
        val tracks = buildList {
            if (kind != SessionKind.PRACTICE) {
                add(track(TrackRole.BACKING, "Backing"))
            }
            add(track(TrackRole.LEAD, "Lead"))
        }
        return SessionDocument(
            id = id,
            title = title.trim().ifBlank { defaultTitle(kind) },
            kind = kind,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            tracks = tracks,
        )
    }

    fun apply(document: SessionDocument, command: SessionCommand): SessionEdit {
        val edited = when (command) {
            is SessionCommand.Rename -> SessionEdit.Applied(
                document.copy(title = command.title.trim().ifBlank { document.title }),
            )
            is SessionCommand.SetGear -> SessionEdit.Applied(document.copy(gear = command.gear))
            is SessionCommand.SetLatency -> setLatency(document, command)
            is SessionCommand.ImportBacking -> importBacking(document, command)
            is SessionCommand.CommitTake -> commitTake(document, command)
            is SessionCommand.DeleteClip -> deleteClip(document, command)
            is SessionCommand.TrimClip -> trimClip(document, command)
            is SessionCommand.PreferClip -> preferClip(document, command)
            is SessionCommand.SetPractice -> SessionEdit.Applied(document.copy(practice = command.stats))
            is SessionCommand.SetTrackMix -> setTrackMix(document, command)
            is SessionCommand.AddTrack -> addTrack(document, command)
            is SessionCommand.SetVocalChain -> SessionEdit.Applied(document.copy(chain = command.chain))
            is SessionCommand.SetLyrics -> SessionEdit.Applied(document.copy(lyrics = command.sheet))
            is SessionCommand.AddMarker -> addMarker(document, command)
            is SessionCommand.SetMusical -> setMusical(document, command)
            is SessionCommand.SetPublish -> SessionEdit.Applied(document.copy(publish = command.settings))
            is SessionCommand.SetSongwriter -> setSongwriter(document, command)
        }
        return when (edited) {
            is SessionEdit.Rejected -> edited
            is SessionEdit.Applied -> edited.copy(
                document = edited.document.copy(updatedAtEpochMs = clock.nowEpochMs()),
            )
        }
    }

    private fun setLatency(document: SessionDocument, command: SessionCommand.SetLatency): SessionEdit {
        if (command.measuredRoundTripMs < 0) {
            return SessionEdit.Rejected(RejectReason.INVALID_LATENCY)
        }
        return SessionEdit.Applied(
            document.copy(
                latency = LatencyCalibration(
                    measuredRoundTripMs = command.measuredRoundTripMs,
                    compensationMs = command.compensationMs,
                    calibratedAtEpochMs = clock.nowEpochMs(),
                ),
            ),
        )
    }

    private fun importBacking(document: SessionDocument, command: SessionCommand.ImportBacking): SessionEdit {
        if (!command.rightsConfirmed) return SessionEdit.Rejected(RejectReason.RIGHTS_REQUIRED)
        if (!SessionLayout.isSafeRelative(command.relativePath)) {
            return SessionEdit.Rejected(RejectReason.UNSAFE_PATH)
        }
        if (command.durationMs <= 0) return SessionEdit.Rejected(RejectReason.EMPTY_CLIP)
        val index = document.tracks.indexOfFirst { it.id == command.trackId && it.role == TrackRole.BACKING }
        if (index < 0) return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        val track = document.tracks[index]
        val clip = ClipState(
            id = ids.newId(),
            sourceRelativePath = command.relativePath,
            timelineStartMs = 0,
            sourceInMs = 0,
            durationMs = command.durationMs,
            label = "Backing",
        )
        return SessionEdit.Applied(
            document.copy(
                tracks = document.tracks.replaceAt(index, track.copy(clips = track.clips + clip)),
                rights = RightsAttestation(
                    backingImported = true,
                    singerConfirmedRights = true,
                    note = command.note,
                    attestedAtEpochMs = clock.nowEpochMs(),
                ),
            ),
        )
    }

    private fun commitTake(document: SessionDocument, command: SessionCommand.CommitTake): SessionEdit {
        if (!SessionLayout.isSafeRelative(command.relativePath)) {
            return SessionEdit.Rejected(RejectReason.UNSAFE_PATH)
        }
        if (command.durationMs <= 0 || command.timelineStartMs < 0) {
            return SessionEdit.Rejected(RejectReason.EMPTY_CLIP)
        }
        val index = document.tracks.indexOfFirst { it.id == command.trackId }
        if (index < 0) return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        val track = document.tracks[index]
        val clip = ClipState(
            id = ids.newId(),
            sourceRelativePath = command.relativePath,
            timelineStartMs = command.timelineStartMs,
            sourceInMs = 0,
            durationMs = command.durationMs,
            label = command.label,
            preferred = true,
            sourceDurationMs = command.durationMs,
            videoRelativePath = command.videoRelativePath,
        )
        val existing = track.clips.map { clip ->
            if (command.keepPreviousPreferred && clip.preferred) clip else clip.copy(preferred = false)
        }
        return SessionEdit.Applied(
            document.copy(
                tracks = document.tracks.replaceAt(index, track.copy(clips = existing + clip)),
            ),
        )
    }

    private fun deleteClip(document: SessionDocument, command: SessionCommand.DeleteClip): SessionEdit {
        val updatedTracks = document.tracks.map { track ->
            track.copy(clips = track.clips.filterNot { it.id == command.clipId })
        }
        return SessionEdit.Applied(document.copy(tracks = updatedTracks))
    }

    private fun trimClip(document: SessionDocument, command: SessionCommand.TrimClip): SessionEdit {
        if (command.sourceInMs < 0 || command.durationMs <= 0) {
            return SessionEdit.Rejected(RejectReason.INVALID_RANGE)
        }
        var found = false
        val updatedTracks = document.tracks.map { track ->
            track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != command.clipId) {
                        clip
                    } else {
                        found = true
                        val maxDuration = clip.usableSourceDurationMs
                        val start = command.sourceInMs.coerceAtMost((maxDuration - 1L).coerceAtLeast(0L))
                        val remaining = (maxDuration - start).coerceAtLeast(1L)
                        clip.copy(
                            sourceInMs = start,
                            durationMs = command.durationMs.coerceIn(1L, remaining),
                        )
                    }
                },
            )
        }
        if (!found) return SessionEdit.Rejected(RejectReason.EMPTY_CLIP)
        return SessionEdit.Applied(document.copy(tracks = updatedTracks))
    }

    private fun preferClip(document: SessionDocument, command: SessionCommand.PreferClip): SessionEdit {
        var found = false
        val updatedTracks = document.tracks.map { track ->
            val hasTarget = track.clips.any { it.id == command.clipId }
            if (!hasTarget) return@map track
            found = true
            track.copy(
                clips = track.clips.map { clip ->
                    clip.copy(preferred = if (clip.id == command.clipId) command.preferred else false)
                },
            )
        }
        if (!found) return SessionEdit.Rejected(RejectReason.EMPTY_CLIP)
        return SessionEdit.Applied(document.copy(tracks = updatedTracks))
    }

    private fun setTrackMix(document: SessionDocument, command: SessionCommand.SetTrackMix): SessionEdit {
        if (!command.gainDb.isFinite() || !command.monitorGainDb.isFinite()) {
            return SessionEdit.Rejected(RejectReason.INVALID_RANGE)
        }
        val index = document.tracks.indexOfFirst { it.id == command.trackId }
        if (index < 0) return SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK)
        val track = document.tracks[index]
        return SessionEdit.Applied(
            document.copy(
                tracks = document.tracks.replaceAt(
                    index,
                    track.copy(
                        gainDb = command.gainDb,
                        monitorGainDb = command.monitorGainDb,
                        mute = command.mute,
                        solo = command.solo,
                    ),
                ),
            ),
        )
    }

    private fun addTrack(document: SessionDocument, command: SessionCommand.AddTrack): SessionEdit {
        if (command.name.isBlank()) return SessionEdit.Rejected(RejectReason.BLANK_NAME)
        val added = track(command.role, command.name.trim())
        return SessionEdit.Applied(document.copy(tracks = document.tracks + added))
    }

    private fun addMarker(document: SessionDocument, command: SessionCommand.AddMarker): SessionEdit {
        if (command.name.isBlank()) return SessionEdit.Rejected(RejectReason.BLANK_MARKER)
        if (command.startMs < 0 || (command.endMs != null && command.endMs < command.startMs)) {
            return SessionEdit.Rejected(RejectReason.INVALID_RANGE)
        }
        val marker = SectionMarker(
            id = ids.newId(),
            name = command.name.trim(),
            startMs = command.startMs,
            endMs = command.endMs,
        )
        return SessionEdit.Applied(document.copy(markers = document.markers + marker))
    }

    private fun setMusical(document: SessionDocument, command: SessionCommand.SetMusical): SessionEdit {
        val tempo = command.settings.tempoBpm
        if (tempo != null && (tempo < 40.0 || tempo > 200.0)) {
            return SessionEdit.Rejected(RejectReason.INVALID_RANGE)
        }
        val rate = command.settings.playbackRate
        if (!rate.isFinite() || rate < 0.7f || rate > 1.35f) {
            return SessionEdit.Rejected(RejectReason.INVALID_RANGE)
        }
        val shift = command.settings.keyShiftSemitones
        if (shift !in -6..6) return SessionEdit.Rejected(RejectReason.INVALID_RANGE)
        return SessionEdit.Applied(
            document.copy(
                musical = command.settings.copy(
                    tempoBpm = tempo,
                    keyName = command.settings.keyName?.ifBlank { "C" } ?: "C",
                    keyShiftSemitones = shift,
                    playbackRate = rate,
                ),
            ),
        )
    }

    private fun setSongwriter(document: SessionDocument, command: SessionCommand.SetSongwriter): SessionEdit {
        val memo = command.notes.memoRelativePath
        val midi = command.notes.midiRelativePath
        if ((memo != null && !SessionLayout.isSafeRelative(memo)) ||
            (midi != null && !SessionLayout.isSafeRelative(midi))
        ) {
            return SessionEdit.Rejected(RejectReason.UNSAFE_PATH)
        }
        return SessionEdit.Applied(document.copy(songwriter = command.notes))
    }

    private fun track(role: TrackRole, name: String) = TrackState(id = ids.newId(), role = role, name = name)
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> {
    val copy = toMutableList()
    copy[index] = value
    return copy
}
