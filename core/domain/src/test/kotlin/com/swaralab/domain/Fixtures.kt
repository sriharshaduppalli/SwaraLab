package com.swaralab.domain

import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.time.Clock

class ManualClock(var now: Long = 1_000L) : Clock {
    override fun nowEpochMs(): Long = now
}

class SequenceIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

fun testReducer(clock: ManualClock = ManualClock()): Pair<ManualClock, SessionReducer> {
    return clock to SessionReducer(SequenceIds(), clock)
}
