package com.swaralab.engine.audio

import com.swaralab.domain.audio.FfmpegAudioFilters
import com.swaralab.domain.audio.TrackProcessingConfig
import com.swaralab.domain.audio.TrackProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tries a host `ffmpeg` binary with asetrate+atempo. Falls back to the
 * in-process overlap-add warp when ffmpeg is missing (typical on Android).
 */
class FfmpegTrackProcessor(
    private val fallback: TrackProcessor = OfflineTrackProcessor(),
) : TrackProcessor {
    override suspend fun process(
        input: File,
        output: File,
        config: TrackProcessingConfig,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        if (runFfmpeg(input, output, config)) {
            onProgress(1f)
            return@withContext true
        }
        fallback.process(input, output, config, onProgress)
    }

    private fun runFfmpeg(input: File, output: File, config: TrackProcessingConfig): Boolean {
        val ffmpeg = findFfmpeg() ?: return false
        output.parentFile?.mkdirs()
        val command = FfmpegAudioFilters.buildCommand(
            input = input.absolutePath,
            output = output.absolutePath,
            semitones = config.semitones,
            tempo = config.tempo,
            ffmpeg = ffmpeg,
        )
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching false
            }
            process.exitValue() == 0 && output.isFile && output.length() > 44L
        }.getOrDefault(false)
    }

    companion object {
        fun findFfmpeg(): String? {
            val names = listOf("ffmpeg", "ffmpeg.exe")
            names.forEach { name ->
                val ok = runCatching {
                    val p = ProcessBuilder(name, "-version").redirectErrorStream(true).start()
                    val done = p.waitFor(3, TimeUnit.SECONDS)
                    if (!done) {
                        p.destroyForcibly()
                        false
                    } else {
                        p.exitValue() == 0
                    }
                }.getOrDefault(false)
                if (ok) return name
            }
            return null
        }
    }
}
