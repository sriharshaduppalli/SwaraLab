package com.swaralab.domain.audio

import com.swaralab.domain.model.LyricLine

data class LyricHighlight(
    val lineIndex: Int = -1,
    val wordIndex: Int = -1,
    val progressInLine: Float = 0f,
)

object LyricSync {
    fun findCurrentLineIndex(lyrics: List<LyricLine>?, positionMs: Long): Int {
        if (lyrics.isNullOrEmpty()) return -1
        val timed = lyrics.indexOfLast { line ->
            val start = line.startMs
            start != null && positionMs >= start
        }
        return timed
    }

    fun findCurrentWord(line: LyricLine?, positionMs: Long): Int {
        val words = line?.words.orEmpty()
        if (words.isEmpty()) return -1
        return words.indexOfLast { positionMs >= it.startMs }
    }

    fun highlight(lyrics: List<LyricLine>?, positionMs: Long): LyricHighlight {
        val lineIndex = findCurrentLineIndex(lyrics, positionMs)
        val line = lyrics?.getOrNull(lineIndex)
        return LyricHighlight(
            lineIndex = lineIndex,
            wordIndex = findCurrentWord(line, positionMs),
            progressInLine = lineProgress(line, positionMs),
        )
    }

    fun lineProgress(line: LyricLine?, positionMs: Long): Float {
        if (line == null) return 0f
        val start = line.startMs ?: return 0f
        val end = line.endMs ?: line.words.lastOrNull()?.endMs ?: (start + 4_000L)
        val duration = (end - start).coerceAtLeast(1L)
        return ((positionMs - start).toFloat() / duration).coerceIn(0f, 1f)
    }
}
