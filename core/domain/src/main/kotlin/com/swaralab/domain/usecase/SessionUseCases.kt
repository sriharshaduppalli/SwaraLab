package com.swaralab.domain.usecase

import com.swaralab.domain.command.RejectReason
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.recordingSnapshot
import com.swaralab.domain.model.withTarget
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary

class CreateSession(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(kind: SessionKind, title: String): SessionDocument {
        val document = reducer.create(kind, title)
        sessions.save(document)
        return document
    }
}

class ListSessions(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(): List<SessionSummary> = sessions.list()
}

class LoadSession(
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String): SessionDocument? = sessions.load(id)
}

class UpdateGear(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, route: GearRoute): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(document, SessionCommand.SetGear(route.recordingSnapshot()))
    }

    private suspend fun applyAndSave(document: SessionDocument, command: SessionCommand): SessionEdit {
        val edit = reducer.apply(document, command)
        if (edit is SessionEdit.Applied) sessions.save(edit.document)
        return edit
    }
}

class UpdatePublishSettings(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, target: PublishTarget): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val settings = document.publish.withTarget(target)
        val edit = reducer.apply(document, SessionCommand.SetPublish(settings))
        if (edit is SessionEdit.Applied) sessions.save(edit.document)
        return edit
    }
}
