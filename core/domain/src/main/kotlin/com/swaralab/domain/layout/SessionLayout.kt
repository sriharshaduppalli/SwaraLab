package com.swaralab.domain.layout

import java.util.Locale
import kotlin.math.abs

object SessionLayout {
    const val DOCUMENT = "session.json"
    const val TAKES = "media/takes"
    const val BACKING = "media/backing"
    const val PROCESSED = "media/processed"
    const val VIDEO = "media/video"
    const val MIDI = "media/midi"
    const val EXPORTS = "exports"

    val directories: List<String> = listOf(TAKES, BACKING, PROCESSED, VIDEO, MIDI, EXPORTS)

    const val MONITOR_BED = "$PROCESSED/monitor-bed.wav"

    fun processedBacking(trackRelativePath: String, semitones: Int, tempo: Float): String {
        val id = trackRelativePath
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(40)
            .ifBlank { "track" }
        val t = "%.2f".format(Locale.US, tempo)
        return "$PROCESSED/${id}_s${semitones}_t$t.wav"
    }

    fun usesProcessedBacking(semitones: Int, tempo: Float): Boolean =
        semitones != 0 || abs(tempo - 1f) >= 0.02f

    fun takeWav(takeId: String): String = "$TAKES/$takeId.wav"

    fun takeAac(takeId: String): String = "$TAKES/$takeId.m4a"

    /** Crash-safe dry vocal while a take is live. Promoted to [takeWav] on stop. */
    const val DRAFT_WAV = "$TAKES/draft.wav"

    fun draftWav(): String = DRAFT_WAV

    const val TAKE_INDEX = "media/takes/index.json"

    fun performanceVideo(takeId: String = "performance"): String = "$VIDEO/$takeId.mp4"

    fun backingFile(fileName: String): String = "$BACKING/$fileName"

    fun exportFile(fileName: String): String = "$EXPORTS/$fileName"

    fun inviteFile(fileName: String): String = "$EXPORTS/$fileName"

    fun isSafeRelative(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (':' in path) return false
        val parts = path.split('/', '\\')
        return parts.none { it.isBlank() || it == ".." || it == "." }
    }
}
