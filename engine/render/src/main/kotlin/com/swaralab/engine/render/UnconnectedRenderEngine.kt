package com.swaralab.engine.render

import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine

/**
 * Holds the publish contract until Media3 mux and the offline vocal chain share one render graph.
 * Review playback and export will both call that graph, so the file matches what the singer heard.
 */
class UnconnectedRenderEngine : RenderEngine {
    override suspend fun export(document: SessionDocument, request: ExportRequest): ExportResult {
        val loudness = document.publish.loudnessLufs.toInt()
        val peak = document.publish.truePeakDbtp.toInt()
        return ExportResult.Waiting(
            "Set for ${request.target.name.lowercase()} at $loudness LUFS and $peak dBTP. The mix writes the file when the render engine is connected.",
        )
    }
}
