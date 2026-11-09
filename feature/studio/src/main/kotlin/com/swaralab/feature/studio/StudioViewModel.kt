package com.swaralab.feature.studio

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.audio.BackingPlayback
import com.swaralab.domain.audio.LiveLyricFollower
import com.swaralab.domain.audio.LyricLanguage
import com.swaralab.domain.audio.MusicalKeys
import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import com.swaralab.domain.audio.ImportedTrack
import com.swaralab.domain.audio.LiveMonitorEffect
import com.swaralab.domain.audio.LyricHighlight
import com.swaralab.domain.audio.LyricSync
import com.swaralab.domain.audio.DetailedScore
import com.swaralab.domain.audio.LoopRegion
import com.swaralab.domain.audio.PitchScorer
import com.swaralab.domain.audio.TrackProcessingConfig
import com.swaralab.domain.audio.RecoverableDraft
import com.swaralab.domain.audio.RecordingDraft
import com.swaralab.domain.audio.StageTake
import com.swaralab.domain.audio.StageTakeState
import com.swaralab.domain.audio.TrackSource
import com.swaralab.domain.audio.WavPeaks
import com.swaralab.domain.model.ImportedTrackRecord
import com.swaralab.domain.model.ScaleMode
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.PracticeStats
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.SingMode
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.Analytics
import com.swaralab.domain.port.AnalyticsEvent
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.NoOpAnalytics
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.InstrumentalResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.TakeCatalog
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.port.TakeConversion
import com.swaralab.domain.port.isLive
import com.swaralab.domain.port.userMessage
import kotlinx.coroutines.flow.combine
import com.swaralab.domain.edit.SnapshotEditCommand
import com.swaralab.domain.edit.UndoManager
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CleanUnusedTakes
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.RestoreSession
import com.swaralab.domain.usecase.SaveTake
import com.swaralab.domain.usecase.SessionAutoSave
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.SplitRecordedClip
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateRecordedClip
import com.swaralab.domain.usecase.UpdateTrackChain
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdateMusicalSettings
import com.swaralab.domain.usecase.UpdatePracticeStats
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.AppCopy
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

data class VoicePartUi(
    val id: String,
    val label: String,
    val role: TrackRole = TrackRole.LEAD,
    val gainDb: Float = 0f,
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val clipCount: Int = 0,
    val chainPreset: VocalPreset? = null,
)

internal fun TrackRole.laneLabel(): String = when (this) {
    TrackRole.LEAD -> "Lead"
    TrackRole.DOUBLE -> "Double"
    TrackRole.HARMONY -> "Harmony"
    TrackRole.ADLIB -> "Ad-lib"
    TrackRole.EXTRA -> "Extra"
    TrackRole.BACKING -> "Backing"
    TrackRole.GUIDE -> "Guide"
    TrackRole.MIDI_SKETCH -> "Sketch"
    TrackRole.METRONOME -> "Click"
}

data class RecordedTakeUi(
    val id: String,
    val label: String,
    val durationText: String,
    val relativePath: String,
    val sourceInMs: Long,
    val durationMs: Long,
    val sourceDurationMs: Long,
    val timelineStartMs: Long,
    val preferred: Boolean,
    val active: Boolean = true,
    val fadeInMs: Long = 45L,
    val fadeOutMs: Long = 45L,
)

data class BackingTrackUi(
    val name: String,
    val durationText: String,
    val relativePath: String,
    val durationMs: Long,
    val loops: Boolean,
    val canMakeInstrumental: Boolean = false,
    val isInstrumental: Boolean = false,
)

data class PendingKeepTake(
    val finished: FinishedTake,
    val punchStartMs: Long,
    val punchEndMs: Long = 0L,
    val silent: Boolean,
    val score: Float? = null,
    val detailed: DetailedScore? = null,
    val analyzing: Boolean = false,
)

data class BackingPrepUi(
    val busy: Boolean = false,
    val progress: Float = 0f,
)

data class StudioUi(
    val title: String,
    val kind: SessionKind,
    val preset: VocalPreset,
    val publishTarget: PublishTarget,
    val backing: BackingTrackUi?,
    val lines: List<LyricLine>,
    val rawLyrics: String,
    val activeLine: Int,
    val parts: List<VoicePartUi>,
    val selectedPartId: String?,
    val canAddPart: Boolean,
    val takes: List<RecordedTakeUi>,
    val route: GearRoute,
    val stageNote: String?,
    val keyName: String,
    val tempoBpm: Float,
    val keyShiftSemitones: Int,
    val playbackRate: Float,
    val countInEnabled: Boolean,
    val metronomeEnabled: Boolean,
    val punchStartMs: Long,
    val punchEndMs: Long = 0L,
    val practice: PracticeStats,
    val lyricLanguage: String,
    val scaleMode: ScaleMode,
    val headphoneFx: Boolean,
    val heardLyric: String?,
    val monitorReverb: Float = 0f,
    val monitorDelay: Float = 0f,
    val vocalChain: VocalChain = VocalChain(),
    val voiceGain: Float = 1f,
    val trackGain: Float = 0.8f,
    val lyricHighlight: LyricHighlight = LyricHighlight(),
)

class StudioViewModel(
    private val sessionId: String,
    private val projectsDir: File,
    private val loadSession: LoadSession,
    private val renameSession: RenameSession,
    private val updateGear: UpdateGear,
    private val updateVocalPreset: UpdateVocalPreset,
    private val updateLyrics: UpdateLyrics,
    private val addVoicePart: AddVoicePart,
    private val commitRecordedTake: CommitRecordedTake,
    private val saveTake: SaveTake = SaveTake(commitRecordedTake),
    private val deleteRecordedClip: DeleteRecordedClip,
    private val importBackingTrack: ImportBackingTrack,
    private val updatePublish: UpdatePublishSettings,
    private val updateMusical: UpdateMusicalSettings,
    private val trimRecordedClip: TrimRecordedClip,
    private val splitRecordedClip: SplitRecordedClip? = null,
    private val updateRecordedClip: UpdateRecordedClip? = null,
    private val updateTrackChain: UpdateTrackChain? = null,
    private val updateTrackMix: UpdateTrackMix? = null,
    private val preferRecordedClip: PreferRecordedClip,
    private val updatePractice: UpdatePracticeStats,
    private val restoreSession: RestoreSession,
    private val cleanUnusedTakes: CleanUnusedTakes? = null,
    private val sessionAutoSave: SessionAutoSave? = null,
    val audio: AudioEngine,
    private val render: RenderEngine,
    private val takeCatalog: TakeCatalog? = null,
    @Suppress("unused")
    private val takeConversion: TakeConversion? = null,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {
    private val uiState = MutableStateFlow<StudioUi?>(null)
    val ui: StateFlow<StudioUi?> = uiState.asStateFlow()
    private val pendingKeepState = MutableStateFlow<PendingKeepTake?>(null)
    val pendingKeep: StateFlow<PendingKeepTake?> = pendingKeepState.asStateFlow()
    private val stageTakeState = MutableStateFlow<StageTakeState>(StageTakeState.Idle)
    val stageTake: StateFlow<StageTakeState> = stageTakeState.asStateFlow()
    private val recoverableDraftState = MutableStateFlow<RecoverableDraft?>(null)
    val recoverableDraft: StateFlow<RecoverableDraft?> = recoverableDraftState.asStateFlow()
    private val liveEffectState = MutableStateFlow<LiveMonitorEffect>(LiveMonitorEffect.None)
    val liveEffect: StateFlow<LiveMonitorEffect> = liveEffectState.asStateFlow()
    private val backingPrepState = MutableStateFlow(BackingPrepUi())
    val backingPrep: StateFlow<BackingPrepUi> = backingPrepState.asStateFlow()
    private val practiceLoopingState = MutableStateFlow(false)
    val practiceLooping: StateFlow<Boolean> = practiceLoopingState.asStateFlow()
    private val practiceLoopState = MutableStateFlow<LoopRegion?>(null)
    val practiceLoop: StateFlow<LoopRegion?> = practiceLoopState.asStateFlow()
    private var practiceLoopMark: Long? = null
    private var processJob: Job? = null
    private var activeLine = 0
    private var selectedPartId: String? = null
    private var stageNote: String? = null
    private var punchStartMs: Long = 0L
    private var punchEndMs: Long = 0L
    private var punchAutoStopped: Boolean = false
    private var instrumentalBusy = false
    private var lyricFollow = LiveLyricFollower.State()
    private var heardLyric: String? = null
    private var headphoneFx = true
    private var voiceGain = 1f
    private var trackGain = 0.8f
    private var liveHits = 0
    private var liveAttempts = 0
    private var liveLowHz = 0f
    private var liveHighHz = 0f
    private val undoManager = UndoManager()
    val canUndo = undoManager.canUndo
    val canRedo = undoManager.canRedo
    private var restoredPlayhead = false
    private var playheadJob: Job? = null

    private suspend fun captureEdit(description: String, block: suspend () -> Unit) {
        val before = loadSession(sessionId) ?: return
        block()
        val after = loadSession(sessionId) ?: return
        if (after == before) return
        undoManager.record(
            SnapshotEditCommand(
                description = description,
                before = before,
                after = after,
                restore = restoreSession::invoke,
            ),
        )
    }

    init {
        refresh()
        checkForDraft()
        viewModelScope.launch {
            combine(audio.state, ui, pendingKeep) { engine, studio, pending ->
                val draft = RecordingDraft(
                    track = studio?.backing?.let { backing ->
                        ImportedTrack(
                            id = backing.relativePath,
                            title = backing.name,
                            durationMs = backing.durationMs,
                            localRelativePath = backing.relativePath,
                            source = TrackSource.LOCAL_IMPORT,
                            hasLyrics = studio.lines.isNotEmpty(),
                        )
                    },
                    lyrics = studio?.lines.orEmpty(),
                    currentPositionMs = engine.positionMs,
                    voiceGain = studio?.voiceGain ?: 1f,
                    trackGain = studio?.trackGain ?: 0.8f,
                )
                StageTake.of(
                    status = engine.status,
                    countInBeat = engine.countInBeat,
                    positionMs = engine.positionMs,
                    durationMs = studio?.backing?.durationMs ?: 0L,
                    peak = engine.peak,
                    lyricIndex = studio?.lyricHighlight?.lineIndex ?: studio?.activeLine ?: 0,
                    draft = draft,
                    dryVocalPath = pending?.finished?.relativePath,
                    reviewDurationMs = pending?.finished?.durationMs ?: 0L,
                    error = engine.lastError?.userMessage() ?: if (engine.captureFailed) {
                        "Could not open the microphone on this device."
                    } else {
                        null
                    },
                    captureFailed = engine.captureFailed,
                ).let { take ->
                    if (take is StageTakeState.Review && pending != null) {
                        StageTakeState.Review(
                            dryVocalPath = pending.finished.relativePath,
                            durationMs = pending.finished.durationMs,
                            score = pending.score,
                            detailed = pending.detailed,
                            analyzing = pending.analyzing,
                        )
                    } else {
                        take
                    }
                }
            }.collect { stageTakeState.value = it }
        }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            renameSession(sessionId, title)
            refresh()
        }
    }

    fun selectPreset(preset: VocalPreset) {
        viewModelScope.launch {
            updateVocalPreset(sessionId, preset)
            stageNote = "Hearing ${preset.mixHearLabel()}…"
            refresh()
            hearMix(maxDurationMs = 12_000L, loop = true)
        }
    }

    fun saveLyrics(text: String, note: String? = null) {
        viewModelScope.launch {
            activeLine = 0
            lyricFollow = LiveLyricFollower.State()
            stageNote = note
            updateLyrics(sessionId, text)
            refresh()
        }
    }

    fun selectLine(index: Int) {
        val current = uiState.value ?: return
        if (index !in current.lines.indices) return
        activeLine = index
        lyricFollow = LiveLyricFollower.State(lineIndex = index)
        uiState.value = current.copy(activeLine = index)
    }

    fun stepLine(delta: Int) {
        val current = uiState.value ?: return
        if (current.lines.isEmpty()) return
        val next = (current.activeLine + delta).coerceIn(0, current.lines.lastIndex)
        activeLine = next
        lyricFollow = LiveLyricFollower.State(lineIndex = next)
        uiState.value = current.copy(activeLine = next)
    }

    fun beginLyricFollow() {
        lyricFollow = LiveLyricFollower.State(lineIndex = activeLine)
    }

    fun onHeardLyric(text: String) {
        heardLyric = text
        uiState.value = uiState.value?.copy(heardLyric = text)
    }

    fun setHeadphoneFx(enabled: Boolean) {
        headphoneFx = enabled
        uiState.value = uiState.value?.copy(headphoneFx = enabled)
    }

    fun setVoiceGain(gain: Float) {
        voiceGain = gain.coerceIn(0f, 2f)
        audio.setVoiceGain(voiceGain)
        uiState.value = uiState.value?.copy(voiceGain = voiceGain)
    }

    fun setTrackGain(gain: Float) {
        trackGain = gain.coerceIn(0f, 2f)
        audio.setTrackGain(trackGain)
        uiState.value = uiState.value?.copy(trackGain = trackGain)
    }

    fun setLiveEffect(effect: LiveMonitorEffect) {
        liveEffectState.value = effect
        if (effect !is LiveMonitorEffect.None) {
            headphoneFx = true
            uiState.value = uiState.value?.copy(headphoneFx = true)
        }
        audio.setLiveEffect(effect)
    }

    fun checkForDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(File(projectsDir, sessionId), SessionLayout.draftWav())
            if (!file.isFile || file.length() <= 44L + 48_000L) return@launch
            val durationMs = ((file.length() - 44L) * 1000L) / 96_000L
            recoverableDraftState.value = RecoverableDraft(SessionLayout.draftWav(), durationMs)
        }
    }

    fun recoverDraft() {
        audio.enableDraftMode(SessionLayout.draftWav())
        recoverableDraftState.value = null
        stageNote = "Continue from the unfinished take. Tap Sing when you are ready."
        uiState.value = uiState.value?.copy(stageNote = stageNote)
    }

    fun discardDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            deleteDraftFile()
            recoverableDraftState.value = null
        }
    }

    private fun deleteDraftFile() {
        runCatching { File(File(projectsDir, sessionId), SessionLayout.draftWav()).delete() }
    }

    fun pauseTake() = audio.pauseTake()

    fun resumeTake() = audio.resumeTake()

    fun retryTake() {
        viewModelScope.launch {
            pendingKeepState.value = null
            audio.recover()
            stageNote = "Ready again. Tap Sing when you want another pass."
            refresh()
        }
    }

    fun resetStage() {
        viewModelScope.launch {
            if (audio.state.value.status.isLive()) {
                audio.stopTake()
            }
            pendingKeepState.value = null
            punchStartMs = 0L
            punchEndMs = 0L
            audio.recover()
            stageNote = null
            uiState.value = uiState.value?.copy(punchStartMs = 0L, punchEndMs = 0L, stageNote = null)
        }
    }

    fun seekReady(positionMs: Long) {
        if (audio.state.value.status == EngineStatus.COUNTING) return
        punchStartMs = positionMs.coerceAtLeast(0L)
        audio.seekTake(punchStartMs)
        uiState.value = uiState.value?.copy(punchStartMs = punchStartMs)
    }

    fun seekToLyricLine(index: Int) {
        val current = uiState.value ?: return
        if (index !in current.lines.indices) return
        if (audio.state.value.status == EngineStatus.COUNTING) return
        val positionMs = LyricSync.seekMs(
            current.lines,
            index,
            current.backing?.durationMs ?: 0L,
        )
        activeLine = index
        lyricFollow = LiveLyricFollower.State(lineIndex = index)
        punchStartMs = positionMs
        audio.seekTake(positionMs)
        uiState.value = current.copy(
            activeLine = index,
            punchStartMs = positionMs,
            lyricHighlight = LyricSync.highlight(current.lines, positionMs).let { highlight ->
                if (highlight.lineIndex >= 0) highlight else highlight.copy(lineIndex = index)
            },
        )
    }

    fun followSungLyric(dtMs: Long) {
        val current = uiState.value ?: return
        if (current.lines.isEmpty()) return
        if (current.lines.any { it.startMs != null }) return
        val metrics = audio.state.value.metrics
        val voiced = LiveLyricFollower.isVoiced(metrics.pitchHz, metrics.rmsDb, metrics.silentFrames)
        val language = LyricLanguage.resolve(current.lyricLanguage, current.rawLyrics)
        val next = LiveLyricFollower.step(
            previous = lyricFollow,
            lineCount = current.lines.size,
            currentLineText = current.lines.getOrNull(lyricFollow.lineIndex)?.text.orEmpty(),
            voiced = voiced,
            dtMs = dtMs,
            language = language,
            heardText = heardLyric,
            allLines = current.lines.map { it.text },
        )
        heardLyric = null
        lyricFollow = next
        if (next.lineIndex != current.activeLine) {
            activeLine = next.lineIndex
            uiState.value = current.copy(activeLine = next.lineIndex)
        }
    }

    fun syncTimedLyric(elapsedMs: Long) {
        val current = uiState.value ?: return
        if (current.lines.none { it.startMs != null }) return
        val positionMs = if (audio.state.value.positionMs > 0L) audio.state.value.positionMs else elapsedMs
        val highlight = LyricSync.highlight(current.lines, positionMs)
        val timedIndex = highlight.lineIndex.coerceAtLeast(0)
        if (timedIndex != current.activeLine || highlight != current.lyricHighlight) {
            activeLine = timedIndex
            uiState.value = current.copy(activeLine = timedIndex, lyricHighlight = highlight)
        }
    }

    fun selectPart(partId: String) {
        val current = uiState.value ?: return
        selectedPartId = partId
        uiState.value = current.copy(selectedPartId = partId)
    }

    fun addSecondVoice() {
        addLayer(TrackRole.HARMONY)
    }

    fun addLayer(role: TrackRole) {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val count = document.tracks.count { it.role == role }
            val name = when (role) {
                TrackRole.DOUBLE -> if (count == 0) "Double" else "Double ${count + 1}"
                TrackRole.HARMONY -> if (count == 0) "Harmony" else "Harmony ${count + 1}"
                TrackRole.ADLIB -> if (count == 0) "Ad-lib" else "Ad-lib ${count + 1}"
                TrackRole.EXTRA -> if (count == 0) "Extra" else "Extra ${count + 1}"
                else -> "Part ${document.tracks.count { it.role != TrackRole.BACKING } + 1}"
            }
            captureEdit("Add layer") {
                addVoicePart(sessionId, role, name)
            }
            val updated = loadSession(sessionId)
            selectedPartId = updated?.tracks?.lastOrNull { it.role == role }?.id ?: selectedPartId
            stageNote = "Recording into $name. Sing, then Mix & Save."
            refresh()
        }
    }

    fun setLayerChain(partId: String, preset: VocalPreset?) {
        viewModelScope.launch {
            captureEdit("Layer chain") {
                updateTrackChain?.invoke(sessionId, partId, preset?.let { VocalChain.preset(it) })
            }
            refresh()
        }
    }

    fun setLayerMix(partId: String, gainDb: Float? = null, pan: Float? = null, mute: Boolean? = null, solo: Boolean? = null) {
        val part = uiState.value?.parts?.firstOrNull { it.id == partId } ?: return
        viewModelScope.launch {
            captureEdit("Layer mix") {
                updateTrackMix?.invoke(
                    sessionId,
                    partId,
                    gainDb ?: part.gainDb,
                    mute ?: part.mute,
                    solo = solo ?: part.solo,
                    pan = pan ?: part.pan,
                )
            }
            refresh()
        }
    }

    suspend fun ensureVoiceParts(minimum: Int) {
        repeat(3) {
            val document = loadSession(sessionId) ?: return
            val voices = document.tracks.count { it.role != TrackRole.BACKING }
            if (voices >= minimum.coerceAtMost(5)) {
                refresh()
                return
            }
            val (role, name) = when (voices) {
                1 -> TrackRole.DOUBLE to "Double"
                2 -> TrackRole.HARMONY to "Harmony"
                else -> TrackRole.ADLIB to "Ad-lib"
            }
            addVoicePart(sessionId, role, name)
        }
        refresh()
    }

    fun prepareSingMode(
        mode: SingMode,
        onNeedSongFromPhone: () -> Unit,
        onNeedLyrics: () -> Unit = {},
        onReadyToRecord: () -> Unit,
    ) {
        viewModelScope.launch {
            when (mode) {
                SingMode.SOLO -> {
                    val first = uiState.value?.parts?.firstOrNull()?.id
                    if (first != null) selectPart(first)
                    stageNote = "Solo. Sing your part, then Mix & Save."
                    uiState.value = uiState.value?.copy(stageNote = stageNote)
                    onReadyToRecord()
                }
                SingMode.DUET -> {
                    ensureVoiceParts(2)
                    val parts = uiState.value?.parts.orEmpty()
                    parts.getOrNull(1)?.id?.let(::selectPart)
                    stageNote = "Duet · Part 2 selected. A friend can take the other part from a pack."
                    uiState.value = uiState.value?.copy(stageNote = stageNote)
                    onReadyToRecord()
                }
                SingMode.GROUP -> {
                    ensureVoiceParts(3)
                    uiState.value?.parts?.lastOrNull()?.id?.let(::selectPart)
                    stageNote = "Group on this phone. Take turns on each part. Not a live room."
                    uiState.value = uiState.value?.copy(stageNote = stageNote)
                    onReadyToRecord()
                }
                SingMode.FROM_PHONE -> {
                    if (uiState.value?.backing == null) {
                        stageNote = "Pick a song you can use from this phone, then sing over it."
                        uiState.value = uiState.value?.copy(stageNote = stageNote)
                        onNeedSongFromPhone()
                    } else {
                        uiState.value?.parts?.firstOrNull()?.id?.let(::selectPart)
                        stageNote = "Sing over the song from this phone, then Mix & Save."
                        uiState.value = uiState.value?.copy(stageNote = stageNote)
                        onReadyToRecord()
                    }
                }
                SingMode.LYRICS -> {
                    stageNote = "Paste or write the lines, then tap Sing to record."
                    uiState.value = uiState.value?.copy(stageNote = stageNote)
                    onNeedLyrics()
                }
            }
        }
    }

    fun keepPendingTake(then: (() -> Unit)? = null) {
        viewModelScope.launch {
            val pending = pendingKeepState.value ?: return@launch
            val current = uiState.value ?: return@launch
            val videoPath = SessionLayout.performanceVideo(pending.finished.takeId)
            val videoFile = File(File(projectsDir, sessionId), videoPath)
            val take = if (videoFile.isFile) {
                pending.finished.copy(videoRelativePath = videoPath)
            } else {
                pending.finished
            }
            captureEdit(if (pending.punchStartMs > 0L) "Punch-in" else "Keep take") {
                saveTake(
                    sessionId = sessionId,
                    trackId = current.selectedPartId,
                    take = take,
                    timelineStartMs = pending.punchStartMs,
                    punchEndMs = pending.punchEndMs,
                    backingRelativePath = current.backing?.relativePath,
                    title = current.title,
                )
            }
            analytics.log(
                AnalyticsEvent.RECORDING_SAVED,
                mapOf(
                    "has_title" to current.title.isNotBlank().toString(),
                    "duration_ms" to take.durationMs.toString(),
                ),
            )
            val punchNote = if (pending.punchStartMs > 0L) {
                if (pending.punchEndMs > pending.punchStartMs) {
                    " That window was replaced. The rest of the take stayed."
                } else {
                    " First half stayed in the mix."
                }
            } else {
                ""
            }
            val cameraNote = if (current.kind == SessionKind.VIDEO && take.videoRelativePath == null) {
                " Camera did not save this pass."
            } else {
                ""
            }
            stageNote = "Saved take (${formatDuration(take.durationMs)}).$punchNote$cameraNote Mix & Save is next."
            punchStartMs = 0L
            punchEndMs = 0L
            pendingKeepState.value = null
            refresh()
            then?.invoke()
        }
    }

    fun selectRoute(route: GearRoute) {
        viewModelScope.launch {
            updateGear(sessionId, route)
            stageNote = null
            refresh()
        }
    }

    fun setMusical(
        keyName: String? = uiState.value?.keyName,
        tempoBpm: Float? = uiState.value?.tempoBpm,
        keyShiftSemitones: Int? = uiState.value?.keyShiftSemitones,
        playbackRate: Float? = uiState.value?.playbackRate,
        countInEnabled: Boolean? = uiState.value?.countInEnabled,
        metronomeEnabled: Boolean? = uiState.value?.metronomeEnabled,
        lyricLanguage: String? = uiState.value?.lyricLanguage,
        scaleMode: ScaleMode? = uiState.value?.scaleMode,
    ) {
        val current = uiState.value ?: return
        viewModelScope.launch {
            updateMusical(
                sessionId,
                MusicalSettings(
                    tempoBpm = (tempoBpm ?: current.tempoBpm).toDouble(),
                    keyName = keyName ?: current.keyName,
                    mode = scaleMode ?: current.scaleMode,
                    keyShiftSemitones = keyShiftSemitones ?: current.keyShiftSemitones,
                    playbackRate = playbackRate ?: current.playbackRate,
                    countInEnabled = countInEnabled ?: current.countInEnabled,
                    metronomeEnabled = metronomeEnabled ?: current.metronomeEnabled,
                    lyricLanguage = lyricLanguage ?: current.lyricLanguage,
                ),
            )
            stageNote = null
            refresh()
            scheduleBackingProcess(
                keyShiftSemitones = keyShiftSemitones ?: current.keyShiftSemitones,
                playbackRate = playbackRate ?: current.playbackRate,
            )
        }
    }

    private fun scheduleBackingProcess(keyShiftSemitones: Int, playbackRate: Float) {
        processJob?.cancel()
        processJob = viewModelScope.launch {
            delay(400)
            prepareBackingCache(keyShiftSemitones, playbackRate)
        }
    }

    private suspend fun prepareBackingCache(keyShiftSemitones: Int, playbackRate: Float): String? {
        val backing = uiState.value?.backing ?: return null
        val config = TrackProcessingConfig(keyShiftSemitones, playbackRate)
        if (config.isIdentity()) {
            backingPrepState.value = BackingPrepUi()
            return backing.relativePath
        }
        val original = File(File(projectsDir, sessionId), backing.relativePath)
        if (!original.isFile) return backing.relativePath
        val relative = SessionLayout.processedBacking(backing.relativePath, config.semitones, config.tempo)
        val output = File(File(projectsDir, sessionId), relative)
        if (output.isFile && output.length() > 44L) {
            backingPrepState.value = BackingPrepUi()
            return relative
        }
        backingPrepState.value = BackingPrepUi(busy = true, progress = 0.05f)
        val ok = audio.prepareProcessedTrack(original.absolutePath, output.absolutePath, config) { progress ->
            backingPrepState.value = BackingPrepUi(busy = true, progress = progress)
        }
        backingPrepState.value = BackingPrepUi()
        return if (ok) relative else backing.relativePath
    }

    private suspend fun prepareMonitorBed(current: StudioUi, backingRelative: String?): String? {
        val document = loadSession(sessionId) ?: return backingRelative
        val exclude = current.selectedPartId
        val others = document.tracks.any { track ->
            track.role != TrackRole.BACKING &&
                track.id != exclude &&
                !track.mute &&
                track.clips.any { it.active }
        }
        if (!others && punchStartMs <= 0L) return backingRelative
        val relative = SessionLayout.MONITOR_BED
        val destination = File(File(projectsDir, sessionId), relative)
        val aimed = document.copy(
            tracks = document.tracks.map { track ->
                if (track.id == exclude) track.copy(mute = true) else track
            },
        )
        return if (render.writeMonitorBed(aimed, destination)) relative else backingRelative
    }

    private fun armBacking(relativePath: String?, keyShiftSemitones: Int, playbackRate: Float): Triple<String?, Int, Float> {
        if (relativePath == null) return Triple(null, 0, 1f)
        val config = TrackProcessingConfig(keyShiftSemitones, playbackRate)
        if (config.isIdentity()) return Triple(relativePath, 0, 1f)
        val processed = SessionLayout.processedBacking(relativePath, config.semitones, config.tempo)
        val file = File(File(projectsDir, sessionId), processed)
        return if (file.isFile && file.length() > 44L) {
            Triple(processed, 0, 1f)
        } else {
            Triple(relativePath, keyShiftSemitones, playbackRate)
        }
    }

    fun trimTake(clipId: String, sourceInMs: Long, durationMs: Long) {
        viewModelScope.launch {
            captureEdit("Trim clip") {
                trimRecordedClip(sessionId, clipId, sourceInMs, durationMs)
            }
            stageNote = "Take trimmed. The mix will use only this window."
            refresh()
        }
    }

    fun moveTake(clipId: String, timelineStartMs: Long) {
        viewModelScope.launch {
            captureEdit("Move clip") {
                updateRecordedClip?.invoke(sessionId, clipId, timelineStartMs = timelineStartMs.coerceAtLeast(0L))
            }
            stageNote = "Clip moved on the timeline."
            refresh()
        }
    }

    fun setTakeFades(clipId: String, fadeInMs: Long, fadeOutMs: Long) {
        viewModelScope.launch {
            captureEdit("Clip fades") {
                updateRecordedClip?.invoke(sessionId, clipId, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs)
            }
            refresh()
        }
    }

    fun restoreTake(clipId: String) {
        viewModelScope.launch {
            captureEdit("Bring back clip") {
                updateRecordedClip?.invoke(sessionId, clipId, active = true, preferred = true)
            }
            stageNote = "Original take is back in the mix."
            refresh()
        }
    }

    fun splitTake(clipId: String, atSourceMs: Long) {
        viewModelScope.launch {
            captureEdit("Split clip") {
                splitRecordedClip?.invoke(sessionId, clipId, atSourceMs)
            }
            stageNote = "Clip split. Trim or punch each half separately."
            refresh()
        }
    }

    fun preferTake(clipId: String) {
        viewModelScope.launch {
            captureEdit("Choose take") {
                preferRecordedClip(sessionId, clipId, preferred = true)
            }
            stageNote = "This take is now the one used in Mix & Save."
            refresh()
        }
    }

    fun punchFrom(take: RecordedTakeUi, startMs: Long) {
        punchRegion(take, startMs, take.sourceInMs + take.durationMs)
    }

    fun punchRegion(take: RecordedTakeUi, startMs: Long, endMs: Long) {
        val fromSource = (startMs - take.sourceInMs).coerceAtLeast(0L)
        punchStartMs = (take.timelineStartMs + fromSource).coerceAtLeast(0L)
        val endSource = (endMs - take.sourceInMs).coerceAtLeast(fromSource + 250L)
        punchEndMs = (take.timelineStartMs + endSource).coerceAtLeast(punchStartMs + 250L)
        punchAutoStopped = false
        audio.seekTake(punchStartMs)
        stageNote = "Punch-in ${formatDuration(punchStartMs)}–${formatDuration(punchEndMs)}. Tap Sing to replace only that line."
        uiState.value = uiState.value?.copy(punchStartMs = punchStartMs, punchEndMs = punchEndMs, stageNote = stageNote)
    }

    fun clearPunch() {
        punchStartMs = 0L
        punchEndMs = 0L
        audio.seekTake(0L)
        uiState.value = uiState.value?.copy(punchStartMs = 0L, punchEndMs = 0L)
    }

    fun checkPunchEnd(positionMs: Long) {
        if (punchAutoStopped) return
        if (punchEndMs <= punchStartMs) return
        if (!audio.state.value.status.isLive()) return
        if (positionMs >= punchEndMs) {
            punchAutoStopped = true
            toggleRecording()
        }
    }

    fun tickPractice() {
        val metrics = audio.state.value.metrics
        if (metrics.pitchHz < 70f) return
        liveAttempts += 1
        if (abs(metrics.centsOffset) <= 45) liveHits += 1
        if (liveLowHz <= 0f || metrics.pitchHz < liveLowHz) liveLowHz = metrics.pitchHz
        if (metrics.pitchHz > liveHighHz) liveHighHz = metrics.pitchHz
    }

    fun selectVideoTarget(target: PublishTarget) {
        viewModelScope.launch {
            updatePublish(sessionId, target)
            refresh()
        }
    }

    fun importBackingFile(
        fileName: String,
        copyToFile: (File) -> Boolean,
    ) {
        viewModelScope.launch {
            val cleanName = fileName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "backing.mp3" }
            val relativePath = SessionLayout.backingFile(cleanName)
            val destination = File(File(projectsDir, sessionId), relativePath)
            val imported = withContext(Dispatchers.IO) {
                destination.parentFile?.mkdirs()
                if (!copyToFile(destination)) return@withContext null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(destination.absolutePath)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 180_000L
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() }
                        ?: fileName
                    duration to title
                } catch (_: Throwable) {
                    180_000L to fileName
                } finally {
                    runCatching { retriever.release() }
                }
            }
            if (imported != null) {
                val (durationMs, title) = imported
                importBackingTrack(sessionId, relativePath, durationMs, title, rightsConfirmed = true)
                if (title.isNotBlank() && title != uiState.value?.title) {
                    renameSession(sessionId, title)
                }
                takeCatalog?.upsertTrack(
                    sessionId,
                    ImportedTrackRecord(
                        id = relativePath,
                        title = title,
                        durationMs = durationMs,
                        localRelativePath = relativePath,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                stageNote = "Backing track loaded ($title). Tap Music only to hear it without the original lyrics, then Sing."
                refresh()
            } else {
                stageNote = "Could not load that audio file."
                uiState.value = uiState.value?.copy(stageNote = stageNote)
            }
        }
    }

    fun makeInstrumental() {
        val backing = uiState.value?.backing ?: return
        if (instrumentalBusy || !backing.canMakeInstrumental) return
        instrumentalBusy = true
        viewModelScope.launch {
            try {
                audio.stopPlayback()
                stageNote = "Making music only… removing the sung lyrics from this song."
                uiState.value = uiState.value?.copy(stageNote = stageNote)
                val source = File(File(projectsDir, sessionId), backing.relativePath)
                if (!source.isFile) {
                    stageNote = "That song file is missing. Import the track again."
                    uiState.value = uiState.value?.copy(stageNote = stageNote)
                    return@launch
                }
                val clean = backing.name
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .ifBlank { "song" }
                val relativePath = SessionLayout.backingFile("Instrumental_$clean.wav")
                val destination = File(File(projectsDir, sessionId), relativePath)
                when (val result = render.writeInstrumental(source, destination)) {
                    is InstrumentalResult.Written -> {
                        val label = "Music only · ${backing.name}"
                        importBackingTrack(sessionId, relativePath, result.durationMs, label, rightsConfirmed = true)
                        stageNote = if (result.mostlyMono) {
                            "This file is mostly one channel, so some words may still be heard. Preview it, then Sing."
                        } else {
                            "Music only is ready — the sung lyrics are pulled off the track. Hear it, then add your voice."
                        }
                        refresh()
                    }
                    is InstrumentalResult.Failed -> {
                        stageNote = result.message
                        uiState.value = uiState.value?.copy(stageNote = stageNote)
                    }
                }
            } finally {
                instrumentalBusy = false
            }
        }
    }

    fun loadSampleBacking() {
        viewModelScope.launch {
            val fileName = "Swara_Drone_C_Sa_Pa.wav"
            val relativePath = SessionLayout.backingFile(fileName)
            val destination = File(File(projectsDir, sessionId), relativePath)
            val durationMs = 16_000L
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    destination.parentFile?.mkdirs()
                    writeSampleDroneWav(
                        destination,
                        sampleRate = 48_000,
                        durationSeconds = 16,
                        keyName = uiState.value?.keyName ?: "C",
                    )
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                val key = uiState.value?.keyName ?: "C"
                importBackingTrack(sessionId, relativePath, durationMs, "Tanpura Drone · $key (Sa)")
                stageNote = "Sample Tanpura backing loaded in $key. Tap Preview or hit Sing."
                refresh()
            }
        }
    }

    fun playGuideMelody() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            val relativePath = SessionLayout.backingFile("Guide_Melody.wav")
            val destination = File(File(projectsDir, sessionId), relativePath)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    destination.parentFile?.mkdirs()
                    writeGuideMelodyWav(
                        destination,
                        sampleRate = 48_000,
                        keyName = current.keyName,
                        mode = current.scaleMode,
                    )
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                if (audio.state.value.playingRelativePath == relativePath) {
                    audio.stopPlayback()
                } else {
                    audio.playClip(sessionId, relativePath, loop = true)
                }
                stageNote = "Guide melody in ${current.keyName} ${current.scaleMode.label()}."
                uiState.value = current.copy(stageNote = stageNote)
            }
        }
    }

    fun loadSampleLyrics() {
        val sampleLrc = """
            [00:00.00] Sa Re Ga Ma Pa Dha Ni Sa — hold each note steady
            [00:04.00] Breathe in deep and let the melody rise
            [00:08.00] Sing from the chest with warm studio tone
            [00:12.00] Hold the final note and let the hall ring out
        """.trimIndent()
        saveLyrics(sampleLrc, note = "Sample synced lyrics loaded. Tap Sing to try them out.")
    }

    fun togglePracticeLoop() {
        val on = !practiceLoopingState.value
        practiceLoopingState.value = on
        audio.setLoopRegion(practiceLoopState.value, on)
    }

    fun setPracticeLoopPoint() {
        val pos = audio.state.value.positionMs.coerceAtLeast(0L)
        val mark = practiceLoopMark
        val region = if (mark == null) {
            practiceLoopMark = pos
            LoopRegion(startMs = pos, endMs = pos + 12_000L)
        } else {
            val start = minOf(mark, pos)
            val end = maxOf(mark, pos).coerceAtLeast(start + 1_000L)
            practiceLoopMark = null
            LoopRegion(startMs = start, endMs = end)
        }
        practiceLoopState.value = region
        practiceLoopingState.value = true
        audio.setLoopRegion(region, true)
    }

    fun exitPractice() {
        viewModelScope.launch {
            if (audio.state.value.status.isLive()) {
                audio.stopTake()
            }
            pendingKeepState.value = null
            audio.setLoopRegion(null, false)
            stageNote = "Practice ended. No score on this pass."
            refresh()
        }
    }

    fun toggleRecording() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (audio.state.value.status.isLive()) {
                val finished = audio.stopTake()
                if (current.kind == SessionKind.PRACTICE) {
                    if (liveAttempts > 0) {
                        val merged = PracticeStats(
                            hits = current.practice.hits + liveHits,
                            attempts = current.practice.attempts + liveAttempts,
                            lowestHz = listOf(current.practice.lowestHz, liveLowHz).filter { it > 0f }.minOrNull() ?: 0f,
                            highestHz = maxOf(current.practice.highestHz, liveHighHz),
                            lastScore = ((liveHits * 100f) / liveAttempts).toInt().coerceIn(0, 100),
                        )
                        updatePractice(sessionId, merged)
                        liveHits = 0
                        liveAttempts = 0
                    }
                    pendingKeepState.value = null
                    stageNote = "Practice pass saved locally as dry audio only — no score."
                    refresh()
                    return@launch
                }
                if (finished != null) {
                    val wav = File(File(projectsDir, sessionId), finished.relativePath)
                    val silent = finished.peak < 0.012f || finished.durationMs < 400L
                    pendingKeepState.value = PendingKeepTake(
                        finished = finished.copy(aacRelativePath = SessionLayout.takeAac(finished.takeId)),
                        punchStartMs = punchStartMs,
                        punchEndMs = punchEndMs,
                        silent = silent,
                        analyzing = true,
                    )
                    val detailed = withContext(Dispatchers.IO) {
                        PitchScorer.detailed(wav, current.lines, punchStartMs)
                    }
                    val score = detailed.overall
                    analytics.log(
                        AnalyticsEvent.KARAOKE_COMPLETED,
                        mapOf(
                            "duration_ms" to finished.durationMs.toString(),
                            "score" to score.toInt().toString(),
                        ),
                    )
                    pendingKeepState.value = PendingKeepTake(
                        finished = finished.copy(score = score, aacRelativePath = SessionLayout.takeAac(finished.takeId)),
                        punchStartMs = punchStartMs,
                        punchEndMs = punchEndMs,
                        silent = silent,
                        score = score,
                        detailed = detailed,
                        analyzing = false,
                    )
                    stageNote = if (finished.peak < 0.012f) {
                        "That pass was almost silent. Keep it only if you meant to."
                    } else {
                        "Keep this take, or discard and sing again. Score ${score.toInt()}."
                    }
                } else {
                    stageNote = "Take stopped."
                }
                refresh()
            } else {
                if (recoverableDraftState.value != null) return@launch
                liveHits = 0
                liveAttempts = 0
                liveLowHz = 0f
                liveHighHz = 0f
                prepareBackingCache(current.keyShiftSemitones, current.playbackRate)
                val bed = armBacking(
                    current.backing?.relativePath,
                    current.keyShiftSemitones,
                    current.playbackRate,
                )
                val monitor = prepareMonitorBed(current, bed.first)
                val mixedBed = monitor == SessionLayout.MONITOR_BED
                audio.arm(
                    ArmRequest(
                        sessionId = sessionId,
                        output = current.route,
                        backingRelativePath = monitor,
                        rootKeyName = current.keyName,
                        tempoBpm = current.tempoBpm.toDouble(),
                        countInEnabled = if (punchStartMs > 0L || current.kind == SessionKind.PRACTICE) {
                            false
                        } else {
                            current.countInEnabled
                        },
                        metronomeEnabled = current.metronomeEnabled,
                        punchStartMs = punchStartMs,
                        punchEndMs = punchEndMs,
                        preRollMs = if (punchStartMs > 0L) 2_000L else 0L,
                        playbackRate = if (mixedBed) 1f else bed.third,
                        keyShiftSemitones = if (mixedBed) 0 else bed.second,
                        monitorEnabled = headphoneFx &&
                            (current.route == GearRoute.WIRED || current.route == GearRoute.USB),
                        monitorReverb = if (headphoneFx) current.monitorReverb else 0f,
                        monitorDelay = if (headphoneFx) current.monitorDelay else 0f,
                        monitorPreset = if (headphoneFx) current.preset else null,
                        loopBacking = practiceLoopingState.value || current.backing?.loops == true,
                        voiceGain = voiceGain,
                        trackGain = if (current.kind == SessionKind.PRACTICE) minOf(trackGain, 0.5f) else trackGain,
                    ),
                )
                val armed = audio.state.value.decision
                stageNote = when (audio.startTake()) {
                    is TakeControl.Started -> {
                        analytics.log(AnalyticsEvent.KARAOKE_STARTED, mapOf("kind" to current.kind.name))
                        val wet = headphoneFx &&
                            (current.route == GearRoute.WIRED || current.route == GearRoute.USB)
                        audio.applyVocalChainMonitor(
                            if (wet) current.vocalChain else current.vocalChain.copy(reverb = 0f, delay = 0f),
                            voiceGain,
                            if (current.kind == SessionKind.PRACTICE) minOf(trackGain, 0.5f) else trackGain,
                        )
                        if (liveEffectState.value !is LiveMonitorEffect.None) {
                            audio.setLiveEffect(liveEffectState.value)
                        }
                        if (current.kind == SessionKind.PRACTICE) {
                            audio.setLoopRegion(practiceLoopState.value, practiceLoopingState.value)
                            audio.setTrackGain(minOf(trackGain, 0.5f))
                        }
                        beginLyricFollow()
                        val followsVoice = current.lines.isNotEmpty() && current.lines.none { it.startMs != null }
                        val bluetooth = if (armed == MonitorDecision.WARN) {
                            " Bluetooth can make you hear late."
                        } else {
                            ""
                        }
                        when {
                            punchStartMs > 0L && punchEndMs > punchStartMs ->
                                "Hear the song for 2 seconds, then sing ${formatDuration(punchStartMs)}–${formatDuration(punchEndMs)}.$bluetooth"
                            punchStartMs > 0L ->
                                "Hear the song for 2 seconds, then sing from ${formatDuration(punchStartMs)}.$bluetooth"
                            current.countInEnabled && followsVoice ->
                                "Count-in started. After 4, lyrics will follow the lines you sing.$bluetooth"
                            current.countInEnabled ->
                                "Count-in started. Sing after 4.$bluetooth"
                            followsVoice ->
                                "Recording. Lyrics will follow each line as you sing.$bluetooth"
                            else ->
                                "Recording live. Sing into the mic and tap Stop when done.$bluetooth"
                        }
                    }
                    is TakeControl.Refused ->
                        "Switch to Headphones top-right so the backing track does not bleed into the mic."
                    TakeControl.TransportUnavailable ->
                        "Could not open the microphone on this device."
                }
                uiState.value = current.copy(stageNote = stageNote)
            }
        }
    }

    fun deleteTake(clipId: String) {
        viewModelScope.launch {
            val current = uiState.value
            val take = current?.takes?.firstOrNull { it.id == clipId }
            if (take != null && audio.state.value.playingRelativePath == take.relativePath) {
                audio.stopPlayback()
            }
            captureEdit("Delete clip") {
                deleteRecordedClip(sessionId, clipId)
            }
            stageNote = "Clip parked. The WAV stays until you clean unused takes."
            refresh()
        }
    }

    fun undoEdit() {
        viewModelScope.launch {
            undoManager.undo()
            refresh()
        }
    }

    fun redoEdit() {
        viewModelScope.launch {
            undoManager.redo()
            refresh()
        }
    }

    fun cleanUnusedTakes() {
        viewModelScope.launch {
            val cleaner = cleanUnusedTakes ?: return@launch
            val result = cleaner(sessionId)
            undoManager.clear()
            stageNote = if (result.removedFiles > 0 || result.removedClips > 0) {
                "Removed ${result.removedClips} parked clips and ${result.removedFiles} unused files."
            } else {
                "No unused takes to clean."
            }
            refresh()
        }
    }

    fun refreshFromLifecycle() {
        refresh()
    }

    fun seekMix(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        audio.seekTake(clamped)
        schedulePlayhead(clamped)
    }

    fun stopMixPlayback() {
        audio.stopPlayback()
        schedulePlayhead(audio.state.value.positionMs)
    }

    fun flushAutoSave() {
        playheadJob?.cancel()
        viewModelScope.launch {
            sessionAutoSave?.persistPlayhead(sessionId, audio.state.value.positionMs)
        }
    }

    private fun schedulePlayhead(positionMs: Long) {
        playheadJob?.cancel()
        playheadJob = viewModelScope.launch {
            delay(800)
            sessionAutoSave?.persistPlayhead(sessionId, positionMs)
        }
    }

    fun onPermissionDenied(message: String) {
        val current = uiState.value ?: return
        stageNote = message
        uiState.value = current.copy(stageNote = stageNote)
    }

    fun togglePlayClip(relativePath: String) {
        viewModelScope.launch {
            val backing = uiState.value?.backing
            val loop = (backing?.relativePath == relativePath && backing.loops) ||
                relativePath.endsWith("Guide_Melody.wav")
            audio.playClip(sessionId, relativePath, loop)
        }
    }

    fun toggleCompareTake(current: RecordedTakeUi, other: RecordedTakeUi) {
        val playing = audio.state.value.playingRelativePath
        if (playing == other.relativePath) {
            togglePlayTake(current)
        } else {
            togglePlayTake(other)
        }
    }

    fun togglePlayTake(take: RecordedTakeUi) {
        viewModelScope.launch {
            val backing = uiState.value?.backing
            audio.playWithBacking(
                sessionId = sessionId,
                vocalRelativePath = take.relativePath,
                vocalStartMs = take.sourceInMs,
                vocalDurationMs = take.durationMs,
                backingRelativePath = backing?.relativePath,
                backingStartMs = take.timelineStartMs,
                loopBacking = backing?.loops == true,
            )
        }
    }

    fun hearPreferredWithTrack() {
        viewModelScope.launch {
            stageNote = "Mixing this take…"
            refresh()
            val heard = hearMix(maxDurationMs = 0L, loop = false)
            if (!heard) {
                val take = uiState.value?.takes?.firstOrNull { it.preferred }
                    ?: uiState.value?.takes?.firstOrNull()
                    ?: return@launch
                togglePlayTake(take)
            }
        }
    }

    private suspend fun hearMix(maxDurationMs: Long, loop: Boolean): Boolean {
        val document = loadSession(sessionId) ?: return false
        val preview = render.preview(document, maxDurationMs) ?: return false
        audio.playPcm(sessionId, preview.left, preview.right, preview.sampleRate, loop)
        stageNote = "This is ${document.chain.preset.mixHearLabel()}."
        refresh()
        return true
    }

    fun retryPendingTake() {
        viewModelScope.launch {
            val pending = pendingKeepState.value ?: return@launch
            val start = pending.punchStartMs
            val end = pending.punchEndMs
            withContext(Dispatchers.IO) {
                runCatching { File(File(projectsDir, sessionId), pending.finished.relativePath).delete() }
            }
            pendingKeepState.value = null
            punchStartMs = start
            punchEndMs = end
            punchAutoStopped = false
            stageNote = if (end > start) {
                "Retry punch ${formatDuration(start)}–${formatDuration(end)}."
            } else {
                "Sing again."
            }
            uiState.value = uiState.value?.copy(
                punchStartMs = start,
                punchEndMs = end,
                stageNote = stageNote,
            )
            toggleRecording()
        }
    }

    fun discardPendingTake() {
        viewModelScope.launch {
            val pending = pendingKeepState.value ?: return@launch
            withContext(Dispatchers.IO) {
                runCatching { File(File(projectsDir, sessionId), pending.finished.relativePath).delete() }
                runCatching {
                    File(File(projectsDir, sessionId), SessionLayout.performanceVideo(pending.finished.takeId)).delete()
                }
            }
            pendingKeepState.value = null
            deleteDraftFile()
            stageNote = "Take discarded. Tap Sing when you are ready."
            uiState.value = uiState.value?.copy(stageNote = stageNote)
        }
    }

    fun keepLiveAndLeave(onLeft: () -> Unit) {
        viewModelScope.launch {
            val current = uiState.value
            val finished = if (
                audio.state.value.status.isLive()
            ) {
                audio.stopTake()
            } else {
                pendingKeepState.value?.finished
            }
            if (finished != null && current != null) {
                val punch = pendingKeepState.value?.punchStartMs ?: punchStartMs
                val punchEnd = pendingKeepState.value?.punchEndMs ?: punchEndMs
                val videoPath = SessionLayout.performanceVideo(finished.takeId)
                val videoFile = File(File(projectsDir, sessionId), videoPath)
                saveTake(
                    sessionId = sessionId,
                    trackId = current.selectedPartId,
                    take = if (videoFile.isFile) finished.copy(videoRelativePath = videoPath) else finished,
                    timelineStartMs = punch,
                    punchEndMs = punchEnd,
                    backingRelativePath = current.backing?.relativePath,
                    title = current.title,
                )
            }
            pendingKeepState.value = null
            punchStartMs = 0L
            punchEndMs = 0L
            audio.disarm()
            onLeft()
        }
    }

    fun discardLiveAndLeave(onLeft: () -> Unit) {
        viewModelScope.launch {
            val finished = if (
                audio.state.value.status.isLive()
            ) {
                audio.stopTake()
            } else {
                pendingKeepState.value?.finished
            }
            if (finished != null) {
                withContext(Dispatchers.IO) {
                    runCatching { File(File(projectsDir, sessionId), finished.relativePath).delete() }
                    runCatching {
                        File(File(projectsDir, sessionId), SessionLayout.performanceVideo(finished.takeId)).delete()
                    }
                }
            }
            pendingKeepState.value = null
            deleteDraftFile()
            audio.disarm()
            onLeft()
        }
    }

    fun onCaptureFailed() {
        stageNote = audio.state.value.lastError?.userMessage()
            ?: "Could not open the microphone on this device."
        uiState.value = uiState.value?.copy(stageNote = stageNote)
    }

    fun onEngineError(message: String) {
        stageNote = message
        uiState.value = uiState.value?.copy(stageNote = stageNote)
    }

    override fun onCleared() {
        sessionAutoSave?.rememberOpened(sessionId, audio.state.value.positionMs)
        super.onCleared()
    }

    fun stopAudioOnExit() {
        sessionAutoSave?.rememberOpened(sessionId, audio.state.value.positionMs)
        val live = audio.state.value.status.isLive()
        if (!live) {
            audio.disarm()
            return
        }
        viewModelScope.launch {
            val current = uiState.value
            val finished = audio.stopTake()
            if (finished != null && current != null) {
                commitRecordedTake(
                    sessionId,
                    current.selectedPartId,
                    finished,
                    timelineStartMs = punchStartMs,
                    punchEndMs = punchEndMs,
                )
            }
            audio.disarm()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            if (!restoredPlayhead) {
                restoredPlayhead = true
                val playhead = document.playheadMs.takeIf { it > 0L }
                    ?: sessionAutoSave?.lastPlayheadMs()?.takeIf { it > 0L }
                    ?: 0L
                sessionAutoSave?.rememberOpened(sessionId, playhead)
                if (playhead > 0L) audio.seekTake(playhead)
            }
            val route = document.gear.output.takeUnless { it == GearRoute.UNKNOWN } ?: GearRoute.WIRED
            if (document.gear.output == GearRoute.UNKNOWN) {
                updateGear(sessionId, GearRoute.WIRED)
            }
            val backingTrack = document.tracks.firstOrNull { it.role == TrackRole.BACKING }
            val backingClip = backingTrack?.clips?.lastOrNull()
            val backingUi = backingClip?.let { clip ->
                val displayName = document.rights?.note?.takeIf { it.isNotBlank() }
                    ?: clip.sourceRelativePath.substringAfterLast('/')
                BackingTrackUi(
                    name = displayName,
                    durationText = formatDuration(clip.durationMs),
                    relativePath = clip.sourceRelativePath,
                    durationMs = clip.durationMs,
                    loops = BackingPlayback.shouldLoop(clip.sourceRelativePath, clip.durationMs),
                    canMakeInstrumental = !BackingPlayback.shouldLoop(clip.sourceRelativePath, clip.durationMs) &&
                        !isMusicOnlyBacking(displayName, clip.sourceRelativePath),
                    isInstrumental = isMusicOnlyBacking(displayName, clip.sourceRelativePath),
                )
            }
            val bed = armBacking(
                backingClip?.sourceRelativePath,
                document.musical.keyShiftSemitones,
                document.musical.playbackRate,
            )
            audio.arm(
                ArmRequest(
                    sessionId = document.id,
                    output = route,
                    backingRelativePath = bed.first,
                    rootKeyName = document.musical.keyName ?: "C",
                    tempoBpm = document.musical.tempoBpm ?: 80.0,
                    countInEnabled = document.musical.countInEnabled,
                    metronomeEnabled = document.musical.metronomeEnabled,
                    punchStartMs = punchStartMs,
                    punchEndMs = punchEndMs,
                    playbackRate = bed.third,
                    keyShiftSemitones = bed.second,
                ),
            )
            val lines = document.lyrics.lines
            val voiceTracks = document.tracks.filter { it.role != TrackRole.BACKING }
            val parts = voiceTracks.map { track ->
                VoicePartUi(
                    id = track.id,
                    label = track.name.ifBlank { track.role.laneLabel() },
                    role = track.role,
                    gainDb = track.gainDb,
                    pan = track.pan,
                    mute = track.mute,
                    solo = track.solo,
                    clipCount = track.clips.count { it.active },
                    chainPreset = track.chain?.preset,
                )
            }
            val activePartId = selectedPartId?.takeIf { id -> parts.any { it.id == id } }
                ?: parts.firstOrNull()?.id
            selectedPartId = activePartId
            val takes = voiceTracks.flatMap { track ->
                track.clips.map { clip ->
                    RecordedTakeUi(
                        id = clip.id,
                        label = clip.label.ifBlank { track.name },
                        durationText = buildString {
                            append(formatDuration(clip.durationMs))
                            if (clip.timelineStartMs > 0L) append(" · from ${formatDuration(clip.timelineStartMs)}")
                            if (clip.preferred && clip.active) append(" · this take")
                            if (!clip.active) append(" · parked")
                        },
                        relativePath = clip.sourceRelativePath,
                        sourceInMs = clip.sourceInMs,
                        durationMs = clip.durationMs,
                        sourceDurationMs = clip.usableSourceDurationMs,
                        timelineStartMs = clip.timelineStartMs,
                        preferred = clip.preferred,
                        active = clip.active,
                        fadeInMs = clip.fadeInMs,
                        fadeOutMs = clip.fadeOutMs,
                    )
                }
            }.reversed()
            val clampedLine = activeLine.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            activeLine = clampedLine
            uiState.value = StudioUi(
                title = document.title,
                kind = document.kind,
                preset = document.chain.preset,
                publishTarget = document.publish.target,
                backing = backingUi,
                lines = lines,
                rawLyrics = document.lyrics.rawText,
                activeLine = clampedLine,
                parts = parts,
                selectedPartId = activePartId,
                canAddPart = voiceTracks.size < 6,
                takes = takes,
                route = route,
                stageNote = stageNote,
                keyName = document.musical.keyName ?: "C",
                tempoBpm = (document.musical.tempoBpm ?: 80.0).toFloat(),
                keyShiftSemitones = document.musical.keyShiftSemitones,
                playbackRate = document.musical.playbackRate,
                countInEnabled = document.musical.countInEnabled,
                metronomeEnabled = document.musical.metronomeEnabled,
                punchStartMs = punchStartMs,
                punchEndMs = punchEndMs,
                practice = document.practice,
                lyricLanguage = document.musical.lyricLanguage,
                scaleMode = document.musical.mode ?: ScaleMode.MAJOR,
                headphoneFx = headphoneFx,
                heardLyric = heardLyric,
                monitorReverb = document.chain.reverb,
                monitorDelay = document.chain.delay,
                vocalChain = document.chain,
                voiceGain = voiceGain,
                trackGain = trackGain,
                lyricHighlight = uiState.value?.lyricHighlight ?: LyricHighlight(),
            )
        }
    }

    companion object {
        fun factory(
            sessionId: String,
            projectsDir: File,
            loadSession: LoadSession,
            renameSession: RenameSession,
            updateGear: UpdateGear,
            updateVocalPreset: UpdateVocalPreset,
            updateLyrics: UpdateLyrics,
            addVoicePart: AddVoicePart,
            commitRecordedTake: CommitRecordedTake,
            saveTake: SaveTake,
            deleteRecordedClip: DeleteRecordedClip,
            importBackingTrack: ImportBackingTrack,
            updatePublish: UpdatePublishSettings,
            updateMusical: UpdateMusicalSettings,
            trimRecordedClip: TrimRecordedClip,
            splitRecordedClip: SplitRecordedClip,
            updateRecordedClip: UpdateRecordedClip,
            updateTrackChain: UpdateTrackChain,
            updateTrackMix: UpdateTrackMix,
            preferRecordedClip: PreferRecordedClip,
            updatePractice: UpdatePracticeStats,
            restoreSession: RestoreSession,
            cleanUnusedTakes: CleanUnusedTakes? = null,
            sessionAutoSave: SessionAutoSave? = null,
            audio: AudioEngine,
            render: RenderEngine,
            takeCatalog: TakeCatalog? = null,
            takeConversion: TakeConversion? = null,
            analytics: Analytics = NoOpAnalytics,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StudioViewModel(
                    sessionId = sessionId,
                    projectsDir = projectsDir,
                    loadSession = loadSession,
                    renameSession = renameSession,
                    updateGear = updateGear,
                    updateVocalPreset = updateVocalPreset,
                    updateLyrics = updateLyrics,
                    addVoicePart = addVoicePart,
                    commitRecordedTake = commitRecordedTake,
                    saveTake = saveTake,
                    deleteRecordedClip = deleteRecordedClip,
                    importBackingTrack = importBackingTrack,
                    updatePublish = updatePublish,
                    updateMusical = updateMusical,
                    trimRecordedClip = trimRecordedClip,
                    splitRecordedClip = splitRecordedClip,
                    updateRecordedClip = updateRecordedClip,
                    updateTrackChain = updateTrackChain,
                    updateTrackMix = updateTrackMix,
                    preferRecordedClip = preferRecordedClip,
                    updatePractice = updatePractice,
                    restoreSession = restoreSession,
                    cleanUnusedTakes = cleanUnusedTakes,
                    sessionAutoSave = sessionAutoSave,
                    audio = audio,
                    render = render,
                    takeCatalog = takeCatalog,
                    takeConversion = takeConversion,
                    analytics = analytics,
                )
            }
        }
    }
}
