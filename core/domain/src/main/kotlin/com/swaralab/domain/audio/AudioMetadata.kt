package com.swaralab.domain.audio

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val durationMs: Long,
)

interface AudioMetadataReader {
    fun read(absolutePath: String): AudioMetadata
}
