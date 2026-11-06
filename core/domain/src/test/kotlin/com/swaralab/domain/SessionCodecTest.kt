package com.swaralab.domain

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.format.SessionFormat
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecTest {
    private val codec = SessionCodec()

    @Test
    fun roundTripKeepsACommittedTake() {
        val (_, reducer) = testReducer()
        val created = reducer.create(SessionKind.SONG, "Verse")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        val edited = reducer.apply(
            created,
            SessionCommand.CommitTake(leadId, SessionLayout.takeWav("id-9"), 2_500, label = "Lead"),
        ) as SessionEdit.Applied
        val restored = codec.decode(codec.encode(edited.document))
        assertEquals(SessionFormat.NAME, restored.format)
        assertEquals(SessionFormat.VERSION, restored.version)
        assertEquals("Verse", restored.title)
        assertEquals("media/takes/id-9.wav", restored.tracks.first { it.role == TrackRole.LEAD }.clips.single().sourceRelativePath)
        assertEquals(0L, restored.playheadMs)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val (_, reducer) = testReducer()
        val document = reducer.create(SessionKind.PRACTICE, "Scale")
        val encoded = codec.encode(document).replaceFirst("{", "{\n  \"futureField\": true,")
        assertEquals("Scale", codec.decode(encoded).title)
    }

    @Test
    fun aNewerSessionVersionIsRefused() {
        val (_, reducer) = testReducer()
        val encoded = codec.encode(reducer.create(SessionKind.SONG, "Song"))
            .replace("\"version\": 1", "\"version\": 99")
        val error = runCatching { codec.decode(encoded) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
