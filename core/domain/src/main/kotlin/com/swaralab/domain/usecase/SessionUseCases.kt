package com.swaralab.domain.usecase

import com.swaralab.domain.command.RejectReason
import com.swaralab.domain.command.SessionCommand
import com.swaralab.domain.command.SessionEdit
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.IdGenerator
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricAlignment
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.LyricSheet
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
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

class RenameSession(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, title: String): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.Rename(title))
    }
}

class UpdateGear(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, route: GearRoute): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetGear(route.recordingSnapshot()))
    }
}

class UpdateVocalPreset(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, preset: VocalPreset): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetVocalChain(VocalChain.preset(preset)))
    }
}

class UpdateLyrics(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(id: String, text: String): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> LyricLine(id = ids.newId(), text = line) }
        val sheet = LyricSheet(
            rawText = text.trimEnd(),
            lines = lines,
            alignment = if (lines.isEmpty()) LyricAlignment.NONE else LyricAlignment.MANUAL,
        )
        return applyAndSave(reducer, sessions, document, SessionCommand.SetLyrics(sheet))
    }
}

class AddVoicePart(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, role: TrackRole, name: String): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        return applyAndSave(reducer, sessions, document, SessionCommand.AddTrack(role, name))
    }
}

class UpdatePublishSettings(
    private val reducer: SessionReducer,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke(id: String, target: PublishTarget): SessionEdit {
        val document = sessions.load(id) ?: return SessionEdit.Rejected(RejectReason.UNKNOWN_SESSION)
        val settings = document.publish.withTarget(target)
        return applyAndSave(reducer, sessions, document, SessionCommand.SetPublish(settings))
    }
}

private suspend fun applyAndSave(
    reducer: SessionReducer,
    sessions: SessionRepository,
    document: SessionDocument,
    command: SessionCommand,
): SessionEdit {
    val edit = reducer.apply(document, command)
    if (edit is SessionEdit.Applied) sessions.save(edit.document)
    return edit
}
