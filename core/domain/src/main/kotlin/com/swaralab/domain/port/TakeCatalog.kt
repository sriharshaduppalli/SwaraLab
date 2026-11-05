package com.swaralab.domain.port

import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.ImportedTrackRecord
import com.swaralab.domain.model.TakeRecord

interface TakeCatalog {
    suspend fun listTracks(sessionId: String): List<ImportedTrackRecord>

    suspend fun upsertTrack(sessionId: String, track: ImportedTrackRecord)

    suspend fun listTakes(sessionId: String): List<TakeRecord>

    suspend fun upsertTake(sessionId: String, take: TakeRecord)

    suspend fun markConversion(sessionId: String, takeId: String, status: ConversionStatus, aacPath: String? = null)

    suspend fun pendingConversions(sessionId: String): List<TakeRecord>
}

interface TakeConversion {
    fun enqueueDryAac(sessionId: String, takeId: String, wavAbsolutePath: String, aacAbsolutePath: String)
}
