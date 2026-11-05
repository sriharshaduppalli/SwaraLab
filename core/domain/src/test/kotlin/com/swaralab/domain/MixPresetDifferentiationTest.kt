package com.swaralab.domain

import com.swaralab.domain.audio.VocalMixProcessor
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class MixPresetDifferentiationTest {
    @Test
    fun rawSongBouncesDifferAcrossMixChips() {
        val raw = File("C:/Users/sriha/Desktop/Applications/Music_app/SwaraLab/build/mix-compare/raw_48k.wav")
        assertTrue("Need the decoded raw song at ${raw.absolutePath}", raw.isFile)
        val (leftIn, rightIn, rate) = readStereoWav(raw)
        val mid = FloatArray(min(leftIn.size, rightIn.size)) { i -> (leftIn[i] + rightIn[i]) * 0.5f }
        val outDir = raw.parentFile
        val reports = linkedMapOf<VocalPreset, MixStat>()
        for (preset in listOf(VocalPreset.RAW, VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO)) {
            val voice = mid.copyOf()
            val chain = VocalChain.preset(preset)
            VocalMixProcessor.applyChain(voice, rate, chain)
            val (left, right) = VocalMixProcessor.spreadStereo(voice, rate, chain)
            VocalMixProcessor.finishMasterStereo(left, right, targetLufs = -14f)
            writeStereoWav(File(outDir, "hear_${preset.name.lowercase()}.wav"), left, right, rate)
            reports[preset] = measure(left, right, rate)
        }
        val dry = reports.getValue(VocalPreset.RAW)
        val close = reports.getValue(VocalPreset.CLOSE)
        val hall = reports.getValue(VocalPreset.STAGE)
        val phone = reports.getValue(VocalPreset.RADIO)
        println(
            reports.entries.joinToString("\n") { (preset, stat) ->
                "${preset.name}: width=${"%.3f".format(stat.width)} gap=${"%.5f".format(stat.gap)} " +
                    "voice=${"%.3f".format(stat.voice)} crest=${"%.2f".format(stat.crest)} " +
                    "air=${"%.5f".format(stat.air)} body=${"%.4f".format(stat.body)}"
            },
        )
        assertTrue("Soft hall should be the widest room, hall=${hall.width} close=${close.width} dry=${dry.width}", hall.width > close.width && close.width > dry.width)
        assertTrue("Phone room should sit between dry and hall, phone=${phone.width}", phone.width > close.width * 0.85f && phone.width < hall.width)
        assertTrue("Keep dry should stay the narrowest, dry=${dry.width} close=${close.width}", close.width > dry.width * 1.8f)
    }

    private data class MixStat(
        val width: Float,
        val gap: Float,
        val voice: Float,
        val crest: Float,
        val air: Float,
        val body: Float,
    )

    private fun measure(left: FloatArray, right: FloatArray, rate: Int): MixStat {
        val n = min(left.size, right.size)
        var midSq = 0.0
        var sideSq = 0.0
        var peak = 0f
        val hop = rate / 20
        val win = rate / 10
        val gaps = ArrayList<Float>()
        val voices = ArrayList<Float>()
        for (i in 0 until n) {
            val m = (left[i] + right[i]) * 0.5f
            val s = (left[i] - right[i]) * 0.5f
            midSq += m * m
            sideSq += s * s
            peak = maxOf(peak, abs(left[i]), abs(right[i]))
        }
        var i = 0
        while (i + win < n) {
            var e = 0.0
            for (k in 0 until win) {
                val m = (left[i + k] + right[i + k]) * 0.5f
                e += m * m
            }
            val rms = sqrt(e / win).toFloat()
            if (rms < 0.02f) gaps.add(rms) else if (rms > 0.05f) voices.add(rms)
            i += hop
        }
        val mid = FloatArray(n) { idx -> (left[idx] + right[idx]) * 0.5f }
        return MixStat(
            width = sqrt((sideSq / midSq.coerceAtLeast(1e-12)).toFloat()),
            gap = if (gaps.isEmpty()) 0f else gaps.sorted()[gaps.size / 2],
            voice = if (voices.isEmpty()) 0f else voices.sorted()[voices.size / 2],
            crest = peak / sqrt((midSq / n).toFloat()).coerceAtLeast(1e-6f),
            air = filteredRms(mid, rate, highPassHz = 6_500.0),
            body = filteredRms(mid, rate, highPassHz = 200.0) - filteredRms(mid, rate, highPassHz = 2_200.0),
        )
    }

    private fun filteredRms(mono: FloatArray, rate: Int, highPassHz: Double): Float {
        val rc = 1.0 / (2.0 * Math.PI * highPassHz)
        val a = (rc / (rc + 1.0 / rate)).toFloat()
        var prevX = 0f
        var prevY = 0f
        var sum = 0.0
        for (x in mono) {
            val y = a * (prevY + x - prevX)
            prevX = x
            prevY = y
            sum += y * y
        }
        return sqrt((sum / mono.size).toFloat())
    }

    private fun readStereoWav(file: File): Triple<FloatArray, FloatArray, Int> {
        val bytes = file.readBytes()
        val rate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val frames = (bytes.size - 44) / 4
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (i in 0 until frames) {
            left[i] = pcm.short / 32768f
            right[i] = pcm.short / 32768f
        }
        return Triple(left, right, rate)
    }

    private fun writeStereoWav(file: File, left: FloatArray, right: FloatArray, rate: Int) {
        val frames = min(left.size, right.size)
        val data = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray())
        data.putInt(36 + frames * 4)
        data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray())
        data.putInt(16)
        data.putShort(1)
        data.putShort(2)
        data.putInt(rate)
        data.putInt(rate * 4)
        data.putShort(4)
        data.putShort(16)
        data.put("data".toByteArray())
        data.putInt(frames * 4)
        for (i in 0 until frames) {
            data.putShort((left[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            data.putShort((right[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        file.writeBytes(data.array())
    }
}
