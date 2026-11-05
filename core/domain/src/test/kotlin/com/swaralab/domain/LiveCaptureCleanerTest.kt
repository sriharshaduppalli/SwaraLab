package com.swaralab.domain

import com.swaralab.domain.audio.LiveCaptureCleaner
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LiveCaptureCleanerTest {
    @Test
    fun liveGateDropsGapHissAndKeepsTheSungTone() {
        val sampleRate = 48_000
        val shorts = ShortArray(sampleRate) { i ->
            val hiss = (((i * 1103515245 + 12345) and 0x7fff) / 32768f * 0.10f - 0.05f)
            val sample = if (i < sampleRate / 3) {
                hiss
            } else {
                hiss + 0.32f * sin(2.0 * PI * 220.0 * i / sampleRate).toFloat()
            }
            (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        val beforeHiss = rms(shorts, 0, sampleRate / 4)
        val beforeTone = rms(shorts, sampleRate / 2, sampleRate / 2 + 8192)
        LiveCaptureCleaner(sampleRate).process(shorts, shorts.size)
        val afterHiss = rms(shorts, 0, sampleRate / 4)
        val afterTone = rms(shorts, sampleRate / 2, sampleRate / 2 + 8192)
        assertTrue("Gap hiss should drop, before=$beforeHiss after=$afterHiss", afterHiss < beforeHiss * 0.55f)
        assertTrue("Sung tone should stay, before=$beforeTone after=$afterTone", afterTone > beforeTone * 0.70f)
    }

    private fun rms(samples: ShortArray, start: Int, end: Int): Float {
        var sum = 0.0
        val from = start.coerceIn(0, samples.lastIndex)
        val to = end.coerceIn(from + 1, samples.size)
        for (i in from until to) {
            val x = samples[i] / 32768f
            sum += x * x
        }
        return kotlin.math.sqrt(sum / (to - from)).toFloat()
    }
}
