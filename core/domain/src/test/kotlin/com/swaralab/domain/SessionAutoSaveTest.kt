package com.swaralab.domain

import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.port.OpenSessionPointer
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.SessionAutoSave
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionAutoSaveTest {
    @Test
    fun persistPlayheadWritesMetadataWithoutChangingTitle() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val pointer = MemoryPointer()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "Keep name")
        val autoSave = SessionAutoSave(store, pointer)
        autoSave.rememberOpened(created.id, 0L)
        autoSave.persistPlayhead(created.id, 2_400L)
        assertEquals(created.id, pointer.sessionId())
        assertEquals(2_400L, pointer.playheadMs())
        val loaded = LoadSession(store)(created.id)!!
        assertEquals("Keep name", loaded.title)
        assertEquals(2_400L, loaded.playheadMs)
    }
}

private class MemoryPointer : OpenSessionPointer {
    private var id: String? = null
    private var playhead = 0L

    override fun remember(sessionId: String, playheadMs: Long) {
        id = sessionId
        playhead = playheadMs
    }

    override fun sessionId(): String? = id

    override fun playheadMs(): Long = playhead
}
