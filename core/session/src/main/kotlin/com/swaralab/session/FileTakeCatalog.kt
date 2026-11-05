package com.swaralab.session

import com.swaralab.domain.codec.TakeIndexCodec
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.ImportedTrackRecord
import com.swaralab.domain.model.TakeIndex
import com.swaralab.domain.model.TakeRecord
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
