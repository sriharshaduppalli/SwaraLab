package com.swaralab.domain.audio

object BackingPlayback {
    fun shouldLoop(relativePath: String, durationMs: Long): Boolean {
        val name = relativePath.substringAfterLast('/').lowercase()
        if (name.contains("drone") || name.contains("tanpura") || name.contains("guide")) {
            return true
        }
        return durationMs in 1L..20_000L
    }
}
