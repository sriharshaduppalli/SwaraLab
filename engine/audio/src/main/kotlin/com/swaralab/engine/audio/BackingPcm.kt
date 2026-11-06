package com.swaralab.engine.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Decode a local backing file to 48 kHz mono I16 for the Oboe live mixer. */
internal object BackingPcm {
    fun loadMono48k(file: File, targetRate: Int = 48_000): ShortArray? {
        if (!file.isFile) return null
        readWavMono(file, targetRate)?.let { return it }
        return decodeCompressed(file, targetRate)
    }

    private fun readWavMono(file: File, targetRate: Int): ShortArray? = runCatching {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var offset = 12
        var channels = 1
        var sampleRate = targetRate
        var bits = 16
        var dataStart = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val body = offset + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                val fmt = ByteBuffer.wrap(bytes, body, min(size, bytes.size - body)).order(ByteOrder.LITTLE_ENDIAN)
                fmt.short
                channels = fmt.short.toInt().coerceAtLeast(1)
                sampleRate = fmt.int.coerceAtLeast(1)
                fmt.int
                fmt.short
                bits = fmt.short.toInt() and 0xFFFF
            } else if (id == "data") {
                dataStart = body
                dataSize = size.coerceAtMost(bytes.size - body)
                break
            }
            offset = body + size + (size and 1)
        }
        if (dataStart < 0 || bits != 16) return null
        val frames = dataSize / (2 * channels)
        if (frames <= 0) return null
        val mono = FloatArray(frames)
        val buf = ByteBuffer.wrap(bytes, dataStart, frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val l = buf.short / 32768f
            val r = if (channels > 1) buf.short / 32768f else l
            if (channels > 2) repeat(channels - 2) { buf.short }
            mono[i] = (l + r) * 0.5f
        }
        cap48k(resample(mono, sampleRate, targetRate), targetRate)
    }.getOrNull()

    private fun decodeCompressed(file: File, targetRate: Int): ShortArray? = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var format: MediaFormat? = null
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                format = candidate
                track = i
                break
            }
        }
        if (track < 0 || format == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, targetRate)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val pcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val cap = sourceRate * channels * 2 * 360
        while (!outputDone && pcm.size() < cap) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(8_000L)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 8_000L)
            when {
                outIndex >= 0 -> {
                    val out = codec.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        out.position(info.offset)
                        out.get(chunk)
                        pcm.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outFormat = codec.outputFormat
                    sourceRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sourceRate)
                    channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        val raw = pcm.toByteArray()
        val frames = raw.size / (2 * channels)
        if (frames <= 0) return null
        val shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            val l = shorts.get(i * channels) / 32768f
            val r = if (channels > 1) shorts.get(i * channels + 1) / 32768f else l
            mono[i] = (l + r) * 0.5f
        }
        cap48k(resample(mono, sourceRate, targetRate), targetRate)
    }.getOrNull()

    fun writeWavMono(file: File, samples: ShortArray, sampleRate: Int = 48_000): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val dataBytes = samples.size * 2
        val out = ByteArray(44 + dataBytes)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        samples.forEach { buf.putShort(it) }
        file.writeBytes(out)
        file.length() > 44L
    }.getOrDefault(false)

    fun stretch(samples: ShortArray, rate: Float): ShortArray {
        val factor = rate.coerceIn(0.5f, 1.5f)
        if (kotlin.math.abs(factor - 1f) < 0.01f) return samples
        val dest = ShortArray((samples.size / factor).toInt().coerceAtLeast(1))
        val step = factor.toDouble()
        var pos = 0.0
        for (i in dest.indices) {
            val idx = pos.toInt().coerceIn(0, samples.lastIndex)
            dest[i] = samples[idx]
            pos += step
        }
        return dest
    }

    private fun cap48k(samples: ShortArray, targetRate: Int): ShortArray {
        val max = targetRate * 360
        return if (samples.size <= max) samples else samples.copyOf(max)
    }

    private fun resample(src: FloatArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate <= 0 || fromRate == toRate) {
            return ShortArray(src.size) { i -> (src[i] * 32767f).toInt().coerceIn(-32767, 32767).toShort() }
        }
        val outLen = ((src.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val dest = ShortArray(outLen)
        val step = fromRate.toDouble() / toRate
        var pos = 0.0
        for (i in dest.indices) {
            val idx = pos.toInt().coerceIn(0, src.lastIndex)
            val frac = (pos - idx).toFloat()
            val next = (idx + 1).coerceAtMost(src.lastIndex)
            val sample = src[idx] * (1f - frac) + src[next] * frac
            dest[i] = (sample * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            pos += step
        }
        return dest
    }
}
