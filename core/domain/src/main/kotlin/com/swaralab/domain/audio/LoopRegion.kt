package com.swaralab.domain.audio

data class LoopRegion(
    val startMs: Long,
    val endMs: Long,
) {
    fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs

    fun wrap(positionMs: Long): Long =
        if (positionMs >= endMs) startMs else positionMs
}

object LoopPlayback {
    fun checkLoop(positionMs: Long, loop: LoopRegion?): Long {
        if (loop == null || loop.endMs <= loop.startMs) return positionMs
        return if (positionMs >= loop.endMs) loop.startMs else positionMs
    }
}
