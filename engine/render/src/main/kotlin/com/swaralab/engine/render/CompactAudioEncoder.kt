package com.swaralab.engine.render

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Encodes a mono mix as AAC inside an M4A container so singers can share a
 * smaller file on WhatsApp, Reels, and Shorts.
 */
object CompactAudioEncoder {
    fun writeAacM4a(
        outFile: File,
        sampleRate: Int,
        left: FloatArray,
        right: FloatArray? = null,
    ): Boolean = runCatching {
        if (left.isEmpty()) return false
        outFile.parentFile?.mkdirs()
        if (outFile.exists()) outFile.delete()
        val stereo = right != null && right.isNotEmpty()
        val channels = if (stereo) 2 else 1
        val frames = if (stereo) min(left.size, right!!.size) else left.size

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (stereo) 256_000 else 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32_768)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var inputDone = false
        var outputDone = false
        val pcmChunk = ShortArray(1024 * channels)

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000L)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    if (inBuf != null) {
                        inBuf.clear()
                        val remaining = frames - offset
                        if (remaining <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val count = min(1024, remaining)
                            var w = 0
                            for (i in 0 until count) {
                                val l = (left[offset + i].coerceIn(-1f, 1f) * 32767f).toInt()
                                pcmChunk[w++] = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), l)).toShort()
                                if (stereo) {
                                    val r = (right!![offset + i].coerceIn(-1f, 1f) * 32767f).toInt()
                                    pcmChunk[w++] = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), r)).toShort()
                                }
                            }
                            val bytes = ByteBuffer.allocate(w * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until w) bytes.putShort(pcmChunk[i])
                            bytes.flip()
                            inBuf.put(bytes)
                            val pts = (offset * 1_000_000L) / sampleRate
                            codec.queueInputBuffer(inIndex, 0, w * 2, pts, 0)
                            offset += count
                        }
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0 && muxerStarted) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, outBuf, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
        outFile.isFile && outFile.length() > 0L
    }.getOrDefault(false)
}
