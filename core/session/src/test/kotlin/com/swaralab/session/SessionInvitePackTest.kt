package com.swaralab.session

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.time.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionInvitePackTest {
    @Test
    fun exportAndImportCreatesAnEmptyPartForTheFriend() {
        val root = Files.createTempDirectory("swaralab-invite").toFile()
        try {
            val ids = UuidIdGenerator()
            val clock = SystemClock()
            val codec = SessionCodec()
            val store = FileSessionRepository(root, codec)
            val reducer = SessionReducer(ids, clock)
            var document = reducer.create(SessionKind.SONG, "Duet Night")
            val backing = document.tracks.first { it.role == TrackRole.BACKING }.id
            document = (reducer.apply(
                document,
                SessionCommand.ImportBacking(
                    trackId = backing,
                    relativePath = SessionLayout.backingFile("guide.wav"),
                    durationMs = 4_000,
                    rightsConfirmed = true,
                    note = "Guide",
                ),
            ) as SessionEdit.Applied).document
            kotlinx.coroutines.runBlocking { store.save(document) }
            File(root, "${document.id}/${SessionLayout.backingFile("guide.wav")}").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(64))
            }

            val pack = SessionInvitePack(root, codec, ids, clock)
            val zip = File(root, "invite.zip")
            assertTrue(pack.exportZip(document.id, zip))
            val imported = pack.importZip(zip)
            requireNotNull(imported)
            assertTrue(imported.title.startsWith("Invite ·"))
            assertTrue(imported.tracks.any { it.name == "Your part" && it.clips.isEmpty() })
            assertEquals(1, imported.tracks.first { it.role == TrackRole.BACKING }.clips.size)
        } finally {
            root.deleteRecursively()
        }
    }
}
