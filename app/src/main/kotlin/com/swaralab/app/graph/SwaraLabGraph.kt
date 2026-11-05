package com.swaralab.app.graph

import com.swaralab.domain.codec.SessionCodec
import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.port.AccountRepository
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.time.SystemClock
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.CurrentAccount
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.DeleteSession
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.SaveTake
import com.swaralab.domain.usecase.RegisterAccount
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.SignIn
import com.swaralab.domain.usecase.StartSession
import com.swaralab.domain.usecase.SignOut
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdateMusicalSettings
import com.swaralab.domain.usecase.UpdatePracticeStats
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateVocalChain
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.domain.usecase.UpdateVocalSyncOffset
import com.swaralab.engine.audio.AndroidAudioEngine
import com.swaralab.engine.render.WavMixRenderEngine
import com.swaralab.session.FileAccountRepository
import com.swaralab.domain.port.Analytics
import com.swaralab.domain.port.AnalyticsEvent
import com.swaralab.session.FileAnalytics
import com.swaralab.session.FileAppSettings
import com.swaralab.session.FileSessionRepository
import com.swaralab.app.media.PlaybackManager
import com.swaralab.app.work.CoroutineTakeConversion
import com.swaralab.session.FileTakeCatalog
import com.swaralab.session.SessionInvitePack
import android.content.Context
import java.io.File

/**
 * Composition root. Screens receive use cases and ports from here.
 * The audio callback, once connected, must not resolve dependencies on its thread.
 */
class SwaraLabGraph(context: Context) {
    private val filesDir = context.filesDir
    private val clock = SystemClock()
    private val ids = UuidIdGenerator()
    private val reducer = SessionReducer(ids, clock)
    val projectsDir = File(filesDir, "swaralab/projects")
    val sessions: SessionRepository = FileSessionRepository(projectsDir, SessionCodec())
    val takeCatalog = FileTakeCatalog(projectsDir)
    val takeConversion = CoroutineTakeConversion(takeCatalog)
    val accounts: AccountRepository = FileAccountRepository(File(filesDir, "swaralab/accounts.json"))
    val appSettings = FileAppSettings(File(filesDir, "swaralab/settings.properties"))
    val analytics: Analytics = FileAnalytics(File(filesDir, "swaralab/analytics.log")).also {
        it.log(AnalyticsEvent.APP_OPEN)
    }
    val audio: AudioEngine = AndroidAudioEngine(projectsDir, ids)
    val playback = PlaybackManager(context.applicationContext)
    val render: RenderEngine = WavMixRenderEngine(projectsDir)
    val createSession = CreateSession(reducer, sessions)
    val listSessions = ListSessions(sessions)
    val startSession = StartSession(createSession, listSessions)
    val deleteSession = DeleteSession(sessions)
    val loadSession = LoadSession(sessions)
    val renameSession = RenameSession(reducer, sessions)
    val updateGear = UpdateGear(reducer, sessions)
    val updateVocalPreset = UpdateVocalPreset(reducer, sessions)
    val updateVocalChain = UpdateVocalChain(reducer, sessions)
    val updateTrackMix = UpdateTrackMix(reducer, sessions)
    val updateVocalSyncOffset = UpdateVocalSyncOffset(reducer, sessions)
    val updateLyrics = UpdateLyrics(reducer, sessions, ids)
    val addVoicePart = AddVoicePart(reducer, sessions)
    val commitRecordedTake = CommitRecordedTake(reducer, sessions)
    val saveTake = SaveTake(commitRecordedTake, takeCatalog, takeConversion, projectsDir)
    val deleteRecordedClip = DeleteRecordedClip(reducer, sessions)
    val importBackingTrack = ImportBackingTrack(reducer, sessions)
    val updatePublish = UpdatePublishSettings(reducer, sessions)
    val updateMusical = UpdateMusicalSettings(reducer, sessions)
    val trimRecordedClip = TrimRecordedClip(reducer, sessions)
    val preferRecordedClip = PreferRecordedClip(reducer, sessions)
    val updatePractice = UpdatePracticeStats(reducer, sessions)
    val invitePack = SessionInvitePack(projectsDir, SessionCodec(), ids, clock)
    val registerAccount = RegisterAccount(accounts, ids)
    val signIn = SignIn(accounts)
    val signOut = SignOut(accounts)
    val currentAccount = CurrentAccount(accounts)
}
