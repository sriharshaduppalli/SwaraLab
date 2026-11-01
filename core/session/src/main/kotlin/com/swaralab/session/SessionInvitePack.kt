package com.swaralab.session

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.TrackState
import com.swaralab.domain.time.Clock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Packs a session so another singer can open the lyrics, hear the backing and guide
 * vocals, and record the empty duet part on their phone.
 */
class SessionInvitePack(
    private val root: File,
    private val codec: SessionCodec,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun exportZip(sessionId: String, destination: File): Boolean {
        val folder = File(root, sessionId)
        val documentFile = File(folder, SessionLayout.DOCUMENT)
        if (!documentFile.isFile) return false
        destination.parentFile?.mkdirs()
        return runCatching {
            val document = codec.decode(documentFile.readText(Charsets.UTF_8))
            val allowedTakes = document.tracks
                .filter { it.role != TrackRole.BACKING }
                .flatMap { track ->
                    val preferred = track.clips.filter { it.preferred }
                    (preferred.ifEmpty { track.clips.takeLast(1) }).map { it.sourceRelativePath }
                }
                .toSet()
            ZipOutputStream(destination.outputStream().buffered()).use { zip ->
                folder.walkTopDown()
                    .filter { it.isFile }
                    .filter { file ->
                        val relative = file.relativeTo(folder).invariantSeparatorsPath
                        relative == SessionLayout.DOCUMENT ||
                            relative.startsWith("${SessionLayout.BACKING}/") ||
                            relative in allowedTakes
                    }
                    .forEach { file ->
                        val relative = file.relativeTo(folder).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry(relative))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            destination.isFile && destination.length() > 0L
        }.getOrDefault(false)
    }

    fun importZip(source: File): SessionDocument? {
        if (!source.isFile) return null
        return runCatching {
            val newId = ids.newId()
            val folder = File(root, newId)
            folder.mkdirs()
            SessionLayout.directories.forEach { relative -> File(folder, relative).mkdirs() }
            var decoded: SessionDocument? = null
            ZipFile(source).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val relative = entry.name.replace('\\', '/').trimStart('/')
                    if (!SessionLayout.isSafeRelative(relative) && relative != SessionLayout.DOCUMENT) continue
                    val out = File(folder, relative)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (relative == SessionLayout.DOCUMENT) {
                        decoded = codec.decode(out.readText(Charsets.UTF_8))
                    }
                }
            }
            val original = decoded ?: return@runCatching null
            val now = clock.nowEpochMs()
            val tracks = original.tracks.toMutableList()
            val emptyLeadIndex = tracks.indexOfFirst { it.role == TrackRole.LEAD && it.clips.isEmpty() }
            if (emptyLeadIndex >= 0) {
                tracks[emptyLeadIndex] = tracks[emptyLeadIndex].copy(name = "Your part")
            } else if (tracks.none { it.name == "Your part" }) {
                tracks += TrackState(
                    id = ids.newId(),
                    role = TrackRole.LEAD,
                    name = "Your part",
                )
            }
            val imported = original.copy(
                id = newId,
                title = inviteTitle(original.title),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                tracks = tracks,
            )
            File(folder, SessionLayout.DOCUMENT).writeText(codec.encode(imported), Charsets.UTF_8)
            imported
        }.getOrNull()
    }

    private fun inviteTitle(title: String): String {
        val base = title.removePrefix("Invite · ").ifBlank { "Song" }
        return "Invite · $base"
    }
}
