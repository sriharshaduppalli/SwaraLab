package com.swaralab.domain

import com.swaralab.domain.audio.PunchWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunchWindowTest {
    @Test
    fun prerollBuffersAreNotWritten() {
        val slice = PunchWindow.slice(
            timelineFrame = PunchWindow.frames(0, 48_000),
            frames = 480,
            punchStartFrame = PunchWindow.frames(2_000, 48_000),
            punchEndFrame = PunchWindow.frames(4_000, 48_000),
        )
        assertEquals(0, slice.writeFrames)
        assertTrue(slice.preroll)
        assertFalse(slice.finished)
    }

    @Test
    fun writeStartsOnThePunchFrameInsideABuffer() {
        val start = PunchWindow.frames(2_000, 48_000)
        val slice = PunchWindow.slice(
            timelineFrame = start - 100,
            frames = 480,
            punchStartFrame = start,
            punchEndFrame = PunchWindow.frames(4_000, 48_000),
        )
        assertEquals(100, slice.writeFrom)
        assertEquals(480, slice.writeTo)
        assertTrue(slice.preroll)
        assertFalse(slice.finished)
    }

    @Test
    fun writeStopsOnThePunchEndFrame() {
        val end = PunchWindow.frames(4_000, 48_000)
        val slice = PunchWindow.slice(
            timelineFrame = end - 40,
            frames = 480,
            punchStartFrame = PunchWindow.frames(2_000, 48_000),
            punchEndFrame = end,
        )
        assertEquals(0, slice.writeFrom)
        assertEquals(40, slice.writeTo)
        assertTrue(slice.finished)
    }

    @Test
    fun openEndedPunchWritesTheWholeBuffer() {
        val slice = PunchWindow.slice(
            timelineFrame = 0,
            frames = 512,
            punchStartFrame = 0,
            punchEndFrame = 0,
        )
        assertEquals(512, slice.writeFrames)
        assertFalse(slice.preroll)
        assertFalse(slice.finished)
    }
}
