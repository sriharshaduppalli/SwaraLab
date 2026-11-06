package com.swaralab.domain.audio

import com.swaralab.domain.model.LyricLine
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PitchSample(
    val timestampMs: Long,
    val frequencyHz: Float,
    val confidence: Float,
    val midiNote: Int? = null,
    val centsOffset: Float? = null,
)

data class SectionScore(
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val pitch: Float,
    val timing: Float,
)

data class DetailedScore(
    val overall: Float,
    val pitchAccuracy: Float,
    val timing: Float,
    val stability: Float,
    val sections: List<SectionScore> = emptyList(),
)

/**
 * On-device take score. Pitch accuracy 40, stability 30, lyric timing 30.
 */
object PitchScorer {
    private val detector = AutocorrelationPitchDetector()

    fun score(
        dryVocal: File,
        lyrics: List<LyricLine> = emptyList(),
        punchStartMs: Long = 0L,
    ): Float = detailed(dryVocal, lyrics, punchStartMs).overall

    fun detailed(
        dryVocal: File,
        lyrics: List<LyricLine> = emptyList(),
        punchStartMs: Long = 0L,
        hopMs: Int = 30,
    ): DetailedScore {
        val pcm = WavPcm.readMono(dryVocal) ?: return DetailedScore(0f, 0f, 0f, 0f)
        val contour = contourSamples(pcm, hopMs)
        val pitch = pitchAccuracy(contour)
        val stability = stabilityFromSamples(contour)
        val timing = timingFromSamples(contour, lyrics, punchStartMs)
        return DetailedScore(
            overall = (pitch * 0.4f + stability * 0.3f + timing * 0.3f).coerceIn(0f, 100f),
            pitchAccuracy = pitch,
            timing = timing,
            stability = stability,
            sections = sectionScores(contour, lyrics, punchStartMs, hopMs),
        )
    }

    fun contourSamples(pcm: MonoPcm, hopMs: Int = 30): List<PitchSample> {
        val hop = ((pcm.sampleRate * hopMs) / 1000).coerceAtLeast(1)
        val window = (pcm.sampleRate * 40 / 1000).coerceAtLeast(hop)
        val out = ArrayList<PitchSample>(pcm.samples.size / hop + 1)
        var i = 0
        while (i + window <= pcm.samples.size) {
            val slice = pcm.samples.copyOfRange(i, i + window)
            val ts = (i * 1000L) / pcm.sampleRate
            val sample = detector.process(slice, pcm.sampleRate, ts)
            out += sample ?: PitchSample(timestampMs = ts, frequencyHz = 0f, confidence = 0f)
            i += hop
        }
        return out
    }

    fun contour(pcm: MonoPcm, hopMs: Int = 30): List<Float> =
        contourSamples(pcm, hopMs).map { it.frequencyHz }

    fun pitchAccuracy(samples: List<PitchSample>): Float {
        val voiced = samples.filter { it.frequencyHz > 70f && it.confidence > 0.6f }
        if (voiced.isEmpty()) return 40f
        val inTune = voiced.count { abs(it.centsOffset ?: 100f) < 40f }
        return (inTune.toFloat() / voiced.size * 100f).coerceIn(0f, 100f)
    }

    fun stabilityFromSamples(samples: List<PitchSample>): Float {
        val voiced = samples.map { it.frequencyHz }.filter { it > 70f }
        return stabilityScore(voiced) / 40f * 100f
    }

    fun timingFromSamples(
        samples: List<PitchSample>,
        lyrics: List<LyricLine>,
        punchStartMs: Long,
    ): Float {
        val timed = lyrics.filter { it.startMs != null }
        if (timed.isEmpty()) {
            if (samples.isEmpty()) return 40f
            val voiced = samples.count { it.frequencyHz > 70f }
            return (voiced.toFloat() / samples.size * 100f).coerceIn(0f, 100f)
        }
        var hits = 0
        var attempts = 0
        timed.forEach { line ->
            val start = ((line.startMs ?: 0L) - punchStartMs).coerceAtLeast(0L)
            attempts += 1
            val hit = samples.any { sample ->
                val dt = abs(sample.timestampMs - start)
                dt <= 300L && sample.frequencyHz > 70f
            }
            if (hit) hits += 1
        }
        if (attempts == 0) return 40f
        return (hits.toFloat() / attempts * 100f).coerceIn(0f, 100f)
    }

    fun sectionScores(
        samples: List<PitchSample>,
        lyrics: List<LyricLine> = emptyList(),
        punchStartMs: Long = 0L,
        hopMs: Int = 30,
    ): List<SectionScore> {
        if (samples.isEmpty()) return emptyList()
        val windowMs = 20_000L
        val out = ArrayList<SectionScore>()
        var startMs = 0L
        val endAll = samples.last().timestampMs.coerceAtLeast(windowMs)
        var part = 1
        while (startMs < endAll) {
            val endMs = (startMs + windowMs).coerceAtMost(endAll + hopMs)
            val slice = samples.filter { it.timestampMs in startMs until endMs }
            val lyric = lyrics.firstOrNull { line ->
                val ls = (line.startMs ?: 0L) - punchStartMs
                ls in startMs until endMs
            }
            val name = lyric?.text?.take(28)?.ifBlank { null } ?: "Part $part"
            out += SectionScore(
                name = name,
                startMs = startMs,
                endMs = endMs,
                pitch = pitchAccuracy(slice),
                timing = timingFromSamples(slice, lyrics, punchStartMs),
            )
            startMs = endMs
            part += 1
        }
        return out
    }

    fun stabilityScore(pitches: List<Float>): Float {
        val voiced = pitches.filter { it > 50f }
        if (voiced.size < 8) return 8f
        val mean = voiced.average().toFloat()
        val variance = voiced.fold(0f) { acc, hz ->
            val d = hz - mean
            acc + d * d
        } / voiced.size
        return (40f - sqrt(variance) / 6f).coerceIn(0f, 40f)
    }

    fun rangeScore(pitches: List<Float>): Float {
        val voiced = pitches.filter { it > 50f }
        if (voiced.size < 8) return 6f
        val low = voiced.min()
        val high = voiced.max()
        val semitones = (12f * (ln((high / low).toDouble()) / ln(2.0))).toFloat()
        return min(30f, max(6f, semitones * 3.2f))
    }

    fun timingScore(
        pitches: List<Float>,
        sampleRate: Int,
        lyrics: List<LyricLine>,
        punchStartMs: Long,
        hopMs: Int = 30,
    ): Float {
        val samples = pitches.mapIndexed { index, hz ->
            PitchSample(timestampMs = index * hopMs.toLong(), frequencyHz = hz, confidence = if (hz > 50f) 0.8f else 0f)
        }
        return timingFromSamples(samples, lyrics, punchStartMs) * 0.3f
    }
}
