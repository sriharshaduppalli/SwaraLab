package com.swaralab.domain

import com.swaralab.domain.model.LyricAlignment
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.SaveTake
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.SplitRecordedClip
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.encodeLyricLines
import com.swaralab.domain.usecase.parseLyricClock
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateVocalChain
import com.swaralab.domain.usecase.UpdateVocalSyncOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAndTakeUseCaseTest {
    @Test
    fun lrcFileParsesTimestampsAndSkipsMetadataTags() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val lrcContent = """
            [ti:My Song]
            [ar:SwaraLab]
            [00:05.50] First verse line
            [01:02.00] Chorus line here
        """.trimIndent()

        UpdateLyrics(reducer, store, SequenceIds())(created.id, lrcContent)
        val loaded = LoadSession(store)(created.id)!!
        assertEquals(LyricAlignment.LINE, loaded.lyrics.alignment)
        assertEquals(listOf("First verse line", "Chorus line here"), loaded.lyrics.lines.map { it.text })
        assertEquals(listOf(5_500L, 62_000L), loaded.lyrics.lines.map { it.startMs })
        val encoded = encodeLyricLines(loaded.lyrics.lines)
        assertTrue(encoded.contains("[00:05.50]"))
        assertTrue(encoded.contains("First verse line"))
        assertEquals(5_500L, parseLyricClock("00:05.50"))
        assertEquals(62_000L, parseLyricClock("1:02.00"))
        assertEquals(5_500L, parseLyricClock("[00:05.50]"))
    }

    @Test
    fun commitRecordedTakeAppendsTakeToSelectedVoiceTrack() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadTrackId = created.tracks.first { it.role == TrackRole.LEAD }.id

        CommitRecordedTake(reducer, store)(
            sessionId = created.id,
            trackId = leadTrackId,
            take = FinishedTake(
                relativePath = "media/takes/take-1.wav",
                durationMs = 14_200L,
                peak = 0.72f,
            ),
        )

        val loaded = LoadSession(store)(created.id)!!
        val leadClips = loaded.tracks.first { it.id == leadTrackId }.clips
        assertEquals(1, leadClips.size)
        assertEquals("Lead · Take 1", leadClips.single().label)
        assertEquals("media/takes/take-1.wav", leadClips.single().sourceRelativePath)
        assertTrue(leadClips.single().preferred)
    }

    @Test
    fun punchCommitKeepsTheFirstHalfAndBothPreferredClips() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadTrackId = created.tracks.first { it.role == TrackRole.LEAD }.id
        val commit = CommitRecordedTake(reducer, store)
        commit(created.id, leadTrackId, FinishedTake("media/takes/take-1.wav", 20_000L, 0.5f))
        commit(
            sessionId = created.id,
            trackId = leadTrackId,
            take = FinishedTake("media/takes/take-2.wav", 8_000L, 0.6f, videoRelativePath = "media/video/take-2.mp4"),
            timelineStartMs = 8_000L,
        )
        val clips = LoadSession(store)(created.id)!!.tracks.first { it.id == leadTrackId }.clips
        assertEquals(3, clips.size)
        assertTrue(clips.first().active.not())
        assertEquals(20_000L, clips.first().durationMs)
        assertEquals(8_000L, clips[1].durationMs)
        assertEquals(8_000L, clips.last().timelineStartMs)
        assertTrue(clips[1].preferred && clips[1].active)
        assertTrue(clips.last().preferred && clips.last().active)
        assertEquals("media/video/take-2.mp4", clips.last().videoRelativePath)
    }

    @Test
    fun trimAndPreferKeepTheChosenPhraseInTheMix() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadTrackId = created.tracks.first { it.role == TrackRole.LEAD }.id
        val commit = CommitRecordedTake(reducer, store)
        commit(created.id, leadTrackId, FinishedTake("media/takes/take-1.wav", 8_000L, 0.5f))
        commit(created.id, leadTrackId, FinishedTake("media/takes/take-2.wav", 8_000L, 0.5f))
        val afterSecond = LoadSession(store)(created.id)!!
        val second = afterSecond.tracks.first { it.id == leadTrackId }.clips.last()
        assertTrue(second.preferred)

        val first = afterSecond.tracks.first { it.id == leadTrackId }.clips.first()
        TrimRecordedClip(reducer, store)(created.id, first.id, sourceInMs = 1_000L, durationMs = 2_500L)
        PreferRecordedClip(reducer, store)(created.id, first.id, preferred = true)
        val loaded = LoadSession(store)(created.id)!!
        val clips = loaded.tracks.first { it.id == leadTrackId }.clips
        assertEquals(1_000L, clips.first().sourceInMs)
        assertEquals(2_500L, clips.first().durationMs)
        assertTrue(clips.first().preferred)
        assertTrue(clips.last().preferred.not())
    }

    @Test
    fun punchWindowKeepsHeadAndTailAroundTheNewTake() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        val commit = CommitRecordedTake(reducer, store)
        commit(created.id, leadId, FinishedTake("media/takes/take-1.wav", 8_000L, 0.5f))
        commit(
            created.id,
            leadId,
            FinishedTake("media/takes/punch.wav", 2_000L, 0.5f),
            timelineStartMs = 2_000L,
            punchEndMs = 4_000L,
        )
        val clips = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips
        assertEquals(4, clips.size)
        assertTrue(clips[0].active.not())
        assertTrue(clips[0].preferred.not())
        assertEquals(8_000L, clips[0].durationMs)
        assertEquals(2_000L, clips[1].durationMs)
        assertEquals(0L, clips[1].timelineStartMs)
        assertEquals(2_000L, clips[2].timelineStartMs)
        assertEquals("media/takes/punch.wav", clips[2].sourceRelativePath)
        assertEquals(4_000L, clips[3].timelineStartMs)
        assertEquals("media/takes/take-1.wav", clips[3].sourceRelativePath)
        assertEquals(4_000L, clips[3].sourceInMs)
        assertEquals(4_000L, clips[3].durationMs)
        assertTrue(clips.drop(1).all { it.preferred && it.active })
    }

    @Test
    fun splitRecordedClipCutsATakeInPlace() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        CommitRecordedTake(reducer, store)(created.id, leadId, FinishedTake("media/takes/take-1.wav", 6_000L, 0.5f))
        val clipId = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips.single().id
        SplitRecordedClip(reducer, store)(created.id, clipId, 2_500L)
        val clips = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips
        assertEquals(2, clips.size)
        assertEquals(2_500L, clips[0].durationMs)
        assertEquals(3_500L, clips[1].durationMs)
    }

    @Test
    fun updateTrackMixVocalChainAndSyncOffsetPersist() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadTrackId = created.tracks.first { it.role == TrackRole.LEAD }.id

        UpdateTrackMix(reducer, store)(created.id, leadTrackId, gainDb = 3.5f, mute = true)
        UpdateVocalChain(reducer, store)(
            id = created.id,
            reverb = 0.6f,
            delay = 0.25f,
            compression = 0.5f,
            denoise = 0.4f,
            backingDuckDb = 4f,
        )
        UpdateVocalSyncOffset(reducer, store)(created.id, compensationMs = -45.0)

        val loaded = LoadSession(store)(created.id)!!
        val leadTrack = loaded.tracks.first { it.id == leadTrackId }
        assertEquals(3.5f, leadTrack.gainDb, 0.01f)
        assertTrue(leadTrack.mute)
        assertEquals(VocalPreset.CUSTOM, loaded.chain.preset)
        assertEquals(0.6f, loaded.chain.reverb, 0.01f)
        assertEquals(0.25f, loaded.chain.delay, 0.01f)
        assertEquals(-45.0, loaded.latency.compensationMs, 0.01)
    }

    @Test
    fun practiceAndVideoModesInitializeDistinctly() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val create = CreateSession(reducer, store)

        val practice = create(SessionKind.PRACTICE, "Morning Riyaz")
        assertEquals(8, practice.lyrics.lines.size)
        assertTrue(practice.lyrics.lines.first().text.startsWith("Sa (C4"))

        ImportBackingTrack(reducer, store)(
            sessionId = practice.id,
            relativePath = "media/backing/drone.wav",
            durationMs = 16_000L,
            label = "Tanpura Drone",
        )
        val practiceWithDrone = LoadSession(store)(practice.id)!!
        assertEquals(1, practiceWithDrone.tracks.first { it.role == TrackRole.BACKING }.clips.size)

        val video = create(SessionKind.VIDEO, "Reel Cover")
        assertEquals(PublishTarget.REELS, video.publish.target)
    }

    @Test
    fun saveTakeCommitsTheDryVocal() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "My Song")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        val edit = SaveTake(CommitRecordedTake(reducer, store))(
            sessionId = created.id,
            trackId = leadId,
            take = FinishedTake("media/takes/take-9.wav", 5_000L, 0.4f, score = 72f),
        )
        assertTrue(edit is com.swaralab.domain.command.SessionEdit.Applied)
        val clips = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips
        assertEquals("media/takes/take-9.wav", clips.single().sourceRelativePath)
    }
}
