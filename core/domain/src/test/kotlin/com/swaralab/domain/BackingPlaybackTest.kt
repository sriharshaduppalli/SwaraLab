package com.swaralab.domain

import com.swaralab.domain.audio.BackingPlayback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackingPlaybackTest {
    @Test
    fun loopsDroneAndShortBedsOnly() {
        assertTrue(BackingPlayback.shouldLoop("media/backing/Swara_Drone_C_Sa_Pa.wav", 16_000L))
        assertTrue(BackingPlayback.shouldLoop("media/backing/Guide_Melody.wav", 40_000L))
        assertFalse(BackingPlayback.shouldLoop("media/backing/karaoke-song.mp3", 180_000L))
    }
}
