package com.swaralab.domain.port

import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.ImportedTrackRecord
import com.swaralab.domain.model.TakeNote
import com.swaralab.domain.model.TakeRecord

interface TakeCatalog {
    suspend fun listTracks(sessionId: String): List<ImportedTrackRecord>

    suspend fun upsertTrack(sessionId: String, track: ImportedTrackRecord)

    suspend fun listTakes(sessionId: String): List<TakeRecord>

    suspend fun upsertTake(sessionId: String, take: TakeRecord)

    suspend fun markConversion(sessionId: String, takeId: String, status: ConversionStatus, aacPath: String? = null)

    suspend fun pendingConversions(sessionId: String): List<TakeRecord>

    /** Local keep on this phone. Not a social like. */
    suspend fun toggleFavorite(sessionId: String, takeId: String): Boolean

    /** Notes on this take. Stays on this phone. Not a social thread. */
    suspend fun addNote(sessionId: String, takeId: String, text: String): TakeNote?

    suspend fun deleteNote(sessionId: String, takeId: String, noteId: String)

    /**
     * Delete WAV/M4A files and catalog rows whose dry path is not in [keepWavPaths].
     * Always keeps `draft.wav` and `index.json`. Returns how many files were removed.
     */
    suspend fun pruneTakes(sessionId: String, keepWavPaths: Set<String>): Int = 0
}

interface TakeConversion {
    fun enqueueDryAac(sessionId: String, takeId: String, wavAbsolutePath: String, aacAbsolutePath: String)
}
