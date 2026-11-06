package com.swaralab.domain.edit

import com.swaralab.domain.model.SessionDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One timeline edit. Execute applies it; undo restores the previous session.json. */
interface EditCommand {
    val description: String

    suspend fun execute()

    suspend fun undo()
}

/**
 * Command payload is a pair of session documents (memento).
 * Audio files are never copied — only clip/take metadata in session.json.
 */
class SnapshotEditCommand(
    override val description: String,
    private val before: SessionDocument,
    private val after: SessionDocument,
    private val restore: suspend (SessionDocument) -> Unit,
) : EditCommand {
    override suspend fun execute() = restore(after)

    override suspend fun undo() = restore(before)
}

class UndoManager(
    private val maxDepth: Int = 50,
) {
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()

    private val canUndoState = MutableStateFlow(false)
    private val canRedoState = MutableStateFlow(false)

    val canUndo: StateFlow<Boolean> = canUndoState.asStateFlow()
    val canRedo: StateFlow<Boolean> = canRedoState.asStateFlow()

    suspend fun execute(command: EditCommand) {
        command.execute()
        record(command)
    }

    fun record(command: EditCommand) {
        undoStack.addLast(command)
        while (undoStack.size > maxDepth) undoStack.removeFirst()
        redoStack.clear()
        publish()
    }

    suspend fun undo() {
        val command = undoStack.removeLastOrNull() ?: return
        command.undo()
        redoStack.addLast(command)
        publish()
    }

    suspend fun redo() {
        val command = redoStack.removeLastOrNull() ?: return
        command.execute()
        undoStack.addLast(command)
        publish()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        publish()
    }

    private fun publish() {
        canUndoState.value = undoStack.isNotEmpty()
        canRedoState.value = redoStack.isNotEmpty()
    }
}
