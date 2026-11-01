package com.swaralab.engine.render

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Replaces camera audio with the mixed vocal master. */
object CameraMixMuxer {
    fun mux(cameraVideo: File, mixedAudioM4a: File, outFile: File): Boolean = runCatching {
        if (!cameraVideo.isFile || !mixedAudioM4a.isFile) return false
        if (outFile.exists()) outFile.delete()
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(cameraVideo.absolutePath)
        var videoTrack = -1
        for (i in 0 until videoExtractor.trackCount) {
            val mime = videoExtractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) {
                videoTrack = i
                break
            }
        }
        if (videoTrack < 0) {
            videoExtractor.release()
            return false
        }
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(mixedAudioM4a.absolutePath)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoExtractor.selectTrack(videoTrack)
        val outVideo = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
        val outAudio = muxer.addTrack(audioExtractor.getTrackFormat(0))
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
