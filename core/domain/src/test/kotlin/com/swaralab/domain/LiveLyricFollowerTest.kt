package com.swaralab.domain

import com.swaralab.domain.audio.LiveLyricFollower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLyricFollowerTest {
    @Test
    fun silenceAfterASungPhraseAdvancesOneLine() {
        var state = LiveLyricFollower.State()
        val lines = listOf("Sa Re Ga Ma", "Pa Dha Ni Sa")
        repeat(4) {
            state = LiveLyricFollower.step(state, lines.size, lines[state.lineIndex], voiced = true, dtMs = 100)
        }
        assertEquals(0, state.lineIndex)
        assertTrue(state.heardVoiceOnLine)

        repeat(4) {
            state = LiveLyricFollower.step(state, lines.size, lines[state.lineIndex], voiced = false, dtMs = 100)
        }
        assertEquals(1, state.lineIndex)
    }

    @Test
    fun longPauseDoesNotSkipExtraLines() {
        var state = LiveLyricFollower.State()
        val lines = listOf("One", "Two", "Three")
        repeat(4) {
            state = LiveLyricFollower.step(state, lines.size, lines[state.lineIndex], voiced = true, dtMs = 100)
        }
        repeat(20) {
            state = LiveLyricFollower.step(state, lines.size, lines[state.lineIndex], voiced = false, dtMs = 100)
        }
        assertEquals(1, state.lineIndex)
    }

    @Test
    fun continuousSingingAdvancesByWordCountEstimate() {
        var state = LiveLyricFollower.State()
        val line = "Hold this note and let it ring out now"
        val estimate = LiveLyricFollower.estimatedLineMs(line)
        assertTrue(estimate >= 1_600L)
        var elapsed = 0L
        while (elapsed < estimate && state.lineIndex == 0) {
            state = LiveLyricFollower.step(state, 2, line, voiced = true, dtMs = 100)
            elapsed += 100
        }
        assertEquals(1, state.lineIndex)
    }

    @Test
    fun staysOnLastLine() {
        var state = LiveLyricFollower.State(lineIndex = 1, heardVoiceOnLine = true, voicedMs = 800)
        repeat(8) {
            state = LiveLyricFollower.step(state, 2, "Final line", voiced = false, dtMs = 100)
        }
        assertEquals(1, state.lineIndex)
    }
}
