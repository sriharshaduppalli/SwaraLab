package com.swaralab.domain.usecase

import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.TakeRecord
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.TakeCatalog
import com.swaralab.domain.port.TakeConversion
import java.io.File

/**
 * Keep a dry take in the session, index it locally, and enqueue a companion AAC.
 * No public flag, no cloud, no mixed file required.
 */
class SaveTake(
    private val commit: CommitRecordedTake,
    private val catalog: TakeCatalog? = null,
    private val conversion: TakeConversion? = null,
    private val projectsRoot: File? = null,
) {
    suspend operator fun invoke(
        sessionId: String,
        trackId: String?,
        take: FinishedTake,
        timelineStartMs: Long = 0L,
        punchEndMs: Long = 0L,
        backingRelativePath: String? = null,
        title: String? = null,
    ): SessionEdit {
        val edit = commit(sessionId, trackId, take, timelineStartMs, punchEndMs)
        if (edit !is SessionEdit.Applied) return edit
        val aacRelative = SessionLayout.takeAac(take.takeId)
        catalog?.upsertTake(
            sessionId,
            TakeRecord(
                id = take.takeId,
                trackId = backingRelativePath,
                dryWavRelativePath = take.relativePath,
                aacRelativePath = aacRelative,
                durationMs = take.durationMs,
                score = take.score,
                title = title,
                createdAt = System.currentTimeMillis(),
                conversion = ConversionStatus.PENDING,
            ),
        )
        val root = projectsRoot
        if (conversion != null && root != null) {
            conversion.enqueueDryAac(
                sessionId,
                take.takeId,
                File(File(root, sessionId), take.relativePath).absolutePath,
                File(File(root, sessionId), aacRelative).absolutePath,
            )
        }
        return edit
    }
}
