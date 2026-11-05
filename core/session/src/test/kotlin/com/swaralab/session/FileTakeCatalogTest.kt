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
    }
}
