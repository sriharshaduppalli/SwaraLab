package com.swaralab.domain

import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.edit.SnapshotEditCommand
import com.swaralab.domain.edit.UndoManager
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.usecase.CleanUnusedTakes
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.RestoreSession
import com.swaralab.domain.usecase.UpdateRecordedClip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoAndTakesTest {
    @Test
    fun deleteClipParksTheClipAndLeavesTheSourcePath() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "Takes")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        CommitRecordedTake(reducer, store)(
            created.id,
            leadId,
            FinishedTake("media/takes/take-keep.wav", 4_000L, 0.4f),
        )
        val clipId = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips.single().id
        val edit = DeleteRecordedClip(reducer, store)(created.id, clipId)
        assertTrue(edit is SessionEdit.Applied)
        val clip = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips.single()
        assertFalse(clip.active)
        assertFalse(clip.preferred)
        assertEquals("media/takes/take-keep.wav", clip.sourceRelativePath)
    }

    @Test
    fun undoRestoresAMovedClip() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val restore = RestoreSession(store)
        val history = UndoManager()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "Move")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        CommitRecordedTake(reducer, store)(
            created.id,
            leadId,
            FinishedTake("media/takes/take-move.wav", 4_000L, 0.4f),
        )
        val before = LoadSession(store)(created.id)!!
        val clipId = before.tracks.first { it.id == leadId }.clips.single().id
        UpdateRecordedClip(reducer, store)(created.id, clipId, timelineStartMs = 1_500L)
        val after = LoadSession(store)(created.id)!!
        history.record(SnapshotEditCommand("Move clip", before, after, restore::invoke))
        assertTrue(history.canUndo.value)
        history.undo()
        assertEquals(
            0L,
            LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips.single().timelineStartMs,
        )
        history.redo()
        assertEquals(
            1_500L,
            LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips.single().timelineStartMs,
        )
    }

    @Test
    fun cleanUnusedDropsParkedClipsThatNoLiveClipNeeds() = runBlocking {
        val (_, reducer) = testReducer()
        val store = InMemorySessions()
        val created = CreateSession(reducer, store)(SessionKind.SONG, "Clean")
        val leadId = created.tracks.first { it.role == TrackRole.LEAD }.id
        CommitRecordedTake(reducer, store)(
            created.id,
            leadId,
            FinishedTake("media/takes/old.wav", 4_000L, 0.4f),
        )
        val oldId = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips.single().id
        DeleteRecordedClip(reducer, store)(created.id, oldId)
        CommitRecordedTake(reducer, store)(
            created.id,
            leadId,
            FinishedTake("media/takes/new.wav", 3_000L, 0.5f),
        )
        val result = CleanUnusedTakes(store)(created.id)
        assertEquals(1, result.removedClips)
        val clips = LoadSession(store)(created.id)!!.tracks.first { it.id == leadId }.clips
        assertEquals(1, clips.size)
        assertEquals("media/takes/new.wav", clips.single().sourceRelativePath)
        assertTrue(clips.single().active)
    }
}
