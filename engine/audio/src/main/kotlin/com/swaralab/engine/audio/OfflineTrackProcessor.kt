package com.swaralab.engine.audio

import com.swaralab.domain.audio.PcmWarp
import com.swaralab.domain.audio.TrackProcessingConfig
import com.swaralab.domain.audio.TrackProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OfflineTrackProcessor : TrackProcessor {
    override suspend fun process(
        input: File,
        output: File,
        config: TrackProcessingConfig,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        if (config.isIdentity()) {
            return@withContext false
        }
        val pcm = BackingPcm.loadMono48k(input) ?: return@withContext false
        onProgress(0.35f)
        val warped = PcmWarp.shiftAndStretch(
            samples = PcmWarp.toFloat(pcm),
            semitones = config.semitones,
            tempo = config.tempo,
            onProgress = { onProgress(0.35f + it * 0.5f) },
        )
        output.parentFile?.mkdirs()
        onProgress(0.9f)
        val ok = BackingPcm.writeWavMono(output, PcmWarp.toShort(warped), 48_000)
        onProgress(if (ok) 1f else 0f)
        ok
    }
}
