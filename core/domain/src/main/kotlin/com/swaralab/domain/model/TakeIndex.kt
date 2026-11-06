package com.swaralab.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConversionStatus {
    PENDING,
    DONE,
    FAILED,
}

/** Local imported backing. No community catalog. */
@Serializable
data class ImportedTrackRecord(
    val id: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long,
    val localRelativePath: String,
    val hasLyrics: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class TakeNote(
    val id: String,
    val text: String,
    val createdAt: Long,
)

@Serializable
data class TakeRecord(
    val id: String,
    val trackId: String? = null,
    val dryWavRelativePath: String,
    val aacRelativePath: String? = null,
    val durationMs: Long,
    val score: Float? = null,
    val title: String? = null,
    val createdAt: Long,
    val conversion: ConversionStatus = ConversionStatus.PENDING,
    val favorite: Boolean = false,
    val notes: List<TakeNote> = emptyList(),
)

@Serializable
data class TakeIndex(
    val tracks: List<ImportedTrackRecord> = emptyList(),
    val recordings: List<TakeRecord> = emptyList(),
)
