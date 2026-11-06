package com.swaralab.domain

import com.swaralab.domain.audio.FfmpegAudioFilters
import com.swaralab.domain.audio.LoopPlayback
import com.swaralab.domain.audio.LoopRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegAudioFiltersTest {
    @Test
    fun tempoOnlyUsesAtempo() {
        val filter = FfmpegAudioFilters.audioFilter(0, 0.75f)
        assertEquals("atempo=0.7500", filter)
    }

    @Test
    fun plusTwoSemitonesRestoresTempo() {
        val filter = FfmpegAudioFilters.audioFilter(2, 1f)
        assertTrue(filter, filter.startsWith("asetrate=48000*1.122462,aresample=48000,atempo=0.8909"))
    }

    @Test
    fun plusThreeAndSlowCombinesPitchAndTempo() {
        val filter = FfmpegAudioFilters.audioFilter(3, 0.8f)
        assertTrue(filter, filter.contains("asetrate=48000*1.189207"))
        assertTrue(filter, filter.contains("atempo=0.672"))
    }

    @Test
    fun commandIncludesFilterAndNoVideo() {
        val cmd = FfmpegAudioFilters.buildCommand("in.mp3", "out.aac", 0, 0.75f)
        assertEquals("ffmpeg", cmd[0])
        assertTrue(cmd.contains("-filter:a"))
        assertTrue(cmd.contains("-vn"))
        assertEquals("out.aac", cmd.last())
    }

    @Test
    fun loopWrapsToStart() {
        val loop = LoopRegion(4_000L, 16_000L)
        assertEquals(4_000L, LoopPlayback.checkLoop(16_000L, loop))
        assertEquals(8_000L, LoopPlayback.checkLoop(8_000L, loop))
        assertEquals(20_000L, LoopPlayback.checkLoop(20_000L, null))
    }
}
