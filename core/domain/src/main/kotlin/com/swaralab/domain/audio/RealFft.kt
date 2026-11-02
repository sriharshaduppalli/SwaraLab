package com.swaralab.domain.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 Cooley–Tukey FFT for real-time vocal frames. */
internal object RealFft {
    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, inverse = false)

    fun inverse(re: FloatArray, im: FloatArray) = transform(re, im, inverse = true)

    private fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val ang = (if (inverse) 2.0 else -2.0) * PI / len
            val wLenRe = cos(ang).toFloat()
            val wLenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val vi = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + half] = ur - vr
                    im[i + k + half] = ui - vi
                    val nextRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            val scale = 1f / n
            for (i in 0 until n) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }
}
