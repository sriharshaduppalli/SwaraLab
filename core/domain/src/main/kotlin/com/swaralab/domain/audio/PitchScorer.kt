package com.swaralab.domain.audio

import com.swaralab.domain.model.LyricLine
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device take score. Stability 40, range 30, lyric timing 30. No ML, no cloud.
 */
object PitchScorer {
    fun score(
        dryVocal: File,
        lyrics: List<LyricLine> = emptyList(),
        punchStartMs: Long = 0L,
    ): Float {
        val pcm = WavPcm.readMono(dryVocal) ?: return 0f
        val contour = contour(pcm)
        val stability = stabilityScore(contour)
        val range = rangeScore(contour)
        val timing = timingScore(contour, pcm.sampleRate, lyrics, punchStartMs)
        return (stability + range + timing).coerceIn(0f, 100f)
    }

    fun contour(pcm: MonoPcm, hopMs: Int = 30): List<Float> {
        val hop = ((pcm.sampleRate * hopMs) / 1000).coerceAtLeast(1)
        val window = (pcm.sampleRate * 40 / 1000).coerceAtLeast(hop)
        val out = ArrayList<Float>(pcm.samples.size / hop + 1)
        var i = 0
        while (i + window <= pcm.samples.size) {
            val slice = pcm.samples.copyOfRange(i, i + window)
            val hz = VocalSignalAnalyzer.detectPitchHz(slice, slice.size, pcm.sampleRate)
            out += if (hz in 75f..1000f) hz else 0f
            i += hop
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
        val timed = lyrics.filter { it.startMs != null }
        if (timed.isEmpty()) {
            val voiced = pitches.count { it > 50f }
            return if (pitches.isEmpty()) 10f else (30f * voiced / pitches.size).coerceIn(8f, 30f)
        }
        var hits = 0
        var attempts = 0
        timed.forEach { line ->
            val start = ((line.startMs ?: 0L) - punchStartMs).coerceAtLeast(0L)
            val end = (line.endMs ?: ((line.startMs ?: 0L) + 3_000L)) - punchStartMs
            val from = (start / hopMs).toInt()
            val to = (end / hopMs).toInt().coerceAtMost(pitches.lastIndex)
            if (from > to || from !in pitches.indices) return@forEach
            attempts += 1
            val window = pitches.subList(from, to + 1)
            if (window.any { it > 50f }) hits += 1
        }
        if (attempts == 0) return 12f
        return (30f * hits / attempts).coerceIn(0f, 30f)
    }
}
