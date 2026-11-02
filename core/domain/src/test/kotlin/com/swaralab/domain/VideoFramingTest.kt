package com.swaralab.domain

import com.swaralab.domain.video.VideoFraming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFramingTest {
    @Test
    fun landscapeHdWithRotationLooksLikeNineSixteenOnStage() {
        val (dw, dh) = VideoFraming.displaySize(1280, 720, 90)
        assertEquals(720, dw)
        assertEquals(1280, dh)
        assertTrue(VideoFraming.isPortraitNineSixteen(1280, 720, 90))
        assertFalse(VideoFraming.isPortraitNineSixteen(1280, 720, 0))
    }

    @Test
    fun centerCropTurnsLandscapeIntoNineSixteen() {
        val box = VideoFraming.centerCrop(1920, 1080)
        assertEquals(0, box.y)
        assertTrue(box.width < 1920)
        assertEquals(1080, box.height)
        val aspect = box.width.toFloat() / box.height
        assertTrue(aspect in 0.54f..0.58f)
    }

    @Test
    fun alreadyPortraitKeepsTheFrame() {
        val box = VideoFraming.centerCrop(1080, 1920)
        assertEquals(0, box.x)
        assertEquals(0, box.y)
        assertEquals(1080, box.width)
        assertEquals(1920, box.height)
    }

    @Test
    fun pictureAndSongEndTogether() {
        assertEquals(8_000_000L, VideoFraming.sharedEndUs(12_000_000L, 8_000_000L))
        assertEquals(5_000_000L, VideoFraming.sharedEndUs(5_000_000L, 9_000_000L))
        assertEquals(4_000_000L, VideoFraming.sharedEndUs(0L, 4_000_000L))
    }
}
