package com.swaralab.domain

import com.swaralab.domain.model.LyricAlignment
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.LoadSession
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
}

private class InMemorySessions : SessionRepository {
    private val map = linkedMapOf<String, com.swaralab.domain.model.SessionDocument>()

    override suspend fun save(document: com.swaralab.domain.model.SessionDocument) {
        map[document.id] = document
    }

    override suspend fun load(id: String) = map[id]

    override suspend fun list(): List<SessionSummary> = map.values.map {
        SessionSummary(it.id, it.title, it.kind, it.updatedAtEpochMs)
    }

    override suspend fun delete(id: String) {
        map.remove(id)
    }
}
