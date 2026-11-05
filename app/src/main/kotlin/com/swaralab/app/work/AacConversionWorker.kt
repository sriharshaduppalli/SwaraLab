package com.swaralab.app.work

import com.swaralab.domain.audio.WavPcm
import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.port.TakeCatalog
import com.swaralab.domain.port.TakeConversion
import com.swaralab.engine.render.CompactAudioEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Background WAV → AAC. Runs on a process-local IO scope (no network).
 * The dry WAV stays on disk.
 */
class CoroutineTakeConversion(
    private val catalog: TakeCatalog,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TakeConversion {
    override fun enqueueDryAac(
        sessionId: String,
        takeId: String,
        wavAbsolutePath: String,
        aacAbsolutePath: String,
    ) {
        scope.launch {
            val wav = File(wavAbsolutePath)
            val aac = File(aacAbsolutePath)
            val relative = aacAbsolutePath.substringAfter("swaralab/projects/$sessionId/").ifBlank {
                "media/takes/$takeId.m4a"
            }
            val pcm = WavPcm.readMono(wav)
            val ok = pcm != null && CompactAudioEncoder.writeAacM4a(aac, pcm.sampleRate, pcm.samples)
            catalog.markConversion(
                sessionId,
                takeId,
                if (ok) ConversionStatus.DONE else ConversionStatus.FAILED,
                if (ok) relative else null,
            )
        }
    }
}
