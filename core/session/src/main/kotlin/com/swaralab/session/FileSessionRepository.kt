package com.swaralab.session

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes each session as a folder. session.json is replaced through a temp file so a crash
 * mid-save leaves the previous document in place.
 */
class FileSessionRepository(
    private val root: File,
    private val codec: SessionCodec,
) : SessionRepository {
    override suspend fun save(document: SessionDocument) = withContext(Dispatchers.IO) {
        val folder = File(root, document.id)
        folder.mkdirs()
        SessionLayout.directories.forEach { relative -> File(folder, relative).mkdirs() }
        val target = File(folder, SessionLayout.DOCUMENT)
        val temporary = File(folder, "${SessionLayout.DOCUMENT}.tmp")
        temporary.writeText(codec.encode(document), Charsets.UTF_8)
        moveIntoPlace(temporary, target)
    }

    override suspend fun load(id: String): SessionDocument? = withContext(Dispatchers.IO) {
        val file = File(File(root, id), SessionLayout.DOCUMENT)
        if (!file.isFile) return@withContext null
        codec.decode(file.readText(Charsets.UTF_8))
    }

    override suspend fun list(): List<SessionSummary> = withContext(Dispatchers.IO) {
        if (!root.isDirectory) return@withContext emptyList()
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { folder ->
                val file = File(folder, SessionLayout.DOCUMENT)
                if (!file.isFile) return@mapNotNull null
                runCatching { codec.decode(file.readText(Charsets.UTF_8)) }.getOrNull()
            }
            .map { document ->
                SessionSummary(
                    id = document.id,
                    title = document.title,
                    kind = document.kind,
                    updatedAtEpochMs = document.updatedAtEpochMs,
                    takeCount = document.tracks
                        .filter { it.role != TrackRole.BACKING }
                        .sumOf { it.clips.size },
                    lastPracticeScore = document.practice.scorePercent,
                )
            }
            .sortedByDescending { it.updatedAtEpochMs }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, id).deleteRecursively()
        Unit
    }

    private fun moveIntoPlace(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
