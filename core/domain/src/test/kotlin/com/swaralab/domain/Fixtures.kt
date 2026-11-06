package com.swaralab.domain

import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary
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

class InMemorySessions : SessionRepository {
    private val map = linkedMapOf<String, com.swaralab.domain.model.SessionDocument>()

    override suspend fun save(document: com.swaralab.domain.model.SessionDocument) {
        map[document.id] = document
    }

    override suspend fun load(id: String) = map[id]

    override suspend fun list(): List<SessionSummary> = map.values.map {
        SessionSummary(
            it.id,
            it.title,
            it.kind,
            it.updatedAtEpochMs,
            takeCount = it.tracks.filter { track -> track.role != TrackRole.BACKING }.sumOf { track -> track.clips.size },
        )
    }

    override suspend fun delete(id: String) {
        map.remove(id)
    }
}
