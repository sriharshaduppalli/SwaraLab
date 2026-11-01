package com.swaralab.domain.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class VocalMetrics(
    val pitchHz: Float = 0f,
    val noteName: String = "—",
    val swaraName: String = "—",
    val centsOffset: Int = 0,
    val rmsDb: Float = -60f,
    val bassLevel: Float = 0f,
    val midLevel: Float = 0f,
    val trebleLevel: Float = 0f,
    val waveformHistory: List<Float> = emptyList(),
    val pitchHistory: List<Float> = emptyList(),
)

object VocalSignalAnalyzer {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val swaraNames = arrayOf("Sa", "Re♭", "Re", "Ga♭", "Ga", "Ma", "Ma♯", "Pa", "Dha♭", "Dha", "Ni♭", "Ni")
    private const val HISTORY_SIZE = 42
    private const val MIN_VOCAL_HZ = 75f
    private const val MAX_VOCAL_HZ = 1000f

    fun analyzeFrame(
        samples: ShortArray,
        length: Int,
        sampleRate: Int,
        previous: VocalMetrics,
    ): VocalMetrics {
        if (length <= 0 || sampleRate <= 0) return previous

        var sumSquares = 0.0
        var peak = 0
        // Simple 1-pole filters at ~250 Hz and ~2500 Hz to separate Bass, Vocal Mid, and Treble/Air
        val alphaLow = (2.0 * Math.PI * 250.0 / (2.0 * Math.PI * 250.0 + sampleRate)).toFloat()
        val alphaMid = (2.0 * Math.PI * 2500.0 / (2.0 * Math.PI * 2500.0 + sampleRate)).toFloat()
        var lowState = 0f
        var midLPState = 0f
        var bassEnergy = 0.0
        var midEnergy = 0.0
        var trebleEnergy = 0.0

        val normalized = FloatArray(length)
        for (i in 0 until length) {
            val s = samples[i].toInt()
            val mag = abs(s)
            if (mag > peak) peak = mag
            val x = s / 32768f
            normalized[i] = x
            sumSquares += x * x

            lowState += alphaLow * (x - lowState)
            midLPState += alphaMid * (x - midLPState)
            val bass = lowState
            val mid = midLPState - lowState
            val treble = x - midLPState

            bassEnergy += bass * bass
            midEnergy += mid * mid
            trebleEnergy += treble * treble
        }

        val rms = sqrt(sumSquares / length).toFloat()
        val rmsDb = if (rms > 1e-5f) (20.0 * ln(rms.toDouble()) / ln(10.0)).toFloat().coerceIn(-60f, 0f) else -60f
        val normPeak = (peak / 32768f).coerceIn(0f, 1f)

        val bassRms = sqrt(bassEnergy / length).toFloat()
        val midRms = sqrt(midEnergy / length).toFloat()
        val trebleRms = sqrt(trebleEnergy / length).toFloat()

        // Scale into readable 0..1 visual meter levels
        val bassLevel = (bassRms * 5.5f).coerceIn(0f, 1f)
        val midLevel = (midRms * 7.0f).coerceIn(0f, 1f)
        val trebleLevel = (trebleRms * 12.0f).coerceIn(0f, 1f)

        val detectedHz = if (rms >= 0.012f) {
            detectPitchHz(normalized, length, sampleRate)
        } else {
            0f
        }

        val (note, swara, cents) = if (detectedHz in MIN_VOCAL_HZ..MAX_VOCAL_HZ) {
            pitchToNoteAndSwara(detectedHz)
        } else {
            Triple("—", "—", 0)
        }

        // Normalized pitch position 0..1 between 80 Hz (E2) and 880 Hz (A5) in log-frequency space
        val normalizedPitch = if (detectedHz in MIN_VOCAL_HZ..MAX_VOCAL_HZ) {
            val minLog = ln(80.0)
            val maxLog = ln(880.0)
            ((ln(detectedHz.toDouble()) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0.05f, 0.98f)
        } else {
            0f
        }

        val nextWave = (previous.waveformHistory + normPeak).takeLast(HISTORY_SIZE)
        val nextPitch = (previous.pitchHistory + normalizedPitch).takeLast(HISTORY_SIZE)

        return VocalMetrics(
            pitchHz = detectedHz,
            noteName = note,
            swaraName = swara,
            centsOffset = cents,
            rmsDb = rmsDb,
            bassLevel = bassLevel,
            midLevel = midLevel,
            trebleLevel = trebleLevel,
            waveformHistory = nextWave,
            pitchHistory = nextPitch,
        )
    }

    private fun detectPitchHz(samples: FloatArray, length: Int, sampleRate: Int): Float {
        // Downsample by 2 for fast autocorrelation across 75..1000 Hz at 48 kHz
        val decimation = if (sampleRate >= 32_000) 2 else 1
        val effRate = sampleRate / decimation
        val n = length / decimation
        if (n < 64) return 0f
        val x = FloatArray(n)
        var mean = 0f
        for (i in 0 until n) {
            val v = samples[i * decimation]
            x[i] = v
            mean += v
        }
        mean /= n
        var energy0 = 0f
        for (i in 0 until n) {
            x[i] -= mean
            energy0 += x[i] * x[i]
        }
        if (energy0 <= 1e-6f) return 0f

        val minLag = (effRate / MAX_VOCAL_HZ).toInt().coerceAtLeast(4)
        val maxLag = (effRate / MIN_VOCAL_HZ).toInt().coerceAtMost(n - 2)
        if (minLag >= maxLag) return 0f

        var bestLag = -1
        var bestCorr = 0f
        var passedFirstDip = false

        for (lag in minLag..maxLag) {
            var sum = 0f
            val limit = n - lag
            for (i in 0 until limit) {
                sum += x[i] * x[i + lag]
            }
            val normCorr = sum / energy0
            if (!passedFirstDip) {
                if (normCorr < 0.2f) passedFirstDip = true
            } else if (normCorr > bestCorr) {
                bestCorr = normCorr
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestCorr < 0.32f) return 0f
        return effRate.toFloat() / bestLag.toFloat()
    }

    internal fun pitchToNoteAndSwara(hz: Float): Triple<String, String, Int> {
        val midiExact = 69.0 + 12.0 * (ln(hz / 440.0) / ln(2.0))
        val midiNearest = midiExact.roundToInt()
        val cents = ((midiExact - midiNearest) * 100.0).roundToInt().coerceIn(-50, 50)
        val noteIndex = ((midiNearest % 12) + 12) % 12
        val octave = (midiNearest / 12) - 1
        val noteName = "${noteNames[noteIndex]}$octave"
        val swaraName = swaraNames[noteIndex]
        return Triple(noteName, swaraName, cents)
    }
}
