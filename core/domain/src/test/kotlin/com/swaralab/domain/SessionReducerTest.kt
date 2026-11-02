package com.swaralab.domain

import com.swaralab.domain.command.RejectReason
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.SongwriterNotes
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.PublishSettings
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.model.recordingSnapshot
import com.swaralab.domain.model.withTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {
    @Test
    fun songStartsWithBackingAndLead() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "  ")
        assertEquals("Song", document.title)
        assertEquals(listOf(TrackRole.BACKING, TrackRole.LEAD), document.tracks.map { it.role })
        assertEquals("id-1", document.id)
    }

    @Test
    fun practiceStartsWithLeadOnly() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.PRACTICE, "Warm up")
        assertEquals(listOf(TrackRole.LEAD), document.tracks.map { it.role })
        assertEquals("Warm up", document.title)
    }

    @Test
    fun videoSessionIsMarkedVideo() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.VIDEO, "Take")
        assertEquals(SessionKind.VIDEO, document.kind)
        assertEquals(TrackRole.BACKING, document.tracks.first().role)
    }

    @Test
    fun backingImportRequiresRightsAndASafeFile() {
        val (clock, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "Cover")
        val backingId = document.tracks.first { it.role == TrackRole.BACKING }.id

        val refused = reducer.apply(
            document,
            SessionCommand.ImportBacking(backingId, "media/backing/song.wav", 1_000, rightsConfirmed = false),
        )
        assertEquals(SessionEdit.Rejected(RejectReason.RIGHTS_REQUIRED), refused)

        val unsafe = reducer.apply(
            document,
            SessionCommand.ImportBacking(backingId, "../song.wav", 1_000, rightsConfirmed = true),
        )
        assertEquals(SessionEdit.Rejected(RejectReason.UNSAFE_PATH), unsafe)

        clock.now = 2_000
        val applied = reducer.apply(
            document,
            SessionCommand.ImportBacking(
                backingId,
                SessionLayout.backingFile("song.wav"),
                4_000,
                rightsConfirmed = true,
            ),
        ) as SessionEdit.Applied
        assertTrue(applied.document.rights?.singerConfirmedRights == true)
        assertEquals(1, applied.document.tracks.first().clips.size)
        assertEquals(2_000L, applied.document.updatedAtEpochMs)
    }

    @Test
    fun practiceHasNoBackingUntilOneIsAdded() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.PRACTICE, "Practice")
        val refused = reducer.apply(
            document,
            SessionCommand.ImportBacking("missing", "media/backing/a.wav", 1_000, true),
        )
        assertEquals(SessionEdit.Rejected(RejectReason.UNKNOWN_TRACK), refused)
    }

    @Test
    fun commitTakeLandsOnTheLeadLane() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.PRACTICE, "Line")
        val leadId = document.tracks.single().id
        val applied = reducer.apply(
            document,
            SessionCommand.CommitTake(leadId, SessionLayout.takeWav("take-1"), 3_200),
        ) as SessionEdit.Applied
        val clip = applied.document.tracks.single().clips.single()
        assertEquals("media/takes/take-1.wav", clip.sourceRelativePath)
        assertEquals(3_200L, clip.durationMs)
    }

    @Test
    fun speakerSnapshotUsesThePhoneMic() {
        val snapshot = GearRoute.SPEAKER.recordingSnapshot()
        assertEquals(GearRoute.BUILTIN_MIC, snapshot.input)
        assertEquals(GearRoute.SPEAKER, snapshot.output)
    }

    @Test
    fun latencyRejectsANegativeMeasurement() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "Song")
        val refused = reducer.apply(document, SessionCommand.SetLatency(-1.0, 0.0))
        assertEquals(SessionEdit.Rejected(RejectReason.INVALID_LATENCY), refused)
    }

    @Test
    fun radioPresetIsStoredOnTheChain() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "Song")
        val applied = reducer.apply(
            document,
            SessionCommand.SetVocalChain(VocalChain.preset(VocalPreset.RADIO)),
        ) as SessionEdit.Applied
        assertEquals(VocalPreset.RADIO, applied.document.chain.preset)
        assertTrue(applied.document.chain.rider > 0f)
    }

    @Test
    fun markerAndTempoValidation() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "Song")
        assertEquals(
            SessionEdit.Rejected(RejectReason.BLANK_MARKER),
            reducer.apply(document, SessionCommand.AddMarker("  ", 0)),
        )
        assertEquals(
            SessionEdit.Rejected(RejectReason.INVALID_RANGE),
            reducer.apply(document, SessionCommand.SetMusical(MusicalSettings(tempoBpm = 0.0))),
        )
        val marked = reducer.apply(document, SessionCommand.AddMarker("Chorus", 12_000, 28_000)) as SessionEdit.Applied
        assertEquals("Chorus", marked.document.markers.single().name)
    }

    @Test
    fun songwriterPathsStayInsideTheFolder() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "Song")
        val refused = reducer.apply(
            document,
            SessionCommand.SetSongwriter(SongwriterNotes(midiRelativePath = "C:/outside.mid")),
        )
        assertEquals(SessionEdit.Rejected(RejectReason.UNSAFE_PATH), refused)
        assertNull(document.songwriter.midiRelativePath)
    }

    @Test
    fun monitorMixIsSeparateFromTheTrackGain() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.SONG, "Song")
        val leadId = document.tracks.first { it.role == TrackRole.LEAD }.id
        val applied = reducer.apply(
            document,
            SessionCommand.SetTrackMix(leadId, gainDb = -1f, monitorGainDb = 3f, mute = false, solo = true),
        ) as SessionEdit.Applied
        val lead = applied.document.tracks.first { it.id == leadId }
        assertEquals(-1f, lead.gainDb)
        assertEquals(3f, lead.monitorGainDb)
        assertTrue(lead.solo)
    }

    @Test
    fun shareTargetsSetSingerLoudness() {
        assertEquals(-11f, PublishSettings().withTarget(PublishTarget.REELS).loudnessLufs)
        assertEquals(-11f, PublishSettings().withTarget(PublishTarget.SHORTS).loudnessLufs)
        assertEquals(-16f, PublishSettings().withTarget(PublishTarget.WHATSAPP).loudnessLufs)
        assertEquals(-14f, PublishSettings().withTarget(PublishTarget.MASTER).loudnessLufs)
        assertEquals(-13f, PublishSettings().withTarget(PublishTarget.SQUARE).loudnessLufs)
    }

    @Test
    fun singerPresetsStayNamedOnTheChain() {
        val voice = VocalChain.preset(VocalPreset.CLOSE)
        val hall = VocalChain.preset(VocalPreset.STAGE)
        val phone = VocalChain.preset(VocalPreset.RADIO)
        assertTrue(voice.eqPresenceDb > hall.eqPresenceDb)
        assertTrue(hall.reverb > voice.reverb)
        assertTrue(phone.denoise > voice.denoise)
        assertTrue(phone.backingDuckDb > voice.backingDuckDb)
    }
}
