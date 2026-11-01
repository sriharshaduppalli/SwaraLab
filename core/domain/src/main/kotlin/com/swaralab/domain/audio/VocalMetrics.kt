package com.swaralab.domain.audio

import com.swaralab.domain.model.ScaleMode
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ScaleTargetNote(
    val swara: String,
    val westernNote: String,
    val hz: Float,
)

object MusicalKeys {
    val ALL: List<String> = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val majorSwaras = arrayOf("Sa", "Re", "Ga", "Ma", "Pa", "Dha", "Ni", "Sa'")

    fun semitoneOf(keyName: String?): Int {
        if (keyName.isNullOrBlank()) return 0
        val idx = ALL.indexOfFirst { it.equals(keyName.trim(), ignoreCase = true) }
        return if (idx >= 0) idx else 0
    }

    fun tonicC3Hz(keyName: String?): Double {
        val semitone = semitoneOf(keyName)
        return 130.81278265 * 2.0.pow(semitone / 12.0)
    }

    fun scaleForKey(keyName: String?, mode: ScaleMode? = null): List<ScaleTargetNote> {
        val rootSemi = semitoneOf(keyName)
        val steps = stepsFor(mode)
        val swaras = swarasFor(mode)
        val baseMidi = 60 + rootSemi
        return steps.mapIndexed { idx, offset ->
            val midi = baseMidi + offset
            val noteIdx = ((midi % 12) + 12) % 12
            val octave = (midi / 12) - 1
            val hz = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
            ScaleTargetNote(
                swara = swaras.getOrElse(idx) { majorSwaras[idx.coerceAtMost(majorSwaras.lastIndex)] },
                westernNote = "${ALL[noteIdx]}$octave",
                hz = hz,
            )
        }
    }

    private fun stepsFor(mode: ScaleMode?): IntArray = when (mode) {
        ScaleMode.MINOR -> intArrayOf(0, 2, 3, 5, 7, 8, 10, 12)
        ScaleMode.MOHANAM -> intArrayOf(0, 2, 4, 7, 9, 12)
        ScaleMode.BHAIRAV -> intArrayOf(0, 1, 4, 5, 7, 8, 11, 12)
        ScaleMode.YAMAN -> intArrayOf(0, 2, 4, 6, 7, 9, 11, 12)
        else -> intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
    }

    private fun swarasFor(mode: ScaleMode?): Array<String> = when (mode) {
        ScaleMode.MOHANAM -> arrayOf("Sa", "Re", "Ga", "Pa", "Dha", "Sa'")
        ScaleMode.BHAIRAV -> arrayOf("Sa", "Re♭", "Ga", "Ma", "Pa", "Dha♭", "Ni", "Sa'")
        ScaleMode.MINOR -> arrayOf("Sa", "Re", "Ga♭", "Ma", "Pa", "Dha♭", "Ni♭", "Sa'")
        ScaleMode.YAMAN -> arrayOf("Sa", "Re", "Ga", "Ma♯", "Pa", "Dha", "Ni", "Sa'")
        else -> majorSwaras
    }
}

data class VocalMetrics(
    val pitchHz: Float = 0f,
    val noteName: String = "—",
    val swaraName: String = "—",
    val centsOffset: Int = 0,
    val rmsDb: Float = -60f,
    val bassLevel: Float = 0f,
    val midLevel: Float = 0f,
    val trebleLevel: Float = 0f,
    val silentFrames: Int = 0,
    val candidateNote: String = "—",
    val candidateCount: Int = 0,
    val waveformHistory: List<Float> = emptyList(),
    val pitchHistory: List<Float> = emptyList(),
)

object VocalSignalAnalyzer {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val swaraNames = arrayOf("Sa", "Re♭", "Re", "Ga♭", "Ga", "Ma", "Ma♯", "Pa", "Dha♭", "Dha", "Ni♭", "Ni")
    private const val HISTORY_SIZE = 42
    private const val MIN_VOCAL_HZ = 75f
    private const val MAX_VOCAL_HZ = 1000f
    private const val NOTE_HOLD_FRAMES = 8

    fun analyzeFrame(
        samples: ShortArray,
        length: Int,
        sampleRate: Int,
        previous: VocalMetrics,
        rootSemitone: Int = 0,
    ): VocalMetrics {
        if (length <= 0 || sampleRate <= 0) return previous

        var sumSquares = 0.0
        var peak = 0
        val alphaLow = (2.0 * Math.PI * 260.0 / (2.0 * Math.PI * 260.0 + sampleRate)).toFloat()
        val alphaMid = (2.0 * Math.PI * 2400.0 / (2.0 * Math.PI * 2400.0 + sampleRate)).toFloat()
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
        val rawRmsDb = if (rms > 1e-5f) (20.0 * ln(rms.toDouble()) / ln(10.0)).toFloat().coerceIn(-60f, 0f) else -60f
        val smoothedRmsDb = if (previous.rmsDb <= -59f) rawRmsDb else (previous.rmsDb * 0.75f + rawRmsDb * 0.25f)
        val rawVisualAmp = sqrt((peak / 32768f).coerceIn(0f, 1f).toDouble()).toFloat().coerceIn(0f, 1f)
        val prevAmp = previous.waveformHistory.lastOrNull() ?: rawVisualAmp
        val visualAmp = (prevAmp * 0.45f + rawVisualAmp * 0.55f).coerceIn(0f, 1f)

        val bassRms = sqrt(bassEnergy / length).toFloat()
        val midRms = sqrt(midEnergy / length).toFloat()
        val trebleRms = sqrt(trebleEnergy / length).toFloat()

        val rawBass = sqrt((bassRms * 12f).coerceIn(0f, 1f).toDouble()).toFloat()
        val rawMid = sqrt((midRms * 16f).coerceIn(0f, 1f).toDouble()).toFloat()
        val rawTreble = sqrt((trebleRms * 28f).coerceIn(0f, 1f).toDouble()).toFloat()

        val hasPreviousHistory = previous.waveformHistory.isNotEmpty()
        val bassLevel = if (hasPreviousHistory) (previous.bassLevel * 0.78f + rawBass * 0.22f) else rawBass
        val midLevel = if (hasPreviousHistory) (previous.midLevel * 0.78f + rawMid * 0.22f) else rawMid
        val trebleLevel = if (hasPreviousHistory) (previous.trebleLevel * 0.78f + rawTreble * 0.22f) else rawTreble

        val rawDetectedHz = if (rms >= 0.0025f) {
            detectPitchHz(normalized, length, sampleRate)
        } else {
            0f
        }

        val isValidPitch = rawDetectedHz in MIN_VOCAL_HZ..MAX_VOCAL_HZ
        val effectiveHz: Float
        val note: String
        val swara: String
        val cents: Int
        val nextSilentFrames: Int
        val nextCandidateNote: String
        val nextCandidateCount: Int

        if (isValidPitch) {
            effectiveHz = if (previous.pitchHz > 0f && abs(rawDetectedHz - previous.pitchHz) < 60f) {
                previous.pitchHz * 0.65f + rawDetectedHz * 0.35f
            } else {
                rawDetectedHz
            }
            val mapped = pitchToNoteAndSwara(effectiveHz, rootSemitone)
            val rawNote = mapped.first
            val rawSwara = mapped.second
            val rawCents = mapped.third

            if (previous.noteName == "—" || rawNote == previous.noteName) {
                note = rawNote
                swara = rawSwara
                cents = if (previous.noteName == rawNote) {
                    ((previous.centsOffset * 2 + rawCents) / 3)
                } else {
                    rawCents
                }
                nextCandidateNote = rawNote
                nextCandidateCount = 0
            } else if (rawNote == previous.candidateNote && previous.candidateCount >= 1) {
                note = rawNote
                swara = rawSwara
                cents = rawCents
                nextCandidateNote = rawNote
                nextCandidateCount = 0
            } else {
                note = previous.noteName
                swara = previous.swaraName
                cents = previous.centsOffset
                nextCandidateNote = rawNote
                nextCandidateCount = if (rawNote == previous.candidateNote) previous.candidateCount + 1 else 1
            }
            nextSilentFrames = 0
        } else if (previous.pitchHz > 0f && previous.silentFrames < NOTE_HOLD_FRAMES) {
            effectiveHz = previous.pitchHz
            note = previous.noteName
            swara = previous.swaraName
            cents = previous.centsOffset
            nextSilentFrames = previous.silentFrames + 1
            nextCandidateNote = previous.candidateNote
            nextCandidateCount = 0
        } else {
            effectiveHz = 0f
            note = "—"
            swara = "—"
            cents = 0
            nextSilentFrames = previous.silentFrames + 1
            nextCandidateNote = "—"
            nextCandidateCount = 0
        }

        val normalizedPitch = if (effectiveHz in MIN_VOCAL_HZ..MAX_VOCAL_HZ) {
            val minLog = ln(80.0)
            val maxLog = ln(880.0)
            val rawNorm = ((ln(effectiveHz.toDouble()) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0.05f, 0.98f)
            val prevNorm = previous.pitchHistory.lastOrNull()?.takeIf { it > 0.01f }
            if (prevNorm != null) (prevNorm * 0.5f + rawNorm * 0.5f) else rawNorm
        } else {
            0f
        }

        val nextWave = (previous.waveformHistory + visualAmp).takeLast(HISTORY_SIZE)
        val nextPitch = (previous.pitchHistory + normalizedPitch).takeLast(HISTORY_SIZE)

        return VocalMetrics(
            pitchHz = effectiveHz,
            noteName = note,
            swaraName = swara,
            centsOffset = cents,
            rmsDb = smoothedRmsDb,
            bassLevel = bassLevel,
            midLevel = midLevel,
            trebleLevel = trebleLevel,
            silentFrames = nextSilentFrames,
            candidateNote = nextCandidateNote,
            candidateCount = nextCandidateCount,
            waveformHistory = nextWave,
            pitchHistory = nextPitch,
        )
    }

    private fun detectPitchHz(samples: FloatArray, length: Int, sampleRate: Int): Float {
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
        if (energy0 <= 1e-8f) return 0f

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
                if (normCorr < 0.25f) passedFirstDip = true
            } else if (normCorr > bestCorr) {
                bestCorr = normCorr
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestCorr < 0.35f) return 0f
        return effRate.toFloat() / bestLag.toFloat()
    }

    fun pitchToNoteAndSwara(hz: Float, rootSemitone: Int = 0): Triple<String, String, Int> {
        val midiExact = 69.0 + 12.0 * (ln(hz / 440.0) / ln(2.0))
        val midiNearest = midiExact.roundToInt()
        val cents = ((midiExact - midiNearest) * 100.0).roundToInt().coerceIn(-50, 50)
        val noteIndex = ((midiNearest % 12) + 12) % 12
        val octave = (midiNearest / 12) - 1
        val noteName = "${noteNames[noteIndex]}$octave"
        val relativeSemi = ((noteIndex - (rootSemitone % 12)) + 12) % 12
        val swaraName = swaraNames[relativeSemi]
        return Triple(noteName, swaraName, cents)
    }
}
