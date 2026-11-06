package com.swaralab.engine.audio

import com.swaralab.domain.audio.AutocorrelationPitchDetector
import com.swaralab.domain.audio.PitchDetector
import com.swaralab.domain.audio.PitchSample
import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ArrayBlockingQueue

/**
 * Pitch work stays off the Oboe callback. The writer thread copies a buffer
 * into a dropping SPSC queue; this coroutine analyzes about every 30 ms.
 */
class PitchAnalysisPipeline(
    private val pitchDetector: PitchDetector = AutocorrelationPitchDetector(),
    private val sampleRate: Int = 48_000,
) {
    private val queue = ArrayBlockingQueue<FloatArray>(8)
    private val _currentPitch = MutableStateFlow<PitchSample?>(null)
    val currentPitch: StateFlow<PitchSample?> = _currentPitch.asStateFlow()
    private val _metrics = MutableStateFlow(VocalMetrics())
    val metrics: StateFlow<VocalMetrics> = _metrics.asStateFlow()
    private var job: Job? = null
    private var skip = 0
    @Volatile private var rootSemitone: Int = 0

    fun setRootSemitone(value: Int) {
        rootSemitone = value
    }

    fun submitBuffer(samples: ShortArray, length: Int) {
        if (length < 512) return
        skip += 1
        if (skip % 2 != 0) return
        if (queue.remainingCapacity() == 0) return
        val copy = FloatArray(length) { i -> samples[i] / 32768f }
        queue.offer(copy)
    }

    fun start(scope: CoroutineScope) {
        stop()
        _currentPitch.value = null
        job = scope.launch {
            while (isActive) {
                val buffer = queue.poll()
                if (buffer != null) {
                    val sample = pitchDetector.process(buffer, sampleRate, System.currentTimeMillis())
                    _currentPitch.value = sample
                    val shorts = ShortArray(buffer.size) { i ->
                        (buffer[i] * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    _metrics.value = VocalSignalAnalyzer.analyzeFrame(
                        samples = shorts,
                        length = shorts.size,
                        sampleRate = sampleRate,
                        previous = _metrics.value,
                        rootSemitone = rootSemitone,
                    )
                }
                delay(30)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        queue.clear()
        _currentPitch.value = null
    }
}
