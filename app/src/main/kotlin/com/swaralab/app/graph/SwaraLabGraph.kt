package com.swaralab.app.graph

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.time.SystemClock
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.engine.audio.UnconnectedAudioEngine
import com.swaralab.engine.render.UnconnectedRenderEngine
import com.swaralab.session.FileSessionRepository
import java.io.File

/**
 * Composition root. Screens receive use cases and ports from here.
 * The audio callback, once connected, must not resolve dependencies on its thread.
 */
class SwaraLabGraph(filesDir: File) {
    private val clock = SystemClock()
    private val ids = UuidIdGenerator()
    private val reducer = SessionReducer(ids, clock)
    val sessions: SessionRepository = FileSessionRepository(File(filesDir, "swaralab/projects"), SessionCodec())
    val audio: AudioEngine = UnconnectedAudioEngine()
    val render: RenderEngine = UnconnectedRenderEngine()
    val createSession = CreateSession(reducer, sessions)
    val listSessions = ListSessions(sessions)
    val loadSession = LoadSession(sessions)
    val updateGear = UpdateGear(reducer, sessions)
    val updatePublish = UpdatePublishSettings(reducer, sessions)
}
