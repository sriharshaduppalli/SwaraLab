package com.swaralab.session

import com.swaralab.domain.codec.TakeIndexCodec
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.ImportedTrackRecord
import com.swaralab.domain.model.TakeIndex
import com.swaralab.domain.model.TakeNote
import com.swaralab.domain.model.TakeRecord
import java.util.UUID
import com.swaralab.domain.port.TakeCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileTakeCatalog(
    private val projectsRoot: File,
    private val codec: TakeIndexCodec = TakeIndexCodec(),
) : TakeCatalog {
    private val lock = Mutex()

    override suspend fun listTracks(sessionId: String): List<ImportedTrackRecord> =
        read(sessionId).tracks

    override suspend fun upsertTrack(sessionId: String, track: ImportedTrackRecord) = mutate(sessionId) { index ->
        index.copy(tracks = index.tracks.filterNot { it.id == track.id } + track)
    }

    override suspend fun listTakes(sessionId: String): List<TakeRecord> = read(sessionId).recordings

    override suspend fun upsertTake(sessionId: String, take: TakeRecord) = mutate(sessionId) { index ->
        index.copy(recordings = index.recordings.filterNot { it.id == take.id } + take)
    }

    override suspend fun markConversion(
        sessionId: String,
        takeId: String,
        status: ConversionStatus,
        aacPath: String?,
    ) = mutate(sessionId) { index ->
        index.copy(
            recordings = index.recordings.map { take ->
                if (take.id != takeId) take else take.copy(
                    conversion = status,
                    aacRelativePath = aacPath ?: take.aacRelativePath,
                )
            },
        )
    }

    override suspend fun pendingConversions(sessionId: String): List<TakeRecord> =
        read(sessionId).recordings.filter { it.conversion == ConversionStatus.PENDING }

    override suspend fun toggleFavorite(sessionId: String, takeId: String): Boolean {
        var next = false
        mutate(sessionId) { index ->
            index.copy(
                recordings = index.recordings.map { take ->
                    if (take.id != takeId) {
                        take
                    } else {
                        next = !take.favorite
                        take.copy(favorite = next)
                    }
                },
            )
        }
        return next
    }

    override suspend fun addNote(sessionId: String, takeId: String, text: String): TakeNote? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val note = TakeNote(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            createdAt = System.currentTimeMillis(),
        )
        var added: TakeNote? = null
        mutate(sessionId) { index ->
            index.copy(
                recordings = index.recordings.map { take ->
                    if (take.id != takeId) {
                        take
                    } else {
                        added = note
                        take.copy(notes = take.notes + note)
                    }
                },
            )
        }
        return added
    }

    override suspend fun deleteNote(sessionId: String, takeId: String, noteId: String) = mutate(sessionId) { index ->
        index.copy(
            recordings = index.recordings.map { take ->
                if (take.id != takeId) take else take.copy(notes = take.notes.filterNot { it.id == noteId })
            },
        )
    }

    override suspend fun pruneTakes(sessionId: String, keepWavPaths: Set<String>): Int {
        var deleted = 0
        mutate(sessionId) { index ->
            val takesDir = File(File(projectsRoot, sessionId), SessionLayout.TAKES)
            takesDir.listFiles()?.forEach { file ->
                if (file.name == "index.json" || file.name.equals("draft.wav", ignoreCase = true)) return@forEach
                val wavRelative = "${SessionLayout.TAKES}/${file.nameWithoutExtension}.wav"
                val ownRelative = "${SessionLayout.TAKES}/${file.name}"
                val keep = ownRelative in keepWavPaths || wavRelative in keepWavPaths
                if (!keep && file.delete()) deleted += 1
            }
            index.copy(recordings = index.recordings.filter { it.dryWavRelativePath in keepWavPaths })
        }
        return deleted
    }

    private suspend fun read(sessionId: String): TakeIndex = withContext(Dispatchers.IO) {
        val file = file(sessionId)
        if (!file.isFile) TakeIndex() else codec.decode(file.readText(Charsets.UTF_8))
    }

    private suspend fun mutate(sessionId: String, block: (TakeIndex) -> TakeIndex) = lock.withLock {
        val next = block(read(sessionId))
        withContext(Dispatchers.IO) {
            val file = file(sessionId)
            file.parentFile?.mkdirs()
            file.writeText(codec.encode(next), Charsets.UTF_8)
        }
    }

    private fun file(sessionId: String) = File(File(projectsRoot, sessionId), SessionLayout.TAKE_INDEX)
}
