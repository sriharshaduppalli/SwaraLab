package com.swaralab.domain.audio

/** Same gain law as the Oboe monitor mix. Used for unit tests and gain sliders. */
object MonitorMix {
    fun sample(voice: Float, track: Float, voiceGain: Float, trackGain: Float): Float =
        (voice * voiceGain.coerceIn(0f, 2f) + track * trackGain.coerceIn(0f, 2f)).coerceIn(-1f, 1f)

    fun process(
        voice: FloatArray,
        track: FloatArray,
        output: FloatArray,
        frames: Int,
        voiceGain: Float,
        trackGain: Float,
    ) {
        val n = frames.coerceAtMost(minOf(voice.size, track.size, output.size))
        for (i in 0 until n) {
            output[i] = sample(voice[i], track[i], voiceGain, trackGain)
        }
    }
}
