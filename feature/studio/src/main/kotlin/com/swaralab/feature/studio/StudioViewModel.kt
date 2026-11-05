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
import com.swaralab.domain.audio.PitchScorer
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
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.SaveTake
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.TrimRecordedClip
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
)

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
    val silent: Boolean,
    val score: Float? = null,
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
    val practice: PracticeStats,
    val lyricLanguage: String,
    val scaleMode: ScaleMode,
    val headphoneFx: Boolean,
    val heardLyric: String?,
    val monitorReverb: Float = 0f,
    val monitorDelay: Float = 0f,
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
    private val preferRecordedClip: PreferRecordedClip,
    private val updatePractice: UpdatePracticeStats,
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
    private var activeLine = 0
    private var selectedPartId: String? = null
    private var stageNote: String? = null
    private var punchStartMs: Long = 0L
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
            audio.recover()
            stageNote = null
            uiState.value = uiState.value?.copy(punchStartMs = 0L, stageNote = null)
        }
    }

    fun seekReady(positionMs: Long) {
        if (audio.state.value.status == EngineStatus.RECORDING ||
            audio.state.value.status == EngineStatus.COUNTING
        ) {
            return
        }
        punchStartMs = positionMs.coerceAtLeast(0L)
        audio.seekTake(punchStartMs)
        uiState.value = uiState.value?.copy(punchStartMs = punchStartMs)
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
        viewModelScope.launch {
            ensureVoiceParts(2)
        }
    }

    suspend fun ensureVoiceParts(minimum: Int) {
        repeat(3) {
            val document = loadSession(sessionId) ?: return
            val voices = document.tracks.count { it.role != TrackRole.BACKING }
            if (voices >= minimum.coerceAtMost(3)) {
                refresh()
                return
            }
            val (role, name) = when (voices) {
                1 -> TrackRole.HARMONY to "Part 2 · Duet"
                else -> TrackRole.DOUBLE to "Part 3 · Group"
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
            saveTake(
                sessionId = sessionId,
                trackId = current.selectedPartId,
                take = take,
                timelineStartMs = pending.punchStartMs,
                backingRelativePath = current.backing?.relativePath,
                title = current.title,
            )
            analytics.log(
                AnalyticsEvent.RECORDING_SAVED,
                mapOf(
                    "has_title" to current.title.isNotBlank().toString(),
                    "duration_ms" to take.durationMs.toString(),
                ),
            )
            val punchNote = if (pending.punchStartMs > 0L) {
                " First half stayed in the mix."
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
        }
    }

    fun trimTake(clipId: String, sourceInMs: Long, durationMs: Long) {
        viewModelScope.launch {
            trimRecordedClip(sessionId, clipId, sourceInMs, durationMs)
            stageNote = "Take trimmed. The mix will use only this window."
            refresh()
        }
    }

    fun preferTake(clipId: String) {
        viewModelScope.launch {
            preferRecordedClip(sessionId, clipId, preferred = true)
            stageNote = "This take is now the one used in Mix & Save."
            refresh()
        }
    }

    fun punchFrom(take: RecordedTakeUi, startMs: Long) {
        val fromSource = (startMs - take.sourceInMs).coerceAtLeast(0L)
        punchStartMs = (take.timelineStartMs + fromSource).coerceAtLeast(0L)
        audio.seekTake(punchStartMs)
        stageNote = "Punch-in armed at ${formatDuration(punchStartMs)}. The first half stays. Tap Sing to replace from there."
        uiState.value = uiState.value?.copy(punchStartMs = punchStartMs, stageNote = stageNote)
    }

    fun clearPunch() {
        punchStartMs = 0L
        audio.seekTake(0L)
        uiState.value = uiState.value?.copy(punchStartMs = 0L)
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

    fun toggleRecording() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (audio.state.value.status.isLive()) {
                val finished = audio.stopTake()
                if (finished != null) {
                    val wav = File(File(projectsDir, sessionId), finished.relativePath)
                    val score = withContext(Dispatchers.IO) { PitchScorer.score(wav, current.lines, punchStartMs) }
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
                        silent = finished.peak < 0.012f || finished.durationMs < 400L,
                        score = score,
                    )
                    stageNote = if (finished.peak < 0.012f) {
                        "That pass was almost silent. Keep it only if you meant to."
                    } else {
                        "Keep this take, or discard and sing again. Score ${score.toInt()}."
                    }
                } else {
                    stageNote = "Take stopped."
                }
                if (current.kind == SessionKind.PRACTICE && liveAttempts > 0) {
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
                refresh()
            } else {
                if (recoverableDraftState.value != null) return@launch
                liveHits = 0
                liveAttempts = 0
                liveLowHz = 0f
                liveHighHz = 0f
                audio.arm(
                    ArmRequest(
                        sessionId = sessionId,
                        output = current.route,
                        backingRelativePath = current.backing?.relativePath,
                        rootKeyName = current.keyName,
                        tempoBpm = current.tempoBpm.toDouble(),
                        countInEnabled = current.countInEnabled,
                        metronomeEnabled = current.metronomeEnabled,
                        punchStartMs = punchStartMs,
                        playbackRate = current.playbackRate,
                        keyShiftSemitones = current.keyShiftSemitones,
                        monitorEnabled = headphoneFx &&
                            (current.route == GearRoute.WIRED || current.route == GearRoute.USB),
                        monitorReverb = if (headphoneFx) current.monitorReverb else 0f,
                        monitorDelay = if (headphoneFx) current.monitorDelay else 0f,
                        monitorPreset = if (headphoneFx) current.preset else null,
                        loopBacking = current.backing?.loops == true,
                        voiceGain = voiceGain,
                        trackGain = trackGain,
                    ),
                )
                val armed = audio.state.value.decision
                stageNote = when (audio.startTake()) {
                    is TakeControl.Started -> {
                        analytics.log(AnalyticsEvent.KARAOKE_STARTED, mapOf("kind" to current.kind.name))
                        audio.setLiveEffect(liveEffectState.value)
                        beginLyricFollow()
                        val followsVoice = current.lines.isNotEmpty() && current.lines.none { it.startMs != null }
                        val bluetooth = if (armed == MonitorDecision.WARN) {
                            " Bluetooth can make you hear late."
                        } else {
                            ""
                        }
                        when {
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
            deleteRecordedClip(sessionId, clipId)
            if (take != null) {
                withContext(Dispatchers.IO) {
                    runCatching { File(File(projectsDir, sessionId), take.relativePath).delete() }
                }
            }
            refresh()
        }
    }

    fun refreshFromLifecycle() {
        refresh()
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
                val videoPath = SessionLayout.performanceVideo(finished.takeId)
                val videoFile = File(File(projectsDir, sessionId), videoPath)
                saveTake(
                    sessionId = sessionId,
                    trackId = current.selectedPartId,
                    take = if (videoFile.isFile) finished.copy(videoRelativePath = videoPath) else finished,
                    timelineStartMs = punch,
                    backingRelativePath = current.backing?.relativePath,
                    title = current.title,
                )
            }
            pendingKeepState.value = null
            punchStartMs = 0L
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

    fun stopAudioOnExit() {
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
                )
            }
            audio.disarm()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
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
            audio.arm(
                ArmRequest(
                    sessionId = document.id,
                    output = route,
                    backingRelativePath = backingClip?.sourceRelativePath,
                    rootKeyName = document.musical.keyName ?: "C",
                    tempoBpm = document.musical.tempoBpm ?: 80.0,
                    countInEnabled = document.musical.countInEnabled,
                    metronomeEnabled = document.musical.metronomeEnabled,
                    punchStartMs = punchStartMs,
                    playbackRate = document.musical.playbackRate,
                    keyShiftSemitones = document.musical.keyShiftSemitones,
                ),
            )
            val lines = document.lyrics.lines
            val voiceTracks = document.tracks.filter { it.role != TrackRole.BACKING }
            val parts = voiceTracks.mapIndexed { index, track ->
                VoicePartUi(
                    id = track.id,
                    label = if (index == 0) "Part 1 · Lead" else track.name,
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
                            if (clip.preferred) append(" · this take")
                        },
                        relativePath = clip.sourceRelativePath,
                        sourceInMs = clip.sourceInMs,
                        durationMs = clip.durationMs,
                        sourceDurationMs = clip.usableSourceDurationMs,
                        timelineStartMs = clip.timelineStartMs,
                        preferred = clip.preferred,
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
                canAddPart = voiceTracks.size < 3,
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
                practice = document.practice,
                lyricLanguage = document.musical.lyricLanguage,
                scaleMode = document.musical.mode ?: ScaleMode.MAJOR,
                headphoneFx = headphoneFx,
                heardLyric = heardLyric,
                monitorReverb = document.chain.reverb,
                monitorDelay = document.chain.delay,
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
            preferRecordedClip: PreferRecordedClip,
            updatePractice: UpdatePracticeStats,
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
                    preferRecordedClip = preferRecordedClip,
                    updatePractice = updatePractice,
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
