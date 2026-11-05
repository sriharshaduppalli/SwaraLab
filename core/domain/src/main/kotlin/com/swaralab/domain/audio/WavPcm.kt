package com.swaralab.domain.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class MonoPcm(
    val sampleRate: Int,
    val samples: FloatArray,
)

object WavPcm {
    fun readMono(file: File, maxSeconds: Int = 360): MonoPcm? {
        if (!file.isFile || file.length() < 44L) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(12)
                raf.readFully(head)
                if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF") return null
                if (String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return null
                var channels = 1
                var sampleRate = 48_000
                var bits = 16
                var dataOffset = -1L
                var dataSize = 0
                while (raf.filePointer + 8 <= raf.length()) {
                    val id = ByteArray(4)
                    raf.readFully(id)
                    val sizeBytes = ByteArray(4)
                    raf.readFully(sizeBytes)
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int.coerceAtLeast(0)
                    when (String(id, Charsets.US_ASCII)) {
                        "fmt " -> {
                            val fmt = ByteArray(size)
                            raf.readFully(fmt)
                            if (size >= 16) {
                                val buf = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                                buf.short
                                channels = buf.short.toInt().coerceAtLeast(1)
                                sampleRate = buf.int.coerceAtLeast(1)
                                buf.int
                                buf.short
                                bits = buf.short.toInt() and 0xFFFF
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
                if (dataOffset < 0L || bits != 16 || dataSize < 2) return null
                val bytesPerFrame = 2 * channels
                val maxFrames = sampleRate * maxSeconds
                val frames = min(dataSize / bytesPerFrame, maxFrames)
                if (frames <= 0) return null
                raf.seek(dataOffset)
                val raw = ByteArray(frames * bytesPerFrame)
                raf.readFully(raw)
                val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                val samples = FloatArray(frames)
                for (i in 0 until frames) {
                    val l = buf.short / 32768f
                    val r = if (channels > 1) buf.short / 32768f else l
                    if (channels > 2) repeat(channels - 2) { buf.short }
                    samples[i] = (l + r) * 0.5f
                }
                MonoPcm(sampleRate, samples)
            }
        }.getOrNull()
    }
}
