package com.swaralab.engine.audio

/**
 * Oboe (Apache 2.0) low-latency mic plus optional monitor mix.
 * Dry samples stay on the reader thread; wet voice + backing leave on the output stream.
 */
internal object OboeMic {
    val available: Boolean = runCatching {
        System.loadLibrary("swaralab_oboe")
        true
    }.getOrDefault(false)

    /** 0 = failed, 1 = input only, 2 = input + live monitor mix. */
    fun start(sampleRate: Int, voicePerformance: Boolean, monitor: Boolean): Int {
        if (!available) return 0
        return runCatching { nativeStart(sampleRate, voicePerformance, monitor) }.getOrDefault(0)
    }

    fun setBacking(samples: ShortArray?, loop: Boolean, startFrame: Int) {
        if (available) runCatching { nativeSetBacking(samples, loop, startFrame) }
    }

    fun setMonitor(reverb: Float, delay: Float, voiceGain: Float, trackGain: Float) {
        if (available) runCatching { nativeSetMonitor(reverb, delay, voiceGain, trackGain) }
    }

    fun setPaused(paused: Boolean) {
        if (available) runCatching { nativeSetPaused(paused) }
    }

    fun seek(startFrame: Int) {
        if (available) runCatching { nativeSeek(startFrame) }
    }

    fun setLoopRegion(startFrame: Int, endFrame: Int, enabled: Boolean) {
        if (available) runCatching { nativeSetLoopRegion(startFrame, endFrame, enabled) }
    }

    fun playHeadFrames(): Int = if (available) runCatching { nativePlayHead() }.getOrDefault(-1) else -1

    fun setEffect(type: Int, params: FloatArray) {
        if (available) runCatching { nativeSetEffect(type, params) }
    }

    fun read(dest: ShortArray, length: Int): Int {
        if (!available || length <= 0) return 0
        return runCatching { nativeRead(dest, length) }.getOrDefault(0)
    }

    fun stop() {
        if (available) runCatching { nativeStop() }
    }

    fun isRunning(): Boolean = available && runCatching { nativeIsRunning() }.getOrDefault(false)

    @JvmStatic
    private external fun nativeStart(sampleRate: Int, voicePerformance: Boolean, monitor: Boolean): Int

    @JvmStatic
    private external fun nativeSetBacking(samples: ShortArray?, loop: Boolean, startFrame: Int)

    @JvmStatic
    private external fun nativeSetMonitor(reverb: Float, delay: Float, voiceGain: Float, trackGain: Float)

    @JvmStatic
    private external fun nativeSetPaused(paused: Boolean)

    @JvmStatic
    private external fun nativeSeek(startFrame: Int)

    @JvmStatic
    private external fun nativeSetLoopRegion(startFrame: Int, endFrame: Int, enabled: Boolean)

    @JvmStatic
    private external fun nativePlayHead(): Int

    @JvmStatic
    private external fun nativeSetEffect(type: Int, params: FloatArray)

    @JvmStatic
    private external fun nativeRead(dest: ShortArray, length: Int): Int

    @JvmStatic
    private external fun nativeStop()

    @JvmStatic
    private external fun nativeIsRunning(): Boolean
}

internal interface MicCapture {
    val liveMix: Boolean
    fun read(dest: ShortArray, length: Int): Int
    fun release()
}
