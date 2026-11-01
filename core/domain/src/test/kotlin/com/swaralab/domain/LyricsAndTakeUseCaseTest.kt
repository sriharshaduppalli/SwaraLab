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
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateLyrics
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
}

private class InMemorySessions : SessionRepository {
    private val map = linkedMapOf<String, com.swaralab.domain.model.SessionDocument>()

    override suspend fun save(document: com.swaralab.domain.model.SessionDocument) {
        map[document.id] = document
    }

    override suspend fun load(id: String) = map[id]

    override suspend fun list(): List<SessionSummary> = map.values.map {
        SessionSummary(
            it.id,
            it.title,
            it.kind,
            it.updatedAtEpochMs,
            takeCount = it.tracks.filter { track -> track.role != TrackRole.BACKING }.sumOf { track -> track.clips.size },
        )
    }

    override suspend fun delete(id: String) {
        map.remove(id)
    }
}
