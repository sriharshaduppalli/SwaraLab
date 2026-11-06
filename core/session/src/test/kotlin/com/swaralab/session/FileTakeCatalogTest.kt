package com.swaralab.session

import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.TakeRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FileTakeCatalogTest {
    @Test
    fun upsertAndMarkConversion() = runBlocking {
        val root = createTempDirectory("takes").toFile()
        val catalog = FileTakeCatalog(root)
        catalog.upsertTake(
            "session-1",
            TakeRecord(
                id = "take-1",
                dryWavRelativePath = "media/takes/take-1.wav",
                durationMs = 4_000L,
                createdAt = 1L,
            ),
        )
        assertEquals(1, catalog.listTakes("session-1").size)
        catalog.markConversion("session-1", "take-1", ConversionStatus.DONE, "media/takes/take-1.m4a")
        val stored = catalog.listTakes("session-1").single()
        assertEquals(ConversionStatus.DONE, stored.conversion)
        assertEquals("media/takes/take-1.m4a", stored.aacRelativePath)
        assertTrue(File(File(root, "session-1"), "media/takes/index.json").isFile)
        assertTrue(!stored.favorite)
        assertTrue(catalog.toggleFavorite("session-1", "take-1"))
        assertTrue(catalog.listTakes("session-1").single().favorite)
        assertTrue(!catalog.toggleFavorite("session-1", "take-1"))
        assertTrue(!catalog.listTakes("session-1").single().favorite)
        val note = catalog.addNote("session-1", "take-1", "  closer next pass  ")
        assertEquals("closer next pass", note?.text)
        assertEquals(1, catalog.listTakes("session-1").single().notes.size)
        catalog.deleteNote("session-1", "take-1", note!!.id)
        assertTrue(catalog.listTakes("session-1").single().notes.isEmpty())
    }

    @Test
    fun pruneTakesDeletesWavsNotKeptByLiveClips() = runBlocking {
        val root = createTempDirectory("takes-prune").toFile()
        val catalog = FileTakeCatalog(root)
        val sessionDir = File(root, "session-1/media/takes")
        sessionDir.mkdirs()
        File(sessionDir, "keep.wav").writeText("keep")
        File(sessionDir, "drop.wav").writeText("drop")
        File(sessionDir, "draft.wav").writeText("draft")
        catalog.upsertTake(
            "session-1",
            TakeRecord(id = "keep", dryWavRelativePath = "media/takes/keep.wav", durationMs = 1L, createdAt = 1L),
        )
        catalog.upsertTake(
            "session-1",
            TakeRecord(id = "drop", dryWavRelativePath = "media/takes/drop.wav", durationMs = 1L, createdAt = 1L),
        )
        val removed = catalog.pruneTakes("session-1", setOf("media/takes/keep.wav"))
        assertEquals(1, removed)
        assertTrue(File(sessionDir, "keep.wav").isFile)
        assertTrue(File(sessionDir, "draft.wav").isFile)
        assertTrue(!File(sessionDir, "drop.wav").isFile)
        assertEquals(listOf("keep"), catalog.listTakes("session-1").map { it.id })
    }
}
