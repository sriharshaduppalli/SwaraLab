package com.swaralab.domain.time

fun interface Clock {
    fun nowEpochMs(): Long
}

class SystemClock : Clock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
