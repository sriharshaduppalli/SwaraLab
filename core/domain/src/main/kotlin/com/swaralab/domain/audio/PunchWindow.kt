package com.swaralab.domain.audio

/**
 * Sample-accurate punch capture window. Timeline frames are backing playhead frames,
 * not wall-clock milliseconds.
 */
data class PunchSlice(
    val writeFrom: Int,
    val writeTo: Int,
    val preroll: Boolean,
    val finished: Boolean,
) {
    val writeFrames: Int get() = (writeTo - writeFrom).coerceAtLeast(0)
}

object PunchWindow {
    fun frames(positionMs: Long, sampleRate: Int): Long =
        (positionMs.coerceAtLeast(0L) * sampleRate.toLong()) / 1_000L

    fun slice(
        timelineFrame: Long,
        frames: Int,
        punchStartFrame: Long,
        punchEndFrame: Long,
    ): PunchSlice {
        val count = frames.coerceAtLeast(0)
        if (count <= 0) return PunchSlice(0, 0, preroll = false, finished = false)
        val start = punchStartFrame.coerceAtLeast(0L)
        val end = if (punchEndFrame > start) punchEndFrame else Long.MAX_VALUE
        val last = timelineFrame + count
        if (last <= start) {
            return PunchSlice(0, 0, preroll = start > 0L, finished = false)
        }
        if (timelineFrame >= end) {
            return PunchSlice(0, 0, preroll = false, finished = true)
        }
        val from = (start - timelineFrame).coerceIn(0L, count.toLong()).toInt()
        val to = if (end == Long.MAX_VALUE) {
            count
        } else {
            (end - timelineFrame).coerceIn(0L, count.toLong()).toInt()
        }
        return PunchSlice(
            writeFrom = from,
            writeTo = to.coerceAtLeast(from),
            preroll = timelineFrame < start,
            finished = end != Long.MAX_VALUE && last >= end,
        )
    }
}
