package com.swaralab.domain.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Peak envelope of a local 16-bit WAV so the booth can draw a take without extra libraries.
 */
object WavPeaks {
    fun fromFile(file: File, buckets: Int = 96): FloatArray {
        if (!file.isFile || buckets < 2 || file.length() < 44L) return FloatArray(0)
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val riff = ByteArray(12)
                raf.readFully(riff)
                if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF") return FloatArray(0)
                if (String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") return FloatArray(0)
                var dataOffset = -1L
                var dataSize = 0
                var channels = 1
                var bits = 16
                while (raf.filePointer + 8 <= raf.length()) {
                    val id = ByteArray(4)
                    raf.readFully(id)
                    val sizeBytes = ByteArray(4)
                    raf.readFully(sizeBytes)
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int.coerceAtLeast(0)
                    val name = String(id, Charsets.US_ASCII)
                    when (name) {
                        "fmt " -> {
                            val fmt = ByteArray(size)
                            raf.readFully(fmt)
                            if (size >= 16) {
                                val buf = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                                buf.short
                                channels = buf.short.toInt().coerceAtLeast(1)
                                buf.int
                                buf.int
                                buf.short
                                bits = buf.short.toInt()
                            }
                            if (size % 2 == 1) raf.skipBytes(1)
                        }
                        "data" -> {
                            dataOffset = raf.filePointer
                            dataSize = size
                            break
                        }
                        else -> {
                            raf.skipBytes(size)
                            if (size % 2 == 1) raf.skipBytes(1)
                        }
                    }
                }
                if (dataOffset < 0L || bits != 16 || dataSize < 2) return FloatArray(0)
                val bytesPerFrame = 2 * channels
                val frames = (dataSize / bytesPerFrame).coerceAtLeast(1)
                val perBucket = (frames / buckets).coerceAtLeast(1)
                val peaks = FloatArray(buckets)
                raf.seek(dataOffset)
                val chunk = ByteArray(4096)
                var remaining = dataSize
                var frameIndex = 0
                while (remaining > 0) {
                    val read = raf.read(chunk, 0, min(chunk.size, remaining))
                    if (read <= 0) break
                    remaining -= read
                    val buf = ByteBuffer.wrap(chunk, 0, read).order(ByteOrder.LITTLE_ENDIAN)
                    while (buf.remaining() >= bytesPerFrame) {
                        var peak = 0
                        repeat(channels) {
                            peak = max(peak, abs(buf.short.toInt()))
                        }
                        val bucket = (frameIndex / perBucket).coerceAtMost(buckets - 1)
                        peaks[bucket] = max(peaks[bucket], peak / 32768f)
                        frameIndex += 1
                    }
                }
                peaks
            }
        }.getOrDefault(FloatArray(0))
    }
}
