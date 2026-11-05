package com.swaralab.domain

import com.swaralab.domain.audio.LiveMonitorChain
import com.swaralab.domain.audio.LiveMonitorEffect
import com.swaralab.domain.audio.RecordingDraft
import com.swaralab.domain.audio.StageTake
import com.swaralab.domain.audio.StageTakeState
import com.swaralab.domain.audio.TimedLyrics
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.port.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTakeTest {
    private val draft = RecordingDraft(track = null, lyrics = emptyList())

    @Test
    fun timedLyricsFollowStartTimes() {
        val lines = listOf(
            LyricLine(id = "a", text = "One", startMs = 0L),
            LyricLine(id = "b", text = "Two", startMs = 4_000L),
            LyricLine(id = "c", text = "Three", startMs = 8_000L),
        )
        assertEquals(0, TimedLyrics.indexAt(lines, 200L))
        assertEquals(1, TimedLyrics.indexAt(lines, 4_000L))
        assertEquals(2, TimedLyrics.indexAt(lines, 12_000L))
    }

    @Test
    fun countdownHappensBeforeRecordingAndSeekIsLocked() {
        val counting = StageTake.of(
            status = EngineStatus.COUNTING,
            countInBeat = 3,
            positionMs = 2_000L,
            durationMs = 10_000L,
            peak = 0f,
            lyricIndex = 0,
            draft = draft,
        )
        assertTrue(counting is StageTakeState.Countdown)
        assertEquals(3, (counting as StageTakeState.Countdown).beatsLeft)
        assertFalse(StageTake.canSeek(counting))

        val ready = StageTake.of(
            status = EngineStatus.ARMED,
            countInBeat = 0,
            positionMs = 2_000L,
            durationMs = 10_000L,
            peak = 0f,
            lyricIndex = 0,
            draft = draft,
        )
        assertTrue(ready is StageTakeState.Ready)
        assertTrue(StageTake.canSeek(ready))
    }

    @Test
    fun reviewKeepsDryVocalOnly() {
        val review = StageTake.of(
            status = EngineStatus.ARMED,
            countInBeat = 0,
            positionMs = 0L,
            durationMs = 0L,
            peak = 0f,
            lyricIndex = 0,
            draft = draft,
            dryVocalPath = "media/takes/take-1.wav",
            reviewDurationMs = 12_400L,
        )
        assertEquals(
            StageTakeState.Review(dryVocalPath = "media/takes/take-1.wav", durationMs = 12_400L),
            review,
        )
    }

    @Test
    fun liveEffectUpdatesOneSlot() {
        val next = LiveMonitorChain().apply(LiveMonitorEffect.Reverb(0.4f, 0.2f))
        assertEquals(0.4f, next.reverb, 0.001f)
        assertEquals(0.2f, next.delay, 0.001f)
        val pitched = LiveMonitorChain().apply(LiveMonitorEffect.Pitch(2f))
        assertEquals(1.4f, pitched.eqHigh, 0.001f)
    }
}
