package com.swaralab.engine.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.swaralab.domain.model.SafeFrames
import com.swaralab.domain.model.SessionDocument
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a 9:16 lyric video from the finished mix. Lyrics stay inside the
 * Reels / Shorts / WhatsApp safe frame stored on the session.
 */
object LyricVideoRenderer {
    private const val WIDTH = 720
    private const val HEIGHT = 1280
    private const val FPS = 10

    fun writeMp4(
        outFile: File,
        document: SessionDocument,
        samples: FloatArray,
        sampleRate: Int,
    ): Boolean = runCatching {
        if (samples.isEmpty()) return false
        outFile.parentFile?.mkdirs()
        if (outFile.exists()) outFile.delete()

        val durationUs = (samples.size * 1_000_000L) / sampleRate
        val frameCount = ((durationUs * FPS) / 1_000_000L).toInt().coerceAtLeast(1)
        val audioOk = CompactAudioEncoder.writeAacM4a(
            File(outFile.parentFile, "${outFile.nameWithoutExtension}-audio.m4a"),
            sampleRate,
            samples,
        )
        if (!audioOk) return false

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var audioTrack = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        var frameIndex = 0
        var inputDone = false
        var outputDone = false
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val yuv = ByteArray(WIDTH * HEIGHT * 3 / 2)

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = encoder.dequeueInputBuffer(20_000L)
                if (inIndex >= 0) {
                    if (frameIndex >= frameCount) {
                        encoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val ptsUs = (frameIndex * 1_000_000L) / FPS
                        drawFrame(canvas, document, ptsUs / 1000L)
                        rgbToNv12(bitmap, yuv)
                        val inBuf = encoder.getInputBuffer(inIndex)
                        if (inBuf != null) {
                            inBuf.clear()
                            inBuf.put(yuv)
                            encoder.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
                        }
                        frameIndex += 1
                    }
                }
            }

            val outIndex = encoder.dequeueOutputBuffer(info, 20_000L)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoTrack = muxer.addTrack(encoder.outputFormat)
                    audioTrack = addAacTrack(
                        muxer,
                        File(outFile.parentFile, "${outFile.nameWithoutExtension}-audio.m4a"),
                    )
                    muxer.start()
                    muxerStarted = true
                }
                outIndex >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0 && muxerStarted) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(videoTrack, outBuf, info)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }

        if (muxerStarted && audioTrack >= 0) {
            copyAacSamples(
                muxer,
                audioTrack,
                File(outFile.parentFile, "${outFile.nameWithoutExtension}-audio.m4a"),
            )
        }

        encoder.stop()
        encoder.release()
        bitmap.recycle()
        if (muxerStarted) muxer.stop()
        muxer.release()
        File(outFile.parentFile, "${outFile.nameWithoutExtension}-audio.m4a").delete()
        outFile.isFile && outFile.length() > 0L
    }.getOrDefault(false)

    private fun drawFrame(canvas: Canvas, document: SessionDocument, timeMs: Long) {
        canvas.drawColor(0xFF111827.toInt())
        val insets = SafeFrames.forTarget(document.publish.target)
        val scaleX = WIDTH / SafeFrames.MASTER_WIDTH.toFloat()
        val scaleY = HEIGHT / SafeFrames.MASTER_HEIGHT.toFloat()
        val left = insets.left * scaleX
        val top = insets.top * scaleY
        val right = WIDTH - insets.right * scaleX
        val bottom = HEIGHT - insets.bottom * scaleY

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33F4C430
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 28f, 28f, framePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 36f
            isFakeBoldText = true
        }
        canvas.drawText(document.title.take(28), left + 16f, top + 48f, titlePaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF4C430.toInt()
            textSize = 22f
        }
        canvas.drawText(document.publish.target.name, right - 160f, top + 48f, badgePaint)

        val lines = document.lyrics.lines
        val active = if (lines.any { it.startMs != null }) {
            lines.indexOfLast { line -> (line.startMs ?: Long.MAX_VALUE) <= timeMs }.coerceAtLeast(0)
        } else {
            if (lines.isEmpty()) -1 else ((timeMs / 2500L).toInt() % lines.size)
        }
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 42f
            isFakeBoldText = true
        }
        val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 28f
        }
        val currentText = lines.getOrNull(active)?.text ?: "Sing with SwaraLab"
        val nextText = lines.getOrNull(active + 1)?.text.orEmpty()
        drawWrapped(canvas, currentText, left + 20f, bottom - 150f, right - left - 40f, lyricPaint)
        if (nextText.isNotBlank()) {
            drawWrapped(canvas, nextText, left + 20f, bottom - 70f, right - left - 40f, nextPaint)
        }

        val pulse = 0.18f + 0.08f * kotlin.math.sin(timeMs / 180.0).toFloat()
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4026A69A }
        canvas.drawCircle(WIDTH / 2f, HEIGHT * 0.42f, WIDTH * pulse, glow)
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        paint: Paint,
    ) {
        val words = text.split(' ')
        var line = ""
        var cursorY = y
        for (word in words) {
            val trial = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(trial) > width && line.isNotBlank()) {
                canvas.drawText(line, x, cursorY, paint)
                cursorY += paint.textSize + 8f
                line = word
            } else {
                line = trial
            }
        }
        if (line.isNotBlank()) canvas.drawText(line, x, cursorY, paint)
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
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uvIndex++] = u.coerceIn(0, 255).toByte()
                    out[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun addAacTrack(muxer: MediaMuxer, aacFile: File): Int {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(aacFile.absolutePath)
        val format = extractor.getTrackFormat(0)
        extractor.release()
        return muxer.addTrack(format)
    }

    private fun copyAacSamples(muxer: MediaMuxer, track: Int, aacFile: File) {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(aacFile.absolutePath)
        extractor.selectTrack(0)
        val buffer = ByteBuffer.allocate(64 * 1024)
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
        extractor.release()
    }
}
