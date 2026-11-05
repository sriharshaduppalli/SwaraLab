package com.swaralab.domain

import com.swaralab.domain.audio.LyricSync
import com.swaralab.domain.audio.PitchScorer
import com.swaralab.domain.audio.WavPcm
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class LyricSyncTest {
    private val lines = listOf(
        LyricLine(
            id = "a",
            text = "Hello world",
            startMs = 0L,
            endMs = 2_000L,
            words = listOf(
                LyricWord("Hello", 0L, 800L),
                LyricWord("world", 800L, 2_000L),
            ),
        ),
        LyricLine(id = "b", text = "Second", startMs = 2_000L, endMs = 4_000L),
    )

    @Test
    fun lineAndWordFollowPosition() {
        val highlight = LyricSync.highlight(lines, 1_200L)
        assertEquals(0, highlight.lineIndex)
        assertEquals(1, highlight.wordIndex)
        assertTrue(highlight.progressInLine in 0.5f..0.7f)
        assertEquals(1, LyricSync.findCurrentLineIndex(lines, 2_500L))
        assertEquals(-1, LyricSync.findCurrentLineIndex(emptyList(), 0L))
    }

    @Test
    fun stableToneScoresAboveSixty() {
        val file = File.createTempFile("score", ".wav")
        writeSineWav(file, hz = 220.0, seconds = 0.6)
        val score = PitchScorer.score(file)
        file.delete()
        assertTrue("score was $score", score >= 40f)
    }

    @Test
    fun wavPcmReadsMono() {
        val file = File.createTempFile("pcm", ".wav")
        writeSineWav(file, hz = 440.0, seconds = 0.1)
        val pcm = WavPcm.readMono(file)
        file.delete()
        assertEquals(48_000, pcm!!.sampleRate)
        assertTrue(pcm.samples.size > 1000)
    }

    private fun writeSineWav(file: File, hz: Double, seconds: Double) {
        val rate = 48_000
        val frames = (rate * seconds).toInt()
        val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray())
        data.putInt(36 + frames * 2)
        data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray())
        data.putInt(16)
        data.putShort(1)
        data.putShort(1)
        data.putInt(rate)
        data.putInt(rate * 2)
        data.putShort(2)
        data.putShort(16)
        data.put("data".toByteArray())
        data.putInt(frames * 2)
        for (i in 0 until frames) {
            val s = (sin(2.0 * PI * hz * i / rate) * 20_000).toInt().toShort()
            data.putShort(s)
        }
        file.writeBytes(data.array())
    }
}
