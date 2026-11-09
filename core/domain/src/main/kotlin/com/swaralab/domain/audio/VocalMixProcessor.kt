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
        val hp = if (chain.preset == VocalPreset.RADIO) 82.0 else 68.0
        highPass(samples, sampleRate, cutoffHz = hp)
        val denoise = if (chain.preset == VocalPreset.RAW) {
            chain.denoise.coerceIn(0f, 1f).coerceAtLeast(0.4f)
        } else {
            chain.denoise.coerceIn(0f, 1f).coerceAtLeast(0.62f)
        }
        spectralDenoise(samples, sampleRate, denoise)
        val gateAmt = if (chain.preset == VocalPreset.STAGE) 0.30f else 0.48f + denoise * 0.28f
        gateGaps(samples, gateAmt)
        tameKnocks(samples, sampleRate)
        val breath = chain.breathReduction.coerceIn(0f, 1f).let { if (it <= 0f) 0.42f else it }
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
        suppressResidualHiss(samples, sampleRate, denoise)
        gateGaps(samples, (gateAmt * 0.72f).coerceIn(0.28f, 0.82f))
        tameKnocks(samples, sampleRate)
        if (abs(chain.outputGainDb) > 0.05f) {
            val gain = 10.0.pow(chain.outputGainDb / 20.0).toFloat()
            for (i in samples.indices) samples[i] *= gain
        }
        val durationMs = (samples.size * 1000L / sampleRate).toInt()
        val fadeOutMs = (400 + chain.fadeOut * 1_600f).toInt().coerceAtMost((durationMs / 5).coerceAtLeast(40))
        fadeEnds(samples, sampleRate, fadeInMs = 45, fadeOutMs = fadeOutMs)
    }

    fun spreadStereo(
        mono: FloatArray,
        sampleRate: Int,
        chain: VocalChain,
    ): Pair<FloatArray, FloatArray> {
        val width = when (chain.preset) {
            VocalPreset.RAW -> 0.14f
            VocalPreset.CLOSE -> 0.40f
            VocalPreset.STAGE -> 0.62f
            VocalPreset.RADIO -> 0.44f
            VocalPreset.BRIGHT -> 0.48f
            VocalPreset.CUSTOM -> 0.38f
        } * chain.effectAmount.coerceIn(0.35f, 1f)
        val haas = ((sampleRate * 0.012).toInt()).coerceAtLeast(1)
        val left = FloatArray(mono.size)
        val right = FloatArray(mono.size)
        for (i in mono.indices) {
            val dry = mono[i]
            val delayed = if (i >= haas) mono[i - haas] else 0f
            left[i] = dry
            right[i] = dry * (1f - width * 0.28f) + delayed * (width * 0.62f)
        }
        val room = when (chain.preset) {
            VocalPreset.STAGE -> 0.42f
            VocalPreset.CLOSE -> 0.18f
            VocalPreset.RADIO -> 0.16f
            else -> 0.08f
        } * chain.effectAmount.coerceIn(0.35f, 1f)
        addStereoRoom(left, right, mono, sampleRate, room)
        return left to right
    }

    private fun addStereoRoom(
        left: FloatArray,
        right: FloatArray,
        dry: FloatArray,
        sampleRate: Int,
        amount: Float,
    ) {
        if (amount < 0.02f || dry.isEmpty()) return
        val leftTaps = intArrayOf(
            (sampleRate * 0.031).toInt(),
            (sampleRate * 0.071).toInt(),
            (sampleRate * 0.127).toInt(),
        )
        val rightTaps = intArrayOf(
            (sampleRate * 0.037).toInt(),
            (sampleRate * 0.089).toInt(),
            (sampleRate * 0.149).toInt(),
        )
        val weights = floatArrayOf(0.26f, 0.18f, 0.11f)
        val mix = amount * 0.36f
        val lpL = Biquad.lowPass(sampleRate, 6800.0)
        val lpR = Biquad.lowPass(sampleRate, 6400.0)
        for (i in dry.indices) {
            var roomL = 0f
            var roomR = 0f
            for (t in leftTaps.indices) {
                val li = i - leftTaps[t]
                val ri = i - rightTaps[t]
                if (li >= 0) roomL += dry[li] * weights[t]
                if (ri >= 0) roomR += dry[ri] * weights[t]
            }
            left[i] += lpL.process(roomL) * mix
            right[i] += lpR.process(roomR) * mix
        }
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
        val targetRms = 10.0.pow(targetLufs / 20.0).toFloat().coerceIn(0.06f, 0.28f)
        val ceiling = 10.0.pow(truePeakDbtp / 20.0).toFloat().coerceIn(0.68f, 0.97f)
        val headroom = (ceiling / peak.coerceAtLeast(1e-6f)).coerceAtLeast(0.22f)
        val loudnessGain = (targetRms / rms).coerceIn(0.35f, min(1.28f, headroom))
        val peakAfter = (peak * loudnessGain).coerceAtLeast(1e-6f)
        val scale = if (peakAfter > ceiling) ceiling / peakAfter else loudnessGain
        for (i in samples.indices) {
            samples[i] = (samples[i] * scale).coerceIn(-ceiling, ceiling)
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
        val noiseCut = ranked[(ranked.size * 0.08f).toInt().coerceIn(0, ranked.lastIndex)]
        val quietLimit = max(noiseCut * 1.05f, 1e-6f).coerceAtMost(0.032f)
        val noise = FloatArray(n / 2 + 1)
        var noiseFrames = 0
        for (f in 0 until frameCount) {
            if (rms[f] <= quietLimit) {
                noiseFrames += 1
                for (k in noise.indices) noise[k] += mag[f][k]
            }
        }
        if (noiseFrames == 0) {
            for (k in noise.indices) {
                val hz = k * sampleRate / n.toFloat()
                noise[k] = if (hz > 4_000f) 0.0016f else 0.0007f
            }
            noiseFrames = 1
        }
        for (k in noise.indices) noise[k] /= noiseFrames.toFloat()

        val alpha = 1.85f + amount * 3.1f
        val floor = (0.010f - amount * 0.006f).coerceIn(0.0025f, 0.010f)
        val rumbleBins = ((90.0 * n) / sampleRate).toInt().coerceAtLeast(1)
        val hissBin = ((4_200.0 * n) / sampleRate).toInt()
        val airBin = ((7_500.0 * n) / sampleRate).toInt()
        for (f in 0 until frameCount) {
            val speech = rms[f] / (quietLimit + 1e-8f)
            val frameGate = if (speech < 1.65f) {
                (speech / 1.65f).pow(2.2f + amount * 0.6f).coerceIn(0.04f, 1f)
            } else {
                1f
            }
            for (k in mag[f].indices) {
                val nMag = noise[k]
                val sMag = mag[f][k]
                val binAlpha = when {
                    k >= airBin -> alpha * 1.45f
                    k >= hissBin -> alpha * 1.22f
                    else -> alpha
                }
                var gain = if (sMag > 1e-8f) 1f - binAlpha * nMag / sMag else 0f
                gain = gain.coerceIn(floor, 1f)
                if (k <= rumbleBins) gain *= 0.34f
                if (k >= hissBin) gain *= 1f - amount * 0.22f
                if (k >= airBin) gain *= 1f - amount * 0.18f
                mag[f][k] = sMag * (gain * frameGate).coerceIn(0.002f, 1f)
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
        gateGaps(samples, amount)
    }

    private fun tameKnocks(samples: FloatArray, sampleRate: Int) {
        if (samples.size < 64) return
        val hole = (sampleRate * 0.048).toInt().coerceAtLeast(8)
        val ctx = (sampleRate * 0.12).toInt().coerceAtLeast(hole * 2)
        val energy = DoubleArray(samples.size + 1)
        for (i in samples.indices) {
            energy[i + 1] = energy[i] + samples[i] * samples[i]
        }
        for (i in samples.indices) {
            val a = abs(samples[i])
            if (a < 0.10f) continue
            val left0 = (i - ctx).coerceAtLeast(0)
            val left1 = (i - hole / 2).coerceAtLeast(0)
            val right0 = (i + hole / 2).coerceAtMost(samples.size)
            val right1 = (i + ctx).coerceAtMost(samples.size)
            val count = (left1 - left0) + (right1 - right0)
            if (count < 32) continue
            val local = sqrt(
                ((energy[left1] - energy[left0]) + (energy[right1] - energy[right0])) / count,
            ).toFloat()
            if (local < 0.12f) {
                samples[i] *= (0.06f / a).coerceIn(0.03f, 0.40f)
            }
        }
    }

    private fun gateGaps(samples: FloatArray, amount: Float) {
        val threshold = (0.0075f + (1f - amount.coerceIn(0f, 1f)) * 0.006f)
        var env = 0f
        for (i in samples.indices) {
            val mag = abs(samples[i])
            val attack = if (env < threshold * 1.5f) 0.048f else 0.22f
            env = if (mag > env) env + attack * (mag - env) else env * 0.9958f
            val gate = if (env < threshold) {
                (env / threshold).pow(2.4f + amount * 0.9f).coerceIn(0.008f, 1f)
            } else {
                1f
            }
            samples[i] *= gate
        }
    }

    /** After compression/reverb, kill leftover air hiss that makeup gain would otherwise lift. */
    private fun suppressResidualHiss(samples: FloatArray, sampleRate: Int, amount: Float) {
        if (amount < 0.08f || samples.isEmpty()) return
        val airEq = Biquad.highPass(sampleRate, 5_800.0)
        val bodyEq = Biquad.lowPass(sampleRate, 2_400.0)
        var air = 0f
        var body = 0f
        val cut = amount.coerceIn(0f, 1f)
        for (i in samples.indices) {
            val x = samples[i]
            val high = abs(airEq.process(x))
            val low = abs(bodyEq.process(x))
            air = if (high > air) air + 0.18f * (high - air) else air * 0.975f
            body = if (low > body) body + 0.10f * (low - body) else body * 0.992f
            val hissy = air > body * (0.72f - cut * 0.10f) + 0.006f
            val quiet = body < 0.028f
            val reduce = when {
                hissy && quiet -> 1f - cut * 0.88f
                hissy -> 1f - cut * 0.38f
                quiet -> 1f - cut * 0.18f
                else -> 1f
            }
            samples[i] = x * reduce.coerceIn(0.06f, 1f)
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
        var air = chain.eqAirDb + tone * 1.1f
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
            VocalPreset.BRIGHT -> {
                if (low == 0f) low = -2.2f
                if (presence == 0f) presence = 2.6f
                if (air == 0f) air = 3.0f
            }
            else -> Unit
        }
        val filters = buildList {
            if (abs(low) > 0.05f) add(Biquad.lowShelf(sampleRate, 160.0, low))
            if (abs(presence) > 0.05f) add(Biquad.peak(sampleRate, 2800.0, presence, q = 0.9))
            if (abs(air) > 0.05f) add(Biquad.highShelf(sampleRate, 7500.0, air))
            if (chain.denoise > 0.35f) {
                add(Biquad.highShelf(sampleRate, 9_500.0, -1.1f - chain.denoise * 0.9f))
            }
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
            if (ride > 0.02f && env > 0.05f && slow > 0.04f) {
                val target = 0.12f
                gain *= (1f + ride * (target / slow - 1f).coerceIn(-0.28f, 0.32f))
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
        val mix = amount * 0.30f
        val lp = Biquad.lowPass(sampleRate, 4_200.0)
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
        var voicedSq = 0.0
        var voiced = 0
        var peak = 0f
        for (i in 0 until n) {
            val l = abs(left[i])
            val r = abs(right[i])
            if (l > peak) peak = l
            if (r > peak) peak = r
            val energy = left[i] * left[i] + right[i] * right[i]
            sumSq += energy
            if ((l + r) * 0.5f > 0.028f) {
                voicedSq += energy
                voiced += 1
            }
        }
        val rms = if (voiced > n / 20) {
            sqrt(voicedSq / (voiced * 2.0)).toFloat()
        } else {
            sqrt(sumSq / (n * 2.0)).toFloat()
        }
        if (rms < 1e-6f) return
        val targetRms = 10.0.pow(targetLufs / 20.0).toFloat().coerceIn(0.06f, 0.28f)
        val ceiling = 10.0.pow(truePeakDbtp / 20.0).toFloat().coerceIn(0.68f, 0.97f)
        val headroom = (ceiling / peak.coerceAtLeast(1e-6f)).coerceAtLeast(0.22f)
        val loudnessGain = (targetRms / rms).coerceIn(0.35f, min(1.28f, headroom))
        val peakAfter = (peak * loudnessGain).coerceAtLeast(1e-6f)
        val scale = if (peakAfter > ceiling) ceiling / peakAfter else loudnessGain
        for (i in 0 until n) {
            left[i] = (left[i] * scale).coerceIn(-ceiling, ceiling)
            right[i] = (right[i] * scale).coerceIn(-ceiling, ceiling)
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
