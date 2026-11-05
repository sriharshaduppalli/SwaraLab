package com.swaralab.app.media

import android.media.MediaMetadataRetriever
import com.swaralab.domain.audio.AudioMetadata
import com.swaralab.domain.audio.AudioMetadataReader

class AndroidMetadataReader : AudioMetadataReader {
    override fun read(absolutePath: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            AudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L,
            )
        } catch (_: Throwable) {
            AudioMetadata(title = null, artist = null, durationMs = 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
