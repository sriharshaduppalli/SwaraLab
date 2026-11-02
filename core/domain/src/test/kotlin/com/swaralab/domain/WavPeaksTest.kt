package com.swaralab.domain

import com.swaralab.domain.audio.WavPeaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavPeaksTest {
    @Test
    fun loudThenQuietShowsInThePicture() {
        val file = File.createTempFile("swara-peaks", ".wav")
        writeMonoWav(file, shortArrayOf(30_000, 30_000, 200, 200, 200, 200))
        val peaks = WavPeaks.fromFile(file, buckets = 2)
        file.delete()
        assertEquals(2, peaks.size)
        assertTrue(peaks[0] > 0.7f)
        assertTrue(peaks[1] < 0.2f)
    }

    @Test
    fun missingFileIsEmpty() {
        assertEquals(0, WavPeaks.fromFile(File("no-such-take.wav")).size)
    }

    private fun writeMonoWav(file: File, samples: ShortArray) {
        val dataBytes = samples.size * 2
        val bytes = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII))
        bytes.putInt(36 + dataBytes)
        bytes.put("WAVE".toByteArray(Charsets.US_ASCII))
        bytes.put("fmt ".toByteArray(Charsets.US_ASCII))
        bytes.putInt(16)
        bytes.putShort(1)
        bytes.putShort(1)
        bytes.putInt(48_000)
        bytes.putInt(96_000)
        bytes.putShort(2)
        bytes.putShort(16)
        bytes.put("data".toByteArray(Charsets.US_ASCII))
        bytes.putInt(dataBytes)
        samples.forEach { bytes.putShort(it) }
        file.writeBytes(bytes.array())
    }
}
