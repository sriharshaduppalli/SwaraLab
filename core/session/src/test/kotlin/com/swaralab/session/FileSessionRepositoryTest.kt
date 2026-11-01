package com.swaralab.session

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.time.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileSessionRepositoryTest {
    @Test
    fun saveCreatesTheOpenFolderAndListSkipsACorruptNeighbor() = runBlocking {
        val root = Files.createTempDirectory("swaralab").toFile()
        try {
            val repository = FileSessionRepository(root, SessionCodec())
            val document = SessionReducer(UuidIdGenerator(), SystemClock()).create(SessionKind.SONG, "First")
            repository.save(document)

            val folder = root.resolve(document.id)
            assertTrue(File(folder, SessionLayout.DOCUMENT).isFile)
            assertFalse(File(folder, "${SessionLayout.DOCUMENT}.tmp").exists())
            SessionLayout.directories.forEach { relative ->
                assertTrue(relative, File(folder, relative).isDirectory)
            }

            val corrupt = root.resolve("broken")
            corrupt.mkdirs()
            File(corrupt, SessionLayout.DOCUMENT).writeText("{not json", Charsets.UTF_8)

            val listed = repository.list()
            assertEquals(listOf(document.id), listed.map { it.id })
            assertEquals("First", repository.load(document.id)?.title)

            repository.delete(document.id)
            assertNull(repository.load(document.id))
        } finally {
            root.deleteRecursively()
        }
    }
}
