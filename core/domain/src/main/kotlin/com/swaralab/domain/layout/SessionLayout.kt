package com.swaralab.domain.layout

object SessionLayout {
    const val DOCUMENT = "session.json"
    const val TAKES = "media/takes"
    const val BACKING = "media/backing"
    const val VIDEO = "media/video"
    const val MIDI = "media/midi"
    const val EXPORTS = "exports"

    val directories: List<String> = listOf(TAKES, BACKING, VIDEO, MIDI, EXPORTS)

    fun takeWav(takeId: String): String = "$TAKES/$takeId.wav"

    fun performanceVideo(): String = "$VIDEO/performance.mp4"

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
