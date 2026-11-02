package com.swaralab.engine.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.swaralab.domain.video.VideoFraming
import java.io.File
import java.nio.ByteBuffer

/** Replaces camera audio with the mixed vocal master and bakes a 9:16 1080p picture. */
object CameraMixMuxer {
    fun mux(cameraVideo: File, mixedAudioM4a: File, outFile: File): Boolean {
        if (!cameraVideo.isFile || !mixedAudioM4a.isFile) return false
        if (writePortrait(cameraVideo, mixedAudioM4a, outFile)) return true
        return copyTracks(cameraVideo, mixedAudioM4a, outFile)
    }

    private fun writePortrait(cameraVideo: File, mixedAudioM4a: File, outFile: File): Boolean = runCatching {
        if (outFile.exists()) outFile.delete()
        val extractor = MediaExtractor()
        extractor.setDataSource(cameraVideo.absolutePath)
        val videoTrack = firstVideoTrack(extractor) ?: run {
            extractor.release()
            return false
        }
        extractor.selectTrack(videoTrack)
        val inFormat = extractor.getTrackFormat(videoTrack)
        val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: run {
            extractor.release()
            return false
        }
        val rotation = if (inFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            inFormat.getInteger(MediaFormat.KEY_ROTATION)
        } else {
            0
        }

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inFormat, null, null, 0)
        decoder.start()

        val sizes = listOf(
            VideoFraming.PORTRAIT_WIDTH to VideoFraming.PORTRAIT_HEIGHT,
            720 to 1280,
        )
        var encoder: MediaCodec? = null
        var outW = VideoFraming.PORTRAIT_WIDTH
        var outH = VideoFraming.PORTRAIT_HEIGHT
        for ((w, h) in sizes) {
            val tried = runCatching {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                    setInteger(MediaFormat.KEY_BIT_RATE, if (w >= 1080) 10_000_000 else 4_500_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                    it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    it.start()
                }
            }.getOrNull()
            if (tried != null) {
                encoder = tried
                outW = w
                outH = h
                break
            }
        }
        if (encoder == null) {
            decoder.stop()
            decoder.release()
            extractor.release()
            return false
        }

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var outVideo = -1
        var outAudio = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        val yuv = ByteArray(outW * outH * 3 / 2)
        var inputDone = false
        var outputDone = false
        var sawDecoderEos = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(20_000L)
                if (inIndex >= 0) {
                    val inBuf = decoder.getInputBuffer(inIndex)
                    val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                        extractor.advance()
                    }
                }
            }

            val decIndex = decoder.dequeueOutputBuffer(info, 20_000L)
            if (decIndex >= 0) {
                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                if (info.size > 0) {
                    val image = runCatching { decoder.getOutputImage(decIndex) }.getOrNull()
                    val framed = image?.let { frameToPortrait(it, rotation, outW, outH) }
                    image?.close()
                    if (framed != null) {
                        rgbToNv12(framed, yuv)
                        framed.recycle()
                        feedEncoder(encoder, yuv, info.presentationTimeUs, false)
                    }
                }
                decoder.releaseOutputBuffer(decIndex, false)
                if (eos) {
                    feedEncoder(encoder, yuv, info.presentationTimeUs, true)
                    sawDecoderEos = true
                }
            }

            val encIndex = encoder.dequeueOutputBuffer(info, 20_000L)
            when {
                encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outVideo = muxer.addTrack(encoder.outputFormat)
                    outAudio = addAacTrack(muxer, mixedAudioM4a)
                    muxer.start()
                    muxerStarted = true
                }
                encIndex >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(encIndex)
                    if (outBuf != null && info.size > 0 && muxerStarted) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(outVideo, outBuf, info)
                    }
                    encoder.releaseOutputBuffer(encIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
            if (sawDecoderEos && !outputDone && encIndex < 0 && decIndex < 0) {
                feedEncoder(encoder, yuv, 0L, true)
            }
        }

        if (muxerStarted && outAudio >= 0) {
            copyAacSamples(muxer, outAudio, mixedAudioM4a)
        }

        encoder.stop()
        encoder.release()
        decoder.stop()
        decoder.release()
        extractor.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
        outFile.isFile && outFile.length() > 0L
    }.getOrDefault(false)

    private fun feedEncoder(encoder: MediaCodec, yuv: ByteArray, ptsUs: Long, eos: Boolean) {
        val inIndex = encoder.dequeueInputBuffer(50_000L)
        if (inIndex < 0) return
        if (eos) {
            encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return
        }
        val inBuf = encoder.getInputBuffer(inIndex) ?: return
        inBuf.clear()
        inBuf.put(yuv)
        encoder.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
    }

    private fun frameToPortrait(image: Image, rotation: Int, outW: Int, outH: Int): Bitmap? {
        val raw = yuv420ToBitmap(image) ?: return null
        val oriented = if (rotation % 360 == 0) {
            raw
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val spun = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (spun != raw) raw.recycle()
            spun
        }
        val crop = VideoFraming.centerCrop(oriented.width, oriented.height)
        val safeX = crop.x.coerceIn(0, (oriented.width - 2).coerceAtLeast(0))
        val safeY = crop.y.coerceIn(0, (oriented.height - 2).coerceAtLeast(0))
        val safeW = crop.width.coerceAtMost(oriented.width - safeX).coerceAtLeast(2)
        val safeH = crop.height.coerceAtMost(oriented.height - safeY).coerceAtLeast(2)
        val cropped = Bitmap.createBitmap(oriented, safeX, safeY, safeW, safeH)
        if (cropped != oriented) oriented.recycle()
        val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
        if (scaled != cropped) cropped.recycle()
        return scaled
    }

    private fun yuv420ToBitmap(image: Image): Bitmap? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride
        val pixels = IntArray(width * height)
        var out = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                val y = yBuf.get(row * yRow + col * yPix).toInt() and 0xFF
                val u = (uBuf.get((row / 2) * uRow + (col / 2) * uPix).toInt() and 0xFF) - 128
                val v = (vBuf.get((row / 2) * vRow + (col / 2) * vPix).toInt() and 0xFF) - 128
                val yAdj = (y - 16).coerceAtLeast(0)
                val r = (1.164f * yAdj + 1.596f * v).toInt()
                val g = (1.164f * yAdj - 0.392f * u - 0.813f * v).toInt()
                val b = (1.164f * yAdj + 2.017f * u).toInt()
                pixels[out++] = (0xFF shl 24) or
                    (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    b.coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun rgbToNv12(bitmap: Bitmap, out: ByteArray) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var yIndex = 0
        var uvIndex = width * height
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = pixels[index++]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < out.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uvIndex++] = u.coerceIn(0, 255).toByte()
                    out[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun copyTracks(cameraVideo: File, mixedAudioM4a: File, outFile: File): Boolean = runCatching {
        if (outFile.exists()) outFile.delete()
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(cameraVideo.absolutePath)
        val videoTrack = firstVideoTrack(videoExtractor)
        if (videoTrack == null) {
            videoExtractor.release()
            return false
        }
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(mixedAudioM4a.absolutePath)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoExtractor.selectTrack(videoTrack)
        val format = videoExtractor.getTrackFormat(videoTrack)
        val outVideo = muxer.addTrack(format)
        val outAudio = muxer.addTrack(audioExtractor.getTrackFormat(0))
        if (format.containsKey(MediaFormat.KEY_ROTATION)) {
            muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
        }
        muxer.start()
        copy(videoExtractor, muxer, outVideo)
        audioExtractor.selectTrack(0)
        copy(audioExtractor, muxer, outAudio)
        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
        outFile.isFile && outFile.length() > 0L
    }.getOrDefault(false)

    private fun firstVideoTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) return i
        }
        return null
    }

    private fun addAacTrack(muxer: MediaMuxer, aacFile: File): Int {
        val extractor = MediaExtractor()
        extractor.setDataSource(aacFile.absolutePath)
        val format = extractor.getTrackFormat(0)
        extractor.release()
        return muxer.addTrack(format)
    }

    private fun copyAacSamples(muxer: MediaMuxer, track: Int, aacFile: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(aacFile.absolutePath)
        extractor.selectTrack(0)
        copy(extractor, muxer, track)
        extractor.release()
    }

    private fun copy(extractor: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
    }
}
