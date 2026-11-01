package com.swaralab.domain.audio

/**
 * Advances pasted (untimed) lyric lines from the live vocal, not a clock.
 * A breath after a sung phrase moves to the next line. If the singer does
 * not pause, the line still moves after a word-count estimate.
 */
object LiveLyricFollower {
    data class State(
        val lineIndex: Int = 0,
        val heardVoiceOnLine: Boolean = false,
        val voicedMs: Long = 0,
        val silentMs: Long = 0,
    )

    fun estimatedLineMs(text: String, language: LyricLanguage = LyricLanguage.ENGLISH): Long {
        val units = LyricLineMatcher.tokenCount(text, language)
        val msPerUnit = if (language.usesAksharaTiming) 220L else 480L
        return (units * msPerUnit).coerceIn(1_400L, 8_000L)
    }

    fun isVoiced(pitchHz: Float, rmsDb: Float, silentFrames: Int): Boolean {
        val hasTone = pitchHz > 70f && silentFrames == 0
        val hasLevel = rmsDb > -36f
        return hasTone || hasLevel
    }

    fun step(
        previous: State,
        lineCount: Int,
        currentLineText: String,
        voiced: Boolean,
        dtMs: Long,
        language: LyricLanguage = LyricLanguage.ENGLISH,
        heardText: String? = null,
        allLines: List<String> = emptyList(),
    ): State {
        if (lineCount <= 0 || dtMs <= 0L) return previous
        val matched = heardText?.let { LyricLineMatcher.bestLine(it, allLines, previous.lineIndex) }
        if (matched != null && matched != previous.lineIndex) {
            return State(lineIndex = matched)
        }
        val index = previous.lineIndex.coerceIn(0, lineCount - 1)
        val lastLine = index >= lineCount - 1
        val estimate = estimatedLineMs(currentLineText, language)

        if (voiced) {
            val voicedMs = previous.voicedMs + dtMs
            if (!lastLine && voicedMs >= estimate) {
                return State(lineIndex = index + 1)
            }
            return State(
                lineIndex = index,
                heardVoiceOnLine = true,
                voicedMs = voicedMs,
                silentMs = 0,
            )
        }

        if (!previous.heardVoiceOnLine) {
            return previous.copy(silentMs = previous.silentMs + dtMs)
        }
        val silentMs = previous.silentMs + dtMs
        if (!lastLine && silentMs >= 400L && previous.voicedMs >= 280L) {
            return State(lineIndex = index + 1)
        }
        return previous.copy(silentMs = silentMs)
    }
}
