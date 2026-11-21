package com.swaralab.feature.studio

import com.swaralab.domain.command.SessionReducer
import com.swaralab.domain.identity.UuidIdGenerator
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.SessionDocument
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.SingMode
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineError
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.InstrumentalResult
import com.swaralab.domain.port.MixPreview
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.SessionRepository
import com.swaralab.domain.port.SessionSummary
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.time.SystemClock
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.RestoreSession
import com.swaralab.domain.usecase.SplitRecordedClip
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdateMusicalSettings
import com.swaralab.domain.usecase.UpdatePracticeStats
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateRecordedClip
import com.swaralab.domain.usecase.UpdateTrackChain
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.domain.usecase.UpdateVocalSyncOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {
    private lateinit var projectsDir: File
    private lateinit var repo: InMemorySessionRepository
    private lateinit var reducer: SessionReducer
    private lateinit var session: SessionDocument
    private lateinit var audio: FakeAudioEngine
    private lateinit var render: FakeRenderEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking {
            projectsDir = Files.createTempDirectory("swaralab-studio-vm-test").toFile()
            reducer = SessionReducer(UuidIdGenerator(), SystemClock())
            repo = InMemorySessionRepository()
            session = reducer.create(SessionKind.SONG, "Test Booth Song")
            repo.save(session)
            audio = FakeAudioEngine()
            render = FakeRenderEngine()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::projectsDir.isInitialized) {
            projectsDir.deleteRecursively()
        }
    }

    @Test
    fun renameLyricsLayersAndUndoUpdateSessionState() {
        runBlocking {
            val vm = createViewModel()
            val initial = awaitUi(vm) { it != null }!!
            assertEquals("Test Booth Song", initial.title)

            vm.rename("Raga Yaman Live")
            val renamed = awaitUi(vm) { it?.title == "Raga Yaman Live" }!!
            assertEquals("Raga Yaman Live", renamed.title)

            vm.saveLyrics("[00:02.00] First line\n[00:06.50] Second line")
            val withLyrics = awaitUi(vm) { it?.lines?.size == 2 }!!
            assertEquals(2_000L, withLyrics.lines[0].startMs)
            assertEquals(6_500L, withLyrics.lines[1].startMs)

            vm.seekToLyricLine(1)
            val sought = awaitUi(vm) { it?.activeLine == 1 }!!
            assertEquals(6_500L, sought.punchStartMs)

            vm.addLayer(TrackRole.HARMONY)
            val withHarmony = awaitUi(vm) { it?.parts?.size == 2 }!!
            assertEquals("Harmony", withHarmony.parts.last().label)
            assertTrue(vm.canUndo.value)

            vm.undoEdit()
            val undone = awaitUi(vm) { it?.parts?.size == 1 }!!
            assertEquals(1, undone.parts.size)
            assertTrue(vm.canRedo.value)

            vm.redoEdit()
            val redone = awaitUi(vm) { it?.parts?.size == 2 }!!
            assertEquals(2, redone.parts.size)
            projectsDir.deleteRecursively()
        }
    }

    @Test
    fun prepareSingModeDuetEnsuresTwoPartsAndSelectsSecond() {
        runBlocking {
            val vm = createViewModel()
            awaitUi(vm) { it != null }
            var readyCalled = false
            vm.prepareSingMode(
                mode = SingMode.DUET,
                onNeedSongFromPhone = {},
                onReadyToRecord = { readyCalled = true },
            )
            val duetUi = awaitUi(vm) {
                val p = it?.parts.orEmpty()
                p.size >= 2 && it?.selectedPartId == p[1].id
            }!!
            assertTrue(readyCalled)
            assertEquals(duetUi.parts[1].id, duetUi.selectedPartId)
            projectsDir.deleteRecursively()
        }
    }

    private fun createViewModel(): StudioViewModel {
        return StudioViewModel(
            sessionId = session.id,
            projectsDir = projectsDir,
            loadSession = LoadSession(repo),
            renameSession = RenameSession(reducer, repo),
            updateGear = UpdateGear(reducer, repo),
            updateVocalPreset = UpdateVocalPreset(reducer, repo),
            updateLyrics = UpdateLyrics(reducer, repo, UuidIdGenerator()),
            addVoicePart = AddVoicePart(reducer, repo),
            commitRecordedTake = CommitRecordedTake(reducer, repo),
            deleteRecordedClip = DeleteRecordedClip(reducer, repo),
            importBackingTrack = ImportBackingTrack(reducer, repo),
            updatePublish = UpdatePublishSettings(reducer, repo),
            updateMusical = UpdateMusicalSettings(reducer, repo),
            updateVocalSyncOffset = UpdateVocalSyncOffset(reducer, repo),
            trimRecordedClip = TrimRecordedClip(reducer, repo),
            splitRecordedClip = SplitRecordedClip(reducer, repo),
            updateRecordedClip = UpdateRecordedClip(reducer, repo),
            updateTrackChain = UpdateTrackChain(reducer, repo),
            updateTrackMix = UpdateTrackMix(reducer, repo),
            preferRecordedClip = PreferRecordedClip(reducer, repo),
            updatePractice = UpdatePracticeStats(reducer, repo),
            restoreSession = RestoreSession(repo),
            audio = audio,
            render = render,
        )
    }

    private suspend fun awaitUi(vm: StudioViewModel, predicate: (StudioUi?) -> Boolean): StudioUi? {
        return withTimeout(3_000L) {
            while (!predicate(vm.ui.value)) {
                kotlinx.coroutines.delay(20L)
            }
            vm.ui.value
        }
    }

    private class InMemorySessionRepository : SessionRepository {
        private val map = LinkedHashMap<String, SessionDocument>()
        override suspend fun save(document: SessionDocument) {
            map[document.id] = document
        }

        override suspend fun load(id: String): SessionDocument? = map[id]

        override suspend fun list(): List<SessionSummary> = map.values.map {
            SessionSummary(
                id = it.id,
                title = it.title,
                kind = it.kind,
                updatedAtEpochMs = it.updatedAtEpochMs,
                takeCount = it.tracks.filter { t -> t.role != TrackRole.BACKING }.sumOf { t -> t.clips.size },
            )
        }

        override suspend fun delete(id: String) {
            map.remove(id)
        }
    }

    private class FakeAudioEngine : AudioEngine {
        private val states = MutableStateFlow(AudioEngineState(status = EngineStatus.ARMED, output = GearRoute.WIRED))
        private val errs = MutableSharedFlow<AudioEngineError>(extraBufferCapacity = 4)
        override val state: StateFlow<AudioEngineState> = states.asStateFlow()
        override val errors: SharedFlow<AudioEngineError> = errs.asSharedFlow()

        override suspend fun arm(request: ArmRequest): MonitorDecision {
            states.value = states.value.copy(
                status = EngineStatus.ARMED,
                sessionId = request.sessionId,
                output = request.output,
                decision = MonitorDecision.ALLOW,
            )
            return MonitorDecision.ALLOW
        }

        override suspend fun startTake(): TakeControl {
            states.value = states.value.copy(status = EngineStatus.RECORDING, liveTakeId = "take-1")
            return TakeControl.Started("take-1")
        }

        override suspend fun stopTake(): FinishedTake? {
            states.value = states.value.copy(status = EngineStatus.ARMED, liveTakeId = null)
            return FinishedTake(relativePath = "media/takes/take-1.wav", durationMs = 2_400L, peak = 0.42f, takeId = "take-1")
        }

        override fun seekTake(positionMs: Long) {
            states.value = states.value.copy(positionMs = positionMs)
        }

        override fun disarm() {
            states.value = AudioEngineState()
        }
    }

    private class FakeRenderEngine : RenderEngine {
        override suspend fun export(
            document: SessionDocument,
            request: ExportRequest,
            onProgress: (Float) -> Unit,
        ): ExportResult = ExportResult.Written("exports/mix.wav", 2_400L)

        override suspend fun preview(document: SessionDocument, maxDurationMs: Long): MixPreview =
            MixPreview(FloatArray(480), FloatArray(480), 48_000)

        override suspend fun writeMonitorBed(document: SessionDocument, destination: File): Boolean = true

        override suspend fun writeInstrumental(source: File, destination: File): InstrumentalResult =
            InstrumentalResult.Written(durationMs = 2_400L, mostlyMono = false)

        override suspend fun encodeCompanionAac(wav: File, destination: File): Boolean = true
    }
}
