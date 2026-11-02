package com.swaralab.domain.audio

import com.swaralab.domain.model.ScaleMode
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mix-only vocal cleanup. The recorded take stays dry; this runs on the bounce.
 * Spectral noise reduction, de-ess, breath control, EQ, dynamics, then
 * musical delay/reverb from a dry copy so tails do not feed themselves.
 */
object VocalMixProcessor {
    fun applyChain(
        samples: FloatArray,
        sampleRate: Int,
        chain: VocalChain,
        keyName: String? = "C",
        mode: ScaleMode? = null,
    ) {
        if (samples.isEmpty() || !chain.enabled) return
        highPass(samples, sampleRate, cutoffHz = 85.0)
        val denoise = chain.denoise.coerceIn(0f, 1f).coerceAtLeast(0.55f)
        spectralDenoise(samples, sampleRate, denoise)
        val breath = chain.breathReduction.coerceIn(0f, 1f).let { if (it <= 0f) 0.35f else it }
        reduceBreaths(samples, sampleRate, breath)
        val deEss = chain.deEss.coerceIn(0f, 1f).let { if (it <= 0f && chain.preset != VocalPreset.RAW) 0.28f else it }
        if (deEss > 0.02f) deEss(samples, sampleRate, deEss)
        if (chain.pitchCorrect > 0.04f) {
            correctPitchToKey(samples, sampleRate, keyName, mode, chain.pitchCorrect)
        }
        applyEq(samples, sampleRate, chain)
        if (chain.compression > 0.02f || chain.rider > 0.02f) {
            compress(samples, sampleRate, chain.compression, chain.rider)
        }
        if (chain.thicken > 0.04f) {
            thicken(samples, sampleRate, chain.thicken)
        }
        val dry = samples.copyOf()
        val wet = chain.effectAmount.coerceIn(0f, 1f)
        if (chain.delay > 0.01f && wet > 0.01f) addDelay(samples, dry, sampleRate, chain.delay * wet)
        if (chain.reverb > 0.01f && wet > 0.01f) addReverb(samples, dry, sampleRate, chain.reverb * wet)
        if (abs(chain.outputGainDb) > 0.05f) {
            val gain = 10.0.pow(chain.outputGainDb / 20.0).toFloat()
            for (i in samples.indices) samples[i] *= gain
        }
        val durationMs = (samples.size * 1000L / sampleRate).toInt()
        val fadeOutMs = (400 + chain.fadeOut * 1_600f).toInt().coerceAtMost((durationMs / 5).coerceAtLeast(40))
        fadeEnds(samples, sampleRate, fadeInMs = 45, fadeOutMs = fadeOutMs)
    }

    fun finishMaster(
        samples: FloatArray,
        targetLufs: Float = -14f,
        truePeakDbtp: Float = -1f,
    ) {
        if (samples.isEmpty()) return
        var sumSq = 0.0
        var peak = 0f
        for (sample in samples) {
            val mag = abs(sample)
            if (mag > peak) peak = mag
            sumSq += sample * sample
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        if (rms < 1e-6f) return
        val targetRms = 10.0.pow(targetLufs / 20.0).toFloat().coerceIn(0.08f, 0.28f)
        val loudnessGain = (targetRms / rms).coerceIn(0.35f, 3.5f)
        val ceiling = 10.0.pow(truePeakDbtp / 20.0).toFloat().coerceIn(0.7f, 0.95f)
        for (i in samples.indices) {
            var x = samples[i] * loudnessGain
            if (abs(x) > ceiling) {
                x = ceiling * if (x >= 0f) 1f else -1f
            }
            samples[i] = x
        }
    }

    internal fun noiseFloor(samples: FloatArray): Float {
        var sumSq = 0.0
        for (sample in samples) sumSq += sample * sample
        return sqrt(sumSq / samples.size.coerceAtLeast(1)).toFloat()
    }

    private fun highPass(samples: FloatArray, sampleRate: Int, cutoffHz: Double) {
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        val a = (rc / (rc + dt)).toFloat()
        var prevX = samples.firstOrNull() ?: return
        var prevY = 0f
        for (i in samples.indices) {
            val x = samples[i]
            val y = a * (prevY + x - prevX)
            samples[i] = y
            prevX = x
            prevY = y
        }
    }

    private fun spectralDenoise(samples: FloatArray, sampleRate: Int, amount: Float) {
        val n = 1024
        if (samples.size < n * 2) {
            expander(samples, amount)
            return
        }
        val hop = 256
        val window = FloatArray(n) { i -> (0.5f * (1.0 - cos(2.0 * PI * i / (n - 1))).toFloat()) }
        val frameCount = 1 + (samples.size - n) / hop
        val re = FloatArray(n)
        val im = FloatArray(n)
        val mag = Array(frameCount) { FloatArray(n / 2 + 1) }
        val phase = Array(frameCount) { FloatArray(n / 2 + 1) }
        val rms = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            val start = f * hop
            var energy = 0.0
            for (i in 0 until n) {
                val x = samples[start + i] * window[i]
                re[i] = x
                im[i] = 0f
                energy += x * x
            }
            rms[f] = sqrt(energy / n).toFloat()
            RealFft.forward(re, im)
            for (k in 0..n / 2) {
                mag[f][k] = sqrt(re[k] * re[k] + im[k] * im[k])
                phase[f][k] = kotlin.math.atan2(im[k], re[k])
            }
        }
        val ranked = rms.copyOf().apply { sort() }
        val noiseCut = ranked[(ranked.size * 0.18f).toInt().coerceIn(0, ranked.lastIndex)]
        val noise = FloatArray(n / 2 + 1)
        var noiseFrames = 0
        for (f in 0 until frameCount) {
            if (rms[f] <= noiseCut * 1.05f) {
                noiseFrames += 1
                for (k in noise.indices) noise[k] += mag[f][k]
            }
        }
        if (noiseFrames == 0) {
            val quietest = rms.indices.minBy { rms[it] }
            for (k in noise.indices) noise[k] = mag[quietest][k]
            noiseFrames = 1
        }
        for (k in noise.indices) noise[k] /= noiseFrames.toFloat()

        val alpha = 1.15f + amount * 2.1f
        val floor = (0.06f - amount * 0.03f).coerceIn(0.02f, 0.08f)
        val rumbleBins = ((80.0 * n) / sampleRate).toInt().coerceAtLeast(1)
        for (f in 0 until frameCount) {
            for (k in mag[f].indices) {
                val nMag = noise[k]
                val sMag = mag[f][k]
                var gain = if (sMag > 1e-8f) 1f - alpha * nMag / sMag else 0f
                gain = gain.coerceIn(floor, 1f)
                if (k <= rumbleBins) gain *= 0.15f
                mag[f][k] = sMag * gain
            }
        }

        val overlap = FloatArray(samples.size)
        val weight = FloatArray(samples.size)
        for (f in 0 until frameCount) {
            re[0] = mag[f][0]
            im[0] = 0f
            if (n / 2 < re.size) {
                re[n / 2] = mag[f][n / 2]
                im[n / 2] = 0f
            }
            for (k in 1 until n / 2) {
                re[k] = (mag[f][k] * kotlin.math.cos(phase[f][k]))
                im[k] = (mag[f][k] * kotlin.math.sin(phase[f][k]))
                re[n - k] = re[k]
                im[n - k] = -im[k]
            }
            RealFft.inverse(re, im)
            val start = f * hop
            for (i in 0 until n) {
                overlap[start + i] += re[i] * window[i]
                weight[start + i] += window[i] * window[i]
            }
        }
        for (i in samples.indices) {
            val w = weight[i]
            samples[i] = if (w > 1e-5f) overlap[i] / w else samples[i]
        }
    }

    private fun expander(samples: FloatArray, amount: Float) {
        val threshold = 0.012f + (1f - amount) * 0.01f
        var env = 0f
        for (i in samples.indices) {
            val mag = abs(samples[i])
            env = if (mag > env) env + 0.15f * (mag - env) else env * 0.997f
            val gate = if (env < threshold) (env / threshold).pow(1.6f + amount) else 1f
            samples[i] *= gate
        }
    }

    private fun reduceBreaths(samples: FloatArray, sampleRate: Int, amount: Float) {
        val hp = Biquad.highPass(sampleRate, 2500.0)
        var env = 0f
        var hiss = 0f
        for (i in samples.indices) {
            val x = samples[i]
            val air = abs(hp.process(x))
            val mag = abs(x)
            env = if (mag > env) env + 0.08f * (mag - env) else env * 0.995f
            hiss = if (air > hiss) air else hiss * 0.98f
            if (env < 0.045f && hiss > env * (1.8f - amount * 0.4f)) {
                samples[i] = x * (1f - amount * 0.72f)
            }
        }
    }

    private fun deEss(samples: FloatArray, sampleRate: Int, amount: Float) {
        val band = Biquad.bandPass(sampleRate, 7000.0, q = 1.1)
        var ess = 0f
        var body = 0f
        for (i in samples.indices) {
            val x = samples[i]
            val s = abs(band.process(x))
            ess = if (s > ess) s else ess * 0.96f
            body = if (abs(x) > body) abs(x) else body * 0.99f
            if (ess > body * 0.42f && body > 0.02f) {
                val cut = 1f - amount * 0.55f * ((ess / (body + 1e-4f) - 0.42f).coerceIn(0f, 1.4f))
                samples[i] = x * cut.coerceIn(0.35f, 1f)
            }
        }
    }

    private fun applyEq(samples: FloatArray, sampleRate: Int, chain: VocalChain) {
        val tone = chain.tone.coerceIn(-1f, 1f)
        var low = chain.eqLowDb - tone * 2.2f
        var presence = chain.eqPresenceDb + tone * 1.4f
        var air = chain.eqAirDb + tone * 2.6f
        when (chain.preset) {
            VocalPreset.CLOSE -> {
                if (low == 0f) low = -1.5f
                if (presence == 0f) presence = 1.2f
            }
            VocalPreset.STAGE -> {
                if (air == 0f) air = 1.0f
            }
            VocalPreset.RADIO -> {
                if (low == 0f) low = -2.0f
                if (presence == 0f) presence = 2.4f
                if (air == 0f) air = 1.8f
            }
            else -> Unit
        }
        val filters = buildList {
            if (abs(low) > 0.05f) add(Biquad.lowShelf(sampleRate, 160.0, low))
            if (abs(presence) > 0.05f) add(Biquad.peak(sampleRate, 2800.0, presence, q = 0.9))
            if (abs(air) > 0.05f) add(Biquad.highShelf(sampleRate, 7500.0, air))
        }
        if (filters.isEmpty()) return
        for (i in samples.indices) {
            var x = samples[i]
            for (filter in filters) x = filter.process(x)
            samples[i] = x
        }
    }

    private fun compress(samples: FloatArray, sampleRate: Int, amount: Float, rider: Float) {
        val attack = exp(-1.0 / (0.006 * sampleRate)).toFloat()
        val release = exp(-1.0 / (0.080 * sampleRate)).toFloat()
        val threshold = 0.14f
        val ratio = 1.4f + amount * 2.8f
        var env = 0f
        var slow = 0.12f
        val ride = rider.coerceIn(0f, 1f)
        for (i in samples.indices) {
            val mag = abs(samples[i])
            env = if (mag > env) attack * env + (1f - attack) * mag else release * env + (1f - release) * mag
            slow = 0.9996f * slow + 0.0004f * mag
            var gain = 1f
            if (env > threshold && amount > 0.02f) {
                val over = env / threshold
                val compressed = threshold * over.pow(1f / ratio)
                gain = compressed / env
            }
            if (ride > 0.02f && slow > 1e-4f) {
                val target = 0.12f
                gain *= (1f + ride * (target / slow - 1f).coerceIn(-0.35f, 0.55f))
            }
            samples[i] *= gain
        }
    }

    private fun addDelay(wet: FloatArray, dry: FloatArray, sampleRate: Int, amount: Float) {
        val delay = (sampleRate * 0.168).toInt().coerceAtLeast(1)
        val mix = amount * 0.32f
        val fb = amount * 0.18f
        val echo = FloatArray(wet.size)
        for (i in dry.indices) {
            val delayed = if (i >= delay) echo[i - delay] else 0f
            echo[i] = dry[i] * 0.15f + delayed * fb
            wet[i] += delayed * mix
        }
    }

    private fun addReverb(wet: FloatArray, dry: FloatArray, sampleRate: Int, amount: Float) {
        val taps = intArrayOf(
            (sampleRate * 0.029).toInt(),
            (sampleRate * 0.053).toInt(),
            (sampleRate * 0.083).toInt(),
            (sampleRate * 0.127).toInt(),
            (sampleRate * 0.181).toInt(),
        )
        val weights = floatArrayOf(0.28f, 0.22f, 0.16f, 0.12f, 0.08f)
        val mix = amount * 0.38f
        val lp = Biquad.lowPass(sampleRate, 6200.0)
        for (i in dry.indices) {
            var room = 0f
            for (t in taps.indices) {
                val idx = i - taps[t]
                if (idx >= 0) room += dry[idx] * weights[t]
            }
            wet[i] += lp.process(room) * mix
        }
    }

    fun fadeEnds(samples: FloatArray, sampleRate: Int, fadeInMs: Int, fadeOutMs: Int) {
        val fadeIn = ((sampleRate * fadeInMs) / 1000).coerceIn(1, samples.size)
        val fadeOut = ((sampleRate * fadeOutMs) / 1000).coerceIn(1, samples.size)
        for (i in 0 until fadeIn) {
            samples[i] *= i / fadeIn.toFloat()
        }
        for (i in 0 until fadeOut) {
            val idx = samples.lastIndex - i
            if (idx >= 0) samples[idx] *= i / fadeOut.toFloat()
        }
    }

    fun thicken(samples: FloatArray, sampleRate: Int, amount: Float) {
        val delay = ((sampleRate * 0.016).toInt()).coerceAtLeast(1)
        val shifted = resample(samples, 2.0.pow(-10.0 / 1200.0).toFloat())
        val mix = amount * 0.22f
        for (i in samples.indices) {
            val src = i - delay
            if (src in shifted.indices) {
                samples[i] += shifted[src] * mix
            }
        }
    }

    fun correctPitchToKey(
        samples: FloatArray,
        sampleRate: Int,
        keyName: String?,
        mode: ScaleMode?,
        strength: Float,
    ) {
        val hop = 512
        val frame = 1024
        if (samples.size < frame * 2) return
        val scale = MusicalKeys.scaleForKey(keyName, mode)
        if (scale.isEmpty()) return
        val out = FloatArray(samples.size)
        val weight = FloatArray(samples.size)
        val window = FloatArray(frame) { i -> (0.5f * (1.0 - cos(2.0 * PI * i / (frame - 1))).toFloat()) }
        var pos = 0
        while (pos + frame <= samples.size) {
            val slice = FloatArray(frame) { i -> samples[pos + i] }
            val hz = VocalSignalAnalyzer.detectPitchHz(slice, frame, sampleRate)
            var ratio = 1f
            if (hz > 70f) {
                val target = nearestScaleHz(hz, scale)
                val cents = (1200.0 * kotlin.math.ln((hz / target).toDouble()) / kotlin.math.ln(2.0))
                    .coerceIn(-70.0, 70.0)
                val correctCents = -cents * strength.coerceIn(0f, 1f)
                ratio = 2.0.pow(correctCents / 1200.0).toFloat().coerceIn(0.96f, 1.04f)
            }
            val shifted = resample(slice, ratio)
            val n = min(frame, shifted.size)
            for (i in 0 until n) {
                val dest = pos + i
                if (dest < out.size) {
                    out[dest] += shifted[i] * window[i]
                    weight[dest] += window[i]
                }
            }
            pos += hop
        }
        for (i in samples.indices) {
            val w = weight[i]
            if (w > 1e-4f) samples[i] = out[i] / w
        }
    }

    fun finishMasterStereo(
        left: FloatArray,
        right: FloatArray,
        targetLufs: Float = -14f,
        truePeakDbtp: Float = -1f,
    ) {
        val n = min(left.size, right.size)
        if (n <= 0) return
        var sumSq = 0.0
        var peak = 0f
        for (i in 0 until n) {
            val l = abs(left[i])
            val r = abs(right[i])
            if (l > peak) peak = l
            if (r > peak) peak = r
            sumSq += left[i] * left[i] + right[i] * right[i]
        }
        val rms = sqrt(sumSq / (n * 2.0)).toFloat()
        if (rms < 1e-6f) return
        val targetRms = 10.0.pow(targetLufs / 20.0).toFloat().coerceIn(0.08f, 0.32f)
        val loudnessGain = (targetRms / rms).coerceIn(0.35f, 3.5f)
        val ceiling = 10.0.pow(truePeakDbtp / 20.0).toFloat().coerceIn(0.7f, 0.95f)
        for (i in 0 until n) {
            left[i] = (left[i] * loudnessGain).let { x ->
                if (abs(x) > ceiling) ceiling * if (x >= 0f) 1f else -1f else x
            }
            right[i] = (right[i] * loudnessGain).let { x ->
                if (abs(x) > ceiling) ceiling * if (x >= 0f) 1f else -1f else x
            }
        }
    }

    private fun nearestScaleHz(hz: Float, scale: List<ScaleTargetNote>): Float {
        val degrees = scale.map { it.hz }.filter { it > 1f }
        if (degrees.isEmpty()) return hz
        var best = degrees.first()
        var bestCents = 10_000.0
        var octave = 0.25f
        while (octave <= 8f) {
            for (degree in degrees) {
                val candidate = degree * octave
                val cents = abs(1200.0 * kotlin.math.ln((hz / candidate).toDouble()) / kotlin.math.ln(2.0))
                if (cents < bestCents) {
                    bestCents = cents
                    best = candidate
                }
            }
            octave *= 2f
        }
        return best
    }

    private fun resample(input: FloatArray, rate: Float): FloatArray {
        val safe = rate.coerceIn(0.5f, 2.0f)
        if (abs(safe - 1f) < 0.0008f) return input
        val outFrames = (input.size / safe).toInt().coerceAtLeast(1)
        val output = FloatArray(outFrames)
        for (i in 0 until outFrames) {
            val srcPos = i * safe
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = srcPos - idx0
            output[i] = input[idx0] * (1f - frac) + input[idx1] * frac
        }
        return output
    }
}

private class Biquad(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    companion object {
        fun highPass(sampleRate: Int, cutoffHz: Double, q: Double = 0.707): Biquad {
            val w = 2.0 * PI * cutoffHz / sampleRate
            val a = sin(w) / (2.0 * q)
            val cosw = cos(w)
            val b0 = ((1.0 + cosw) / 2.0)
            val b1 = -(1.0 + cosw)
            val b2 = ((1.0 + cosw) / 2.0)
            val a0 = 1.0 + a
            val a1 = -2.0 * cosw
            val a2 = 1.0 - a
            return Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }

        fun lowPass(sampleRate: Int, cutoffHz: Double, q: Double = 0.707): Biquad {
            val w = 2.0 * PI * cutoffHz / sampleRate
            val a = sin(w) / (2.0 * q)
            val cosw = cos(w)
            val b0 = (1.0 - cosw) / 2.0
            val b1 = 1.0 - cosw
            val b2 = (1.0 - cosw) / 2.0
            val a0 = 1.0 + a
            val a1 = -2.0 * cosw
            val a2 = 1.0 - a
            return Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }

        fun lowShelf(sampleRate: Int, cutoffHz: Double, gainDb: Float): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2.0 * PI * cutoffHz / sampleRate
            val cosw = cos(w)
            val sinw = sin(w)
            val sqrtA = sqrt(a)
            val s = sinw * sqrt(2.0) / 2.0 * sqrtA
            val b0 = a * ((a + 1) - (a - 1) * cosw + 2 * s)
            val b1 = 2 * a * ((a - 1) - (a + 1) * cosw)
            val b2 = a * ((a + 1) - (a - 1) * cosw - 2 * s)
            val a0 = (a + 1) + (a - 1) * cosw + 2 * s
            val a1 = -2 * ((a - 1) + (a + 1) * cosw)
            val a2 = (a + 1) + (a - 1) * cosw - 2 * s
            return Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }

        fun highShelf(sampleRate: Int, cutoffHz: Double, gainDb: Float): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2.0 * PI * cutoffHz / sampleRate
            val cosw = cos(w)
            val sinw = sin(w)
            val sqrtA = sqrt(a)
            val s = sinw * sqrt(2.0) / 2.0 * sqrtA
            val b0 = a * ((a + 1) + (a - 1) * cosw + 2 * s)
            val b1 = -2 * a * ((a - 1) + (a + 1) * cosw)
            val b2 = a * ((a + 1) + (a - 1) * cosw - 2 * s)
            val a0 = (a + 1) - (a - 1) * cosw + 2 * s
            val a1 = 2 * ((a - 1) - (a + 1) * cosw)
            val a2 = (a + 1) - (a - 1) * cosw - 2 * s
            return Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }

        fun peak(sampleRate: Int, cutoffHz: Double, gainDb: Float, q: Double): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2.0 * PI * cutoffHz / sampleRate
            val cosw = cos(w)
            val alpha = sin(w) / (2.0 * q)
            val b0 = 1 + alpha * a
            val b1 = -2 * cosw
            val b2 = 1 - alpha * a
            val a0 = 1 + alpha / a
            val a1 = -2 * cosw
            val a2 = 1 - alpha / a
            return Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }

        fun bandPass(sampleRate: Int, cutoffHz: Double, q: Double): Biquad {
            val w = 2.0 * PI * cutoffHz / sampleRate
            val alpha = sin(w) / (2.0 * q)
            val cosw = cos(w)
            val b0 = alpha
            val b1 = 0.0
            val b2 = -alpha
            val a0 = 1 + alpha
            val a1 = -2 * cosw
            val a2 = 1 - alpha
            return Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
        }
    }
}
