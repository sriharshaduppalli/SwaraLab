package com.swaralab.domain.usecase

import com.swaralab.domain.port.OpenSessionPointer
import com.swaralab.domain.port.SessionRepository

/**
 * Clip/layer edits already write session.json immediately (atomic replace).
 * This only persists the last-opened pointer and playhead, debounced from the ViewModel.
 */
class SessionAutoSave(
    private val sessions: SessionRepository,
    private val pointer: OpenSessionPointer,
) {
    fun rememberOpened(sessionId: String, playheadMs: Long = 0L) {
        pointer.remember(sessionId, playheadMs)
    }

    suspend fun persistPlayhead(sessionId: String, playheadMs: Long) {
        val clamped = playheadMs.coerceAtLeast(0L)
        pointer.remember(sessionId, clamped)
        val document = sessions.load(sessionId) ?: return
        if (document.playheadMs == clamped) return
        sessions.save(document.copy(playheadMs = clamped))
    }

    fun lastOpenedId(): String? = pointer.sessionId()

    fun lastPlayheadMs(): Long = pointer.playheadMs()
}
