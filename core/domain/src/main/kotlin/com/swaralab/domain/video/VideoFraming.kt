package com.swaralab.domain.video

/**
 * How the 9:16 stage crops a camera frame so export matches the preview.
 */
data class PixelBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

object VideoFraming {
    const val PORTRAIT_WIDTH = 1080
    const val PORTRAIT_HEIGHT = 1920
    const val PORTRAIT_ASPECT = 9f / 16f

    fun displaySize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
        return if (rotationDegrees % 180 != 0) height to width else width to height
    }

    fun isPortraitNineSixteen(width: Int, height: Int, rotationDegrees: Int): Boolean {
        val (dw, dh) = displaySize(width, height, rotationDegrees)
        if (dh <= dw) return false
        val aspect = dw.toFloat() / dh.toFloat()
        return aspect in 0.50f..0.62f
    }

    fun centerCrop(srcWidth: Int, srcHeight: Int, destAspect: Float = PORTRAIT_ASPECT): PixelBox {
        val w = srcWidth.coerceAtLeast(2)
        val h = srcHeight.coerceAtLeast(2)
        val srcAspect = w.toFloat() / h.toFloat()
        return if (srcAspect > destAspect) {
            val cropW = even((h * destAspect).toInt().coerceIn(2, w))
            PixelBox(x = even((w - cropW) / 2), y = 0, width = cropW, height = even(h))
        } else {
            val cropH = even((w / destAspect).toInt().coerceIn(2, h))
            PixelBox(x = 0, y = even((h - cropH) / 2), width = even(w), height = cropH)
        }
    }

    fun sharedEndUs(videoUs: Long, audioUs: Long): Long {
        val video = videoUs.coerceAtLeast(0L)
        val audio = audioUs.coerceAtLeast(0L)
        return when {
            video <= 0L -> audio
            audio <= 0L -> video
            else -> minOf(video, audio)
        }
    }

    private fun even(value: Int): Int = value and 1.inv()
}
