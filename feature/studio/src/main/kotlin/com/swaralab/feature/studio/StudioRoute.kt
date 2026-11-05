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
import com.swaralab.domain.audio.WavPeaks
import com.swaralab.domain.model.ScaleMode
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.PracticeStats
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.InstrumentalResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
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
    private val deleteRecordedClip: DeleteRecordedClip,
    private val importBackingTrack: ImportBackingTrack,
    private val updatePublish: UpdatePublishSettings,
    private val updateMusical: UpdateMusicalSettings,
    private val trimRecordedClip: TrimRecordedClip,
    private val preferRecordedClip: PreferRecordedClip,
    private val updatePractice: UpdatePracticeStats,
    val audio: AudioEngine,
    private val render: RenderEngine,
) : ViewModel() {
    private val uiState = MutableStateFlow<StudioUi?>(null)
    val ui: StateFlow<StudioUi?> = uiState.asStateFlow()
    private val pendingKeepState = MutableStateFlow<PendingKeepTake?>(null)
    val pendingKeep: StateFlow<PendingKeepTake?> = pendingKeepState.asStateFlow()
    private var activeLine = 0
    private var selectedPartId: String? = null
    private var stageNote: String? = null
    private var punchStartMs: Long = 0L
    private var instrumentalBusy = false
    private var lyricFollow = LiveLyricFollower.State()
    private var heardLyric: String? = null
    private var headphoneFx = true
    private var liveHits = 0
    private var liveAttempts = 0
    private var liveLowHz = 0f
    private var liveHighHz = 0f

    init {
        refresh()
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
        val timedIndex = current.lines.indexOfLast { line ->
            val start = line.startMs
            start != null && start <= elapsedMs
        }
        if (timedIndex >= 0 && timedIndex != current.activeLine) {
            activeLine = timedIndex
            uiState.value = current.copy(activeLine = timedIndex)
        }
    }

    fun selectPart(partId: String) {
        val current = uiState.value ?: return
        selectedPartId = partId
        uiState.value = current.copy(selectedPartId = partId)
    }

    fun addSecondVoice() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val voices = document.tracks.count { it.role != TrackRole.BACKING }
            val (role, name) = when (voices) {
                1 -> TrackRole.HARMONY to "Part 2 · Duet"
                else -> TrackRole.DOUBLE to "Part 3 · Harmony"
            }
            addVoicePart(sessionId, role, name)
            refresh()
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
        stageNote = "Punch-in armed at ${formatDuration(punchStartMs)}. The first half stays. Tap Sing to replace from there."
        uiState.value = uiState.value?.copy(punchStartMs = punchStartMs, stageNote = stageNote)
    }

    fun clearPunch() {
        punchStartMs = 0L
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
            val durationMs = withContext(Dispatchers.IO) {
                destination.parentFile?.mkdirs()
                if (!copyToFile(destination)) return@withContext null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(destination.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 180_000L
                } catch (_: Throwable) {
                    180_000L
                } finally {
                    runCatching { retriever.release() }
                }
            }
            if (durationMs != null) {
                importBackingTrack(sessionId, relativePath, durationMs, fileName, rightsConfirmed = true)
                stageNote = "Backing track loaded ($fileName). Tap Music only to hear it without the original lyrics, then Sing."
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
            if (audio.state.value.status == EngineStatus.RECORDING ||
                audio.state.value.status == EngineStatus.COUNTING
            ) {
                val finished = audio.stopTake()
                if (finished != null) {
                    pendingKeepState.value = PendingKeepTake(
                        finished = finished,
                        punchStartMs = punchStartMs,
                        silent = finished.peak < 0.012f || finished.durationMs < 400L,
                    )
                    stageNote = if (finished.peak < 0.012f) {
                        "That pass was almost silent. Keep it only if you meant to."
                    } else {
                        "Keep this take, or discard and sing again."
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
                        loopBacking = current.backing?.loops == true,
                    ),
                )
                val armed = audio.state.value.decision
                stageNote = when (audio.startTake()) {
                    is TakeControl.Started -> {
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

    fun keepPendingTake() {
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
            commitRecordedTake(
                sessionId,
                current.selectedPartId,
                take,
                timelineStartMs = pending.punchStartMs,
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
            stageNote = "Saved take (${formatDuration(take.durationMs)}).$punchNote$cameraNote Tap Hear with track."
            punchStartMs = 0L
            pendingKeepState.value = null
            refresh()
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
            stageNote = "Take discarded. Tap Sing when you are ready."
            uiState.value = uiState.value?.copy(stageNote = stageNote)
        }
    }

    fun keepLiveAndLeave(onLeft: () -> Unit) {
        viewModelScope.launch {
            val current = uiState.value
            val finished = if (
                audio.state.value.status == EngineStatus.RECORDING ||
                audio.state.value.status == EngineStatus.COUNTING
            ) {
                audio.stopTake()
            } else {
                pendingKeepState.value?.finished
            }
            if (finished != null && current != null) {
                val punch = pendingKeepState.value?.punchStartMs ?: punchStartMs
                val videoPath = SessionLayout.performanceVideo(finished.takeId)
                val videoFile = File(File(projectsDir, sessionId), videoPath)
                commitRecordedTake(
                    sessionId,
                    current.selectedPartId,
                    if (videoFile.isFile) finished.copy(videoRelativePath = videoPath) else finished,
                    timelineStartMs = punch,
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
                audio.state.value.status == EngineStatus.RECORDING ||
                audio.state.value.status == EngineStatus.COUNTING
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
            audio.disarm()
            onLeft()
        }
    }

    fun onCaptureFailed() {
        stageNote = "Could not open the microphone on this device."
        uiState.value = uiState.value?.copy(stageNote = stageNote)
    }

    fun stopAudioOnExit() {
        val live = audio.state.value.status == EngineStatus.RECORDING ||
            audio.state.value.status == EngineStatus.COUNTING
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
            deleteRecordedClip: DeleteRecordedClip,
            importBackingTrack: ImportBackingTrack,
            updatePublish: UpdatePublishSettings,
            updateMusical: UpdateMusicalSettings,
            trimRecordedClip: TrimRecordedClip,
            preferRecordedClip: PreferRecordedClip,
            updatePractice: UpdatePracticeStats,
            audio: AudioEngine,
            render: RenderEngine,
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
                    deleteRecordedClip = deleteRecordedClip,
                    importBackingTrack = importBackingTrack,
                    updatePublish = updatePublish,
                    updateMusical = updateMusical,
                    trimRecordedClip = trimRecordedClip,
                    preferRecordedClip = preferRecordedClip,
                    updatePractice = updatePractice,
                    audio = audio,
                    render = render,
                )
            }
        }
    }
}

private val studioPresets = listOf(
    VocalPreset.CLOSE,
    VocalPreset.STAGE,
    VocalPreset.RADIO,
    VocalPreset.RAW,
)

private val listeningRoutes = listOf(
    GearRoute.WIRED,
    GearRoute.USB,
    GearRoute.BLUETOOTH,
    GearRoute.SPEAKER,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudioRoute(
    sessionId: String,
    projectsDir: File,
    loadSession: LoadSession,
    renameSession: RenameSession,
    updateGear: UpdateGear,
    updateVocalPreset: UpdateVocalPreset,
    updateLyrics: UpdateLyrics,
    addVoicePart: AddVoicePart,
    commitRecordedTake: CommitRecordedTake,
    deleteRecordedClip: DeleteRecordedClip,
    importBackingTrack: ImportBackingTrack,
    updatePublish: UpdatePublishSettings,
    updateMusical: UpdateMusicalSettings,
    trimRecordedClip: TrimRecordedClip,
    preferRecordedClip: PreferRecordedClip,
    updatePractice: UpdatePracticeStats,
    audio: AudioEngine,
    render: RenderEngine,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.factory(
            sessionId = sessionId,
            projectsDir = projectsDir,
            loadSession = loadSession,
            renameSession = renameSession,
            updateGear = updateGear,
            updateVocalPreset = updateVocalPreset,
            updateLyrics = updateLyrics,
            addVoicePart = addVoicePart,
            commitRecordedTake = commitRecordedTake,
            deleteRecordedClip = deleteRecordedClip,
            importBackingTrack = importBackingTrack,
            updatePublish = updatePublish,
            updateMusical = updateMusical,
            trimRecordedClip = trimRecordedClip,
            preferRecordedClip = preferRecordedClip,
            updatePractice = updatePractice,
            audio = audio,
            render = render,
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val pendingKeep by viewModel.pendingKeep.collectAsState()
    val playingRelativePath by remember(viewModel.audio) {
        viewModel.audio.state.map { it.playingRelativePath }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val liveTakeId by remember(viewModel.audio) {
        viewModel.audio.state.map { it.liveTakeId }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val captureFailed by remember(viewModel.audio) {
        viewModel.audio.state.map { it.captureFailed }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val state = ui ?: return
    val context = LocalContext.current
    val copy = LocalAppCopy.current
    val cameraController = remember { SingerCameraController(context) }
    val lyricListener = remember {
        OnDeviceLyricListener(context) { heard -> viewModel.onHeardLyric(heard) }
    }
    val engineStatus by remember(viewModel.audio) {
        viewModel.audio.state.map { it.status }.distinctUntilChanged()
    }.collectAsState(initial = EngineStatus.IDLE)
    val isLiveTake = engineStatus == EngineStatus.RECORDING
    val isCapturing = engineStatus == EngineStatus.RECORDING || engineStatus == EngineStatus.COUNTING
    val view = LocalView.current

    var editingTitle by remember { mutableStateOf(false) }
    var draftTitle by remember(state.title) { mutableStateOf(state.title) }
    var editingLyrics by remember { mutableStateOf(false) }
    var draftLyrics by remember(state.rawLyrics) { mutableStateOf(state.rawLyrics) }
    var pickingRoute by remember { mutableStateOf(false) }
    var showMusical by remember { mutableStateOf(false) }
    var rightsOk by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingDeleteTake by remember { mutableStateOf<String?>(null) }

    val studioOverlayOpen = editingTitle || editingLyrics || pickingRoute || showMusical
    BackHandler(isCapturing) { confirmLeave = true }
    BackHandler(pendingKeep != null && !isCapturing) { }
    BackHandler(!isCapturing && pendingKeep == null && studioOverlayOpen) {
        editingTitle = false
        editingLyrics = false
        pickingRoute = false
        showMusical = false
    }

    DisposableEffect(isCapturing) {
        view.keepScreenOn = isCapturing
        onDispose { view.keepScreenOn = false }
    }

    fun readClipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        return item?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(viewModel) {
        viewModel.audio.state
            .map { (it.status == EngineStatus.RECORDING) to (it.elapsedMs / 100L) }
            .distinctUntilChanged()
            .collect { (recording, tenthSec) ->
                if (recording) {
                    val elapsed = tenthSec * 100L
                    viewModel.syncTimedLyric(elapsed)
                    viewModel.followSungLyric(100L)
                    if (state.kind == SessionKind.PRACTICE) viewModel.tickPractice()
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            lyricListener.stop()
            cameraController.stop()
            cameraController.release()
            viewModel.stopAudioOnExit()
        }
    }

    LaunchedEffect(isLiveTake, state.lyricLanguage, state.rawLyrics) {
        if (isLiveTake) {
            val language = LyricLanguage.resolve(state.lyricLanguage, state.rawLyrics)
            lyricListener.start(language.speechTag)
        } else {
            lyricListener.stop()
        }
    }

    LaunchedEffect(captureFailed) {
        if (captureFailed) viewModel.onCaptureFailed()
    }

    LaunchedEffect(isLiveTake, liveTakeId, state.kind) {
        if (state.kind == SessionKind.VIDEO && isLiveTake && !liveTakeId.isNullOrBlank()) {
            cameraController.start(
                File(File(projectsDir, sessionId), SessionLayout.performanceVideo(liveTakeId!!)),
            )
        } else if (!isLiveTake) {
            cameraController.stop()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFromLifecycle()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lyricsFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()
            if (!imported.isNullOrBlank()) {
                draftLyrics = imported
                editingLyrics = false
                viewModel.saveLyrics(imported, note = "Lyrics file loaded. Tap a line or hit Sing.")
            }
        }
    }

    val backingFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull() ?: "backing.mp3"

            viewModel.importBackingFile(displayName) { destination ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { out ->
                            input.copyTo(out)
                        }
                    } != null
                }.getOrDefault(false)
            }
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.toggleRecording()
        } else {
            viewModel.onPermissionDenied(copy.micNeeded)
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(copy.keepTake) },
            text = { Text(copy.keepTakeBody) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLeave = false
                        viewModel.keepLiveAndLeave(onBack)
                    },
                ) { Text(copy.keepTake) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            confirmLeave = false
                            viewModel.discardLiveAndLeave(onBack)
                        },
                    ) { Text(copy.discardTake) }
                    TextButton(onClick = { confirmLeave = false }) { Text(copy.stayOnStage) }
                }
            },
        )
    }
    pendingKeep?.let { pending ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(copy.keepTake) },
            text = {
                Text(
                    if (pending.silent) {
                        "That pass was almost silent. ${copy.keepTakeBody}"
                    } else {
                        copy.keepTakeBody
                    },
                )
            },
            confirmButton = {
                Button(onClick = viewModel::keepPendingTake) { Text(copy.keepTake) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardPendingTake) { Text(copy.discardTake) }
            },
        )
    }
    pendingDeleteTake?.let { takeId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTake = null },
            title = { Text(copy.delete) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTake(takeId)
                        pendingDeleteTake = null
                    },
                ) { Text(copy.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTake = null }) { Text(copy.cancel) }
            },
        )
    }

    Column(modifier.fillMaxSize().studioAtmosphere(BoothRoom.STAGE, dim = isCapturing)) {
    if (isCapturing) {
        StageLightsPane(
            modifier = Modifier.weight(1f),
            kind = state.kind,
            title = state.title,
            lines = state.lines,
            activeLine = state.activeLine,
            publishTarget = state.publishTarget,
            cameraController = cameraController,
            audioStateFlow = viewModel.audio.state,
            onSelectLine = viewModel::selectLine,
            onSelectTarget = viewModel::selectVideoTarget,
        )
    } else {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (isCapturing) confirmLeave = true else onBack()
                },
            ) { Text(copy.back) }
            TextButton(onClick = { pickingRoute = !pickingRoute }) {
                Text(state.route.label())
            }
        }

        if (editingTitle) {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Song name") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.rename(draftTitle)
                        editingTitle = false
                    },
                ) {
                    Text("Save name")
                }
                TextButton(onClick = { editingTitle = false }) { Text("Cancel") }
            }
        } else {
            Surface(
                onClick = {
                    draftTitle = state.title
                    editingTitle = true
                },
                color = Color.Transparent,
            ) {
                Column {
                    Text(text = state.title, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "${state.kind.label()} · Tap to rename",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Gold,
                    )
                }
            }
        }

        if (pickingRoute) {
            Text(
                text = "How will you hear the music?",
                style = MaterialTheme.typography.titleLarge,
            )
            listeningRoutes.forEach { route ->
                val (accent, wash) = when (route) {
                    GearRoute.WIRED -> SwaraColors.Teal to SwaraColors.TealWash
                    GearRoute.USB -> SwaraColors.Gold to SwaraColors.GoldWash
                    GearRoute.BLUETOOTH -> SwaraColors.Rose to SwaraColors.RoseWash
                    else -> SwaraColors.Gold to SwaraColors.GoldWash
                }
                ChoiceCard(
                    title = route.label(),
                    body = listeningCopy(route),
                    selected = state.route == route,
                    accent = accent,
                    accentWash = wash,
                    onClick = {
                        viewModel.selectRoute(route)
                        pickingRoute = false
                    },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
            onClick = { showMusical = !showMusical },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(copy.keyAndTiming, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (showMusical) copy.back else "${state.keyName} · ${state.tempoBpm.roundToInt()} BPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
        }
        if (showMusical) MusicalSetupCard(
            keyName = state.keyName,
            scaleMode = state.scaleMode,
            tempoBpm = state.tempoBpm,
            keyShiftSemitones = state.keyShiftSemitones,
            playbackRate = state.playbackRate,
            countInEnabled = state.countInEnabled,
            metronomeEnabled = state.metronomeEnabled,
            headphoneFx = state.headphoneFx,
            punchStartMs = state.punchStartMs,
            showBackingShift = state.backing != null,
            guidePlaying = playingRelativePath?.endsWith("Guide_Melody.wav") == true,
            onKey = { viewModel.setMusical(keyName = it) },
            onMode = { viewModel.setMusical(scaleMode = it) },
            onTempo = { viewModel.setMusical(tempoBpm = it) },
            onShift = { viewModel.setMusical(keyShiftSemitones = it) },
            onRate = { viewModel.setMusical(playbackRate = it) },
            onCountIn = { viewModel.setMusical(countInEnabled = it) },
            onMetronome = { viewModel.setMusical(metronomeEnabled = it) },
            onHeadphoneFx = viewModel::setHeadphoneFx,
            onPlayGuide = viewModel::playGuideMelody,
            onClearPunch = viewModel::clearPunch,
        )
        if (state.headphoneFx) {
            Text(
                text = copy.headphoneFxHint,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }

        if (state.kind == SessionKind.PRACTICE) {
            WarmupScaleCoachCard(
                keyName = state.keyName,
                scaleMode = state.scaleMode,
                practice = state.practice,
                audioStateFlow = viewModel.audio.state,
                backing = state.backing,
                playingRelativePath = playingRelativePath,
                onEnableDrone = viewModel::loadSampleBacking,
                onToggleDrone = { path -> viewModel.togglePlayClip(path) },
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = SwaraColors.Card,
                border = BorderStroke(1.dp, SwaraColors.Line),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Backing track",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val compactPad = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            if (state.backing == null) {
                                TextButton(
                                    onClick = viewModel::loadSampleBacking,
                                    contentPadding = compactPad,
                                ) {
                                    Text("Sample drone", maxLines = 1, softWrap = false)
                                }
                            } else {
                                val isPlayingBacking = playingRelativePath == state.backing.relativePath
                                TextButton(
                                    onClick = { viewModel.togglePlayClip(state.backing.relativePath) },
                                    contentPadding = compactPad,
                                ) {
                                    Text(
                                        if (isPlayingBacking) "Stop" else "Preview",
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    if (state.backing != null || rightsOk) {
                                        backingFileLauncher.launch(arrayOf("audio/*"))
                                    }
                                },
                                enabled = state.backing != null || rightsOk,
                                contentPadding = compactPad,
                            ) {
                                Text(
                                    if (state.backing == null) "Track" else "Change",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    val backing = state.backing
                    Text(
                        text = if (backing != null) {
                            "${backing.name} · ${backing.durationText}"
                        } else {
                            "Upload a song you have the right to use. Then tap Music only to play it without the original lyrics."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                    if (backing?.isInstrumental == true) {
                        Text(
                            text = "Playing music only — original lyrics removed. Sing your part on this.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwaraColors.Teal,
                        )
                    }
                    if (backing?.canMakeInstrumental == true) {
                        Button(
                            onClick = viewModel::makeInstrumental,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Music only — remove lyrics")
                        }
                    }
                    if (backing == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rightsOk, onCheckedChange = { rightsOk = it })
                            Text(copy.iCanUseTrack, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (state.kind == SessionKind.VIDEO) {
            VerticalVideoStageCard(
                title = state.title,
                target = state.publishTarget,
                activeLyric = state.lines.getOrNull(state.activeLine)?.text
                    ?: "Add or paste lyrics below to preview them on your 9:16 video stage",
                nextLyric = state.lines.getOrNull(state.activeLine + 1)?.text.orEmpty(),
                audioStateFlow = viewModel.audio.state,
                cameraController = cameraController,
                onSelectTarget = viewModel::selectVideoTarget,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = copy.lyrics, style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    val compactPad = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    TextButton(
                        onClick = {
                            val pasted = readClipboardText()
                            if (pasted != null) {
                                draftLyrics = pasted
                                editingLyrics = false
                                viewModel.saveLyrics(pasted, note = "Lyrics pasted from clipboard.")
                            } else {
                                draftLyrics = state.rawLyrics
                                editingLyrics = true
                            }
                        },
                        contentPadding = compactPad,
                    ) {
                        Text(copy.paste, maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            lyricsFileLauncher.launch(
                                arrayOf("text/*", "application/octet-stream", "*/*"),
                            )
                        },
                        contentPadding = compactPad,
                    ) {
                        Text("File", maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            draftLyrics = state.rawLyrics
                            editingLyrics = !editingLyrics
                        },
                        contentPadding = compactPad,
                    ) {
                        Text(
                            if (editingLyrics) "Close" else if (state.lines.isEmpty()) "Write" else "Edit",
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                var languageMenuOpen by remember { mutableStateOf(false) }
                val chosenLanguage = LyricLanguage.fromTag(state.lyricLanguage)
                val resolvedLanguage = LyricLanguage.resolve(state.lyricLanguage, state.rawLyrics)
                ExposedDropdownMenuBox(
                    expanded = languageMenuOpen,
                    onExpandedChange = { languageMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = chosenLanguage.englishName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            ),
                        label = { Text(copy.lyricLanguage, maxLines = 1, softWrap = false) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuOpen) },
                    )
                    ExposedDropdownMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false },
                    ) {
                        LyricLanguage.entries.forEach { language ->
                            DropdownMenuItem(
                                text = {
                                    Text(language.menuLabel, maxLines = 1, softWrap = false)
                                },
                                onClick = {
                                    viewModel.setMusical(lyricLanguage = language.tag)
                                    languageMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = buildString {
                        if (chosenLanguage == LyricLanguage.AUTO) {
                            append("Auto reads the script (Telugu, Arabic, Korean…). Latin songs: pick Spanish, French, or English.")
                        } else {
                            append("Follows ${chosenLanguage.menuLabel} while you sing.")
                        }
                        if (!state.heardLyric.isNullOrBlank()) {
                            append(" · ${state.heardLyric}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (editingLyrics) {
                    OutlinedTextField(
                        value = draftLyrics,
                        onValueChange = { draftLyrics = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        label = { Text("Write lyrics", maxLines = 1, softWrap = false) },
                        placeholder = { Text("One line per row") },
                        supportingText = {
                            Text("Sing and the highlight follows each line.")
                        },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveLyrics(draftLyrics)
                                editingLyrics = false
                            },
                        ) {
                            Text("Save lyrics")
                        }
                        OutlinedButton(
                            onClick = {
                                val pasted = readClipboardText()
                                if (pasted != null) {
                                    draftLyrics = if (draftLyrics.isBlank()) pasted else "$draftLyrics\n$pasted"
                                }
                            },
                        ) {
                            Text("Paste from clipboard")
                        }
                        if (draftLyrics.isNotBlank()) {
                            TextButton(onClick = { draftLyrics = "" }) {
                                Text("Clear")
                            }
                        }
                    }
                } else if (state.lines.isEmpty()) {
                    Text(
                        text = "Copy lyrics from anywhere and tap Paste. When you Sing, the highlight follows each line you sing.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Muted,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val pasted = readClipboardText()
                                if (pasted != null) {
                                    draftLyrics = pasted
                                    editingLyrics = false
                                    viewModel.saveLyrics(pasted, note = "Lyrics pasted from clipboard.")
                                } else {
                                    draftLyrics = state.rawLyrics
                                    editingLyrics = true
                                }
                            },
                        ) {
                            Text("Paste lyrics")
                        }
                        OutlinedButton(
                            onClick = {
                                lyricsFileLauncher.launch(
                                    arrayOf("text/*", "application/octet-stream", "*/*"),
                                )
                            },
                        ) {
                            Text("Upload file")
                        }
                        OutlinedButton(
                            onClick = {
                                draftLyrics = state.rawLyrics
                                editingLyrics = true
                            },
                        ) {
                            Text("Write")
                        }
                        TextButton(onClick = viewModel::loadSampleLyrics) {
                            Text("Try sample lyrics")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.lines.any { it.startMs != null }) {
                                "Line ${state.activeLine + 1} of ${state.lines.size}"
                            } else {
                                "Line ${state.activeLine + 1} of ${state.lines.size} · ${copy.followsVoice}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwaraColors.Muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { viewModel.stepLine(-1) },
                                enabled = state.activeLine > 0,
                            ) {
                                Text("Prev")
                            }
                            TextButton(
                                onClick = { viewModel.stepLine(1) },
                                enabled = state.activeLine < state.lines.lastIndex,
                            ) {
                                Text("Next")
                            }
                        }
                    }
                    val windowEnd = minOf(state.lines.lastIndex, maxOf(2, state.activeLine + 1))
                    val windowStart = (windowEnd - 2).coerceAtLeast(0)
                    for (index in windowStart..windowEnd) {
                        val line = state.lines[index]
                        val active = index == state.activeLine
                        Surface(
                            onClick = { viewModel.selectLine(index) },
                            color = if (active) SwaraColors.TealWash else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = line.text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                maxLines = if (active) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (active) SwaraColors.Teal else SwaraColors.Muted,
                            )
                        }
                    }
                }
            }
        }

        LiveVocalMonitorCard(
            audioStateFlow = viewModel.audio.state,
        )

        val thisTake = state.takes.firstOrNull { it.preferred } ?: state.takes.firstOrNull()
        if (thisTake != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SwaraColors.TealWash,
                border = BorderStroke(1.5.dp, SwaraColors.Teal),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "This take", style = MaterialTheme.typography.titleLarge, color = SwaraColors.Teal)
                    Text(text = thisTake.label, style = MaterialTheme.typography.titleMedium, color = SwaraColors.Ink)
                    Text(text = thisTake.durationText, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Muted)
                    Button(onClick = viewModel::hearPreferredWithTrack, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (playingRelativePath == "preview-mix") "Stop mix" else "Hear this mix with the track",
                        )
                    }
                    Button(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                        Text("Share this take")
                    }
                }
            }
        }

        Text(text = "Mix for this take", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Tap a mix to hear it. Share sends this mix as a small file WhatsApp can take.",
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presetsToShow = if (state.preset == VocalPreset.CUSTOM) {
                studioPresets + VocalPreset.CUSTOM
            } else {
                studioPresets
            }
            presetsToShow.forEach { preset ->
                val (accent, wash) = preset.chipColors()
                FilterChip(
                    selected = state.preset == preset,
                    onClick = {
                        if (preset == VocalPreset.CUSTOM) {
                            onPublish()
                        } else {
                            viewModel.selectPreset(preset)
                        }
                    },
                    label = { Text(preset.label()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = wash,
                        selectedLabelColor = accent,
                    ),
                    border = BorderStroke(1.dp, if (state.preset == preset) accent else SwaraColors.Line),
                )
            }
        }
        Text(
            text = state.preset.description(),
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Ink,
        )

        if (state.takes.isNotEmpty()) {
            Text(text = "Recorded takes", style = MaterialTheme.typography.titleLarge)
            state.takes.forEach { take ->
                TakeEditCard(
                    take = take,
                    wavFile = File(File(projectsDir, sessionId), take.relativePath),
                    isPlaying = playingRelativePath == take.relativePath,
                    onPlay = { viewModel.togglePlayTake(take) },
                    onDelete = { pendingDeleteTake = take.id },
                    onPrefer = { viewModel.preferTake(take.id) },
                    onTrim = { start, duration -> viewModel.trimTake(take.id, start, duration) },
                    onPunch = { start -> viewModel.punchFrom(take, start) },
                )
            }
        }

        var showVoices by remember { mutableStateOf(state.parts.size > 1) }
        TextButton(onClick = { showVoices = !showVoices }) {
            Text(if (showVoices) "Hide voices" else "More voices")
        }
        if (showVoices) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.parts.forEach { part ->
                    FilterChip(
                        selected = state.selectedPartId == part.id,
                        onClick = { viewModel.selectPart(part.id) },
                        label = { Text(part.label) },
                    )
                }
                if (state.canAddPart) {
                    OutlinedButton(onClick = viewModel::addSecondVoice) {
                        Text(if (state.parts.size == 1) "+ Duet part" else "+ Harmony")
                    }
                }
            }
        }

        if (thisTake == null) {
            OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                Text(copy.mixSave)
            }
        }
    }
    }
    RecordingControlBar(
        audioStateFlow = viewModel.audio.state,
        stageNote = state.stageNote,
        copy = copy,
        lines = state.lines,
        activeLine = state.activeLine,
        onSingClick = { currentlyRecording ->
            if (currentlyRecording) {
                viewModel.toggleRecording()
            } else {
                val needed = buildList {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (state.kind == SessionKind.VIDEO &&
                        context.checkSelfPermission(Manifest.permission.CAMERA) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.CAMERA)
                    }
                }
                if (needed.isEmpty()) {
                    viewModel.toggleRecording()
                } else {
                    recordPermissionLauncher.launch(needed.toTypedArray())
                }
            }
        },
    )
    }
}

/**
 * Dedicated recording control bar. Only observes `isRecording` and 1-second timer ticks
 * (`elapsedMs / 1000L`), so the Sing/Stop button never recomposes on fast audio meter frames.
 */
@Composable
private fun RecordingControlBar(
    audioStateFlow: StateFlow<AudioEngineState>,
    stageNote: String?,
    copy: AppCopy,
    lines: List<LyricLine>,
    activeLine: Int,
    onSingClick: (Boolean) -> Unit,
) {
    val isRecording by remember(audioStateFlow) {
        audioStateFlow.map { it.status == EngineStatus.RECORDING || it.status == EngineStatus.COUNTING }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    val isCounting by remember(audioStateFlow) {
        audioStateFlow.map { it.status == EngineStatus.COUNTING }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val countInBeat by remember(audioStateFlow) {
        audioStateFlow.map { it.countInBeat }.distinctUntilChanged()
    }.collectAsState(initial = 0)
    val elapsedSeconds by remember(audioStateFlow) {
        audioStateFlow.map { it.elapsedMs / 1000L }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
    val micHealth by remember(audioStateFlow) {
        audioStateFlow.map { engine ->
            when {
                engine.status != EngineStatus.RECORDING -> 0
                engine.peak > 0.89f -> 2
                engine.maxPeak < 0.012f && engine.elapsedMs > 2000L -> 1
                else -> 0
            }
        }.distinctUntilChanged()
    }.collectAsState(initial = 0)

    val recPulse = rememberInfiniteTransition(label = "recPulse")
    val pulseScale by recPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recPulseScale",
    )

    Column(Modifier.fillMaxWidth()) {
    if (micHealth > 0) {
        Text(
            text = if (micHealth == 2) copy.micHot else copy.micSilent,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Clay,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isRecording) SwaraColors.TealWash else SwaraColors.Card,
        border = BorderStroke(1.dp, if (isRecording) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                onClick = { onSingClick(isRecording) },
                modifier = Modifier
                    .size(72.dp)
                    .scale(if (isRecording && !isCounting) pulseScale else 1f)
                    .clip(CircleShape),
                color = if (isRecording) SwaraColors.Clay else SwaraColors.Teal,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            isCounting -> "$countInBeat"
                            isRecording -> copy.stop
                            else -> copy.sing
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isCounting -> if (countInBeat <= 1) copy.singOnOne else "$countInBeat"
                        isRecording -> "${copy.recordingLive} · ${formatDuration(elapsedSeconds * 1000L)}"
                        else -> copy.tapToSing
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (isRecording) SwaraColors.Clay else SwaraColors.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stageNote
                        ?: if (isRecording) copy.recordingLive else copy.tapToSing,
                    minLines = 2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
        }
    }
    }
}

/**
 * Reads `audioState.value` inside the `Canvas` draw scope so high-frequency waveform/pitch/EQ
 * updates only trigger GPU draw invalidation without recomposing or re-laying out text.
 * Text readouts update at a calm 400 ms cadence.
 */
@Composable
private fun LiveVocalMonitorCard(
    audioStateFlow: StateFlow<AudioEngineState>,
) {
    val audioState = audioStateFlow.collectAsState()
    var calmMetrics by remember { mutableStateOf(VocalMetrics()) }
    var calmModeLabel by remember { mutableStateOf("READY") }
    var calmActive by remember { mutableStateOf(false) }

    LaunchedEffect(audioStateFlow) {
        while (true) {
            val current = audioStateFlow.value
            val rec = current.status == EngineStatus.RECORDING
            val play = current.playingRelativePath != null
            calmActive = rec || play
            calmModeLabel = when {
                rec -> "LIVE MIC"
                play -> "PLAYBACK"
                else -> "READY"
            }
            calmMetrics = current.metrics
            delay(380L)
        }
    }

    val hasPitch = calmMetrics.pitchHz > 0f
    val tuningLabel = when {
        !hasPitch -> "Listen, then sing"
        abs(calmMetrics.centsOffset) <= 12 -> "In tune"
        abs(calmMetrics.centsOffset) <= 35 && calmMetrics.centsOffset < 0 -> "A little low"
        abs(calmMetrics.centsOffset) <= 35 -> "A little high"
        calmMetrics.centsOffset < 0 -> "Low · lift the note"
        else -> "High · ease the note"
    }
    val voiceLevel = voiceLevelFromDb(calmMetrics.rmsDb)
    val voiceTone = when {
        voiceLevel < 0.22f -> SwaraColors.Muted
        voiceLevel > 0.88f -> SwaraColors.Clay
        else -> SwaraColors.Teal
    }
    val pitchTone = when {
        !hasPitch -> SwaraColors.Gold
        abs(calmMetrics.centsOffset) <= 12 -> SwaraColors.Teal
        abs(calmMetrics.centsOffset) <= 35 -> SwaraColors.Gold
        else -> SwaraColors.Clay
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (calmActive) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Live pitch",
                style = MaterialTheme.typography.titleLarge,
                color = SwaraColors.Ink,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (hasPitch) pitchTone.copy(alpha = 0.16f) else SwaraColors.Paper,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = if (hasPitch) {
                            "${calmMetrics.swaraName}  ·  ${calmMetrics.noteName}"
                        } else {
                            calmModeLabel
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (hasPitch) pitchTone else SwaraColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (hasPitch) {
                            "$tuningLabel  ·  ${calmMetrics.pitchHz.roundToInt()} Hz  ·  ${calmMetrics.rmsDb.roundToInt()} dB"
                        } else {
                            "Sing a held note. Sa and the Western name will sit here."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Ink,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = SwaraColors.Paper,
                    border = BorderStroke(1.dp, SwaraColors.Line),
                ) {
                    PitchAndWaveformCanvas(
                        audioState = audioState,
                        pitchColor = pitchTone,
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp, top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("High", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text("Mid", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text("Low", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqBandRow(
                    label = "Voice",
                    hint = when {
                        voiceLevel < 0.22f -> "Closer"
                        voiceLevel > 0.88f -> "Hot"
                        else -> "Good"
                    },
                    levelProvider = { voiceLevelFromDb(audioState.value.metrics.rmsDb) },
                    color = voiceTone,
                )
                EqBandRow(
                    label = "Low",
                    hint = "Warmth",
                    levelProvider = { audioState.value.metrics.bassLevel },
                    color = SwaraColors.Teal,
                )
                EqBandRow(
                    label = "Mid",
                    hint = "Words",
                    levelProvider = { audioState.value.metrics.midLevel },
                    color = SwaraColors.Gold,
                )
                EqBandRow(
                    label = "Air",
                    hint = "Shine",
                    levelProvider = { audioState.value.metrics.trebleLevel },
                    color = SwaraColors.Rose,
                )
            }
        }
    }
}

@Composable
private fun PitchAndWaveformCanvas(
    audioState: State<AudioEngineState>,
    pitchColor: Color,
) {
    val path = remember { Path() }
    Canvas(modifier = Modifier.fillMaxSize().padding(start = 36.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        val metrics = audioState.value.metrics
        val width = size.width
        val height = size.height
        val waveFloor = height * 0.78f
        val pitchHeight = height * 0.74f
        listOf(0.08f, 0.5f, 0.92f).forEach { frac ->
            val y = pitchHeight * frac
            drawLine(
                color = SwaraColors.Line.copy(alpha = 0.7f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawRect(
            color = SwaraColors.Teal.copy(alpha = 0.10f),
            topLeft = Offset(0f, pitchHeight * 0.38f),
            size = Size(width, pitchHeight * 0.24f),
        )

        val wave = metrics.waveformHistory
        val pitches = metrics.pitchHistory
        val slots = maxOf(wave.size, pitches.size, 2)
        val stepX = width / (slots - 1).toFloat()

        wave.forEachIndexed { idx, amp ->
            val x = idx * stepX
            val bar = (amp.coerceIn(0.04f, 1f) * (height - waveFloor) * 0.9f)
            drawLine(
                color = SwaraColors.Teal.copy(alpha = 0.45f),
                start = Offset(x, height),
                end = Offset(x, height - bar),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        path.reset()
        var penDown = false
        var lastPoint: Offset? = null
        pitches.forEachIndexed { idx, normPitch ->
            val x = idx * stepX
            if (normPitch > 0.01f) {
                val y = pitchHeight * (1f - normPitch.coerceIn(0.05f, 0.95f))
                val point = Offset(x, y)
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                lastPoint = point
            } else {
                penDown = false
            }
        }
        drawPath(
            path = path,
            color = pitchColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        lastPoint?.let { point ->
            drawCircle(color = pitchColor.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = point)
            drawCircle(color = pitchColor, radius = 4.5.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun EqBandRow(
    label: String,
    hint: String,
    levelProvider: () -> Float,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Ink,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = SwaraColors.Line.copy(alpha = 0.55f))
                drawRect(
                    color = SwaraColors.Teal.copy(alpha = 0.16f),
                    topLeft = Offset(size.width * 0.28f, 0f),
                    size = Size(size.width * 0.52f, size.height),
                )
                val fillWidth = size.width * levelProvider().coerceIn(0.02f, 1f)
                drawRect(
                    color = color,
                    size = Size(fillWidth, size.height),
                )
            }
        }
        Text(
            text = hint,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

private fun voiceLevelFromDb(rmsDb: Float): Float {
    return ((rmsDb + 48f) / 42f).coerceIn(0f, 1f)
}

private data class ScaleTarget(
    val swara: String,
    val note: String,
    val hz: Float,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarmupScaleCoachCard(
    keyName: String,
    scaleMode: ScaleMode,
    practice: PracticeStats,
    audioStateFlow: StateFlow<AudioEngineState>,
    backing: BackingTrackUi?,
    playingRelativePath: String?,
    onEnableDrone: () -> Unit,
    onToggleDrone: (String) -> Unit,
) {
    var selectedIdx by remember { mutableStateOf(0) }
    var calmPitchHz by remember { mutableStateOf(0f) }
    val scale = remember(keyName, scaleMode) { MusicalKeys.scaleForKey(keyName, scaleMode) }
    val target = scale.getOrElse(selectedIdx.coerceIn(0, scale.lastIndex)) { scale.first() }

    LaunchedEffect(audioStateFlow) {
        while (true) {
            calmPitchHz = audioStateFlow.value.metrics.pitchHz
            delay(380L)
        }
    }

    val centsDiff = if (calmPitchHz > 60f) {
        (1200.0 * (ln(calmPitchHz.toDouble() / target.hz.toDouble()) / ln(2.0))).roundToInt()
    } else {
        null
    }
    val isHit = centsDiff != null && abs(centsDiff) <= 45

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (isHit) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Swara & Scale Coach", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Target: ${target.swara} (${target.westernNote} · ${target.hz.roundToInt()} Hz)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Gold,
                    )
                }
                if (backing == null) {
                    OutlinedButton(onClick = onEnableDrone) {
                        Text("Load Tanpura")
                    }
                } else {
                    val isPlayingDrone = playingRelativePath == backing.relativePath
                    OutlinedButton(onClick = { onToggleDrone(backing.relativePath) }) {
                        Text(if (isPlayingDrone) "Stop Tanpura" else "Play Tanpura")
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                scale.forEachIndexed { idx, item ->
                    FilterChip(
                        selected = selectedIdx == idx,
                        onClick = { selectedIdx = idx },
                        label = { Text("${item.swara} (${item.westernNote})") },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isHit) SwaraColors.TealWash else SwaraColors.Paper,
            ) {
                val feedback = when {
                    centsDiff == null -> "Tap Sing below and hold ${target.swara} (${target.westernNote})"
                    abs(centsDiff) <= 45 -> "Locked on ${target.swara} (${target.westernNote})! Hold steady."
                    centsDiff < 0 -> "Sing slightly higher ↑ to reach ${target.swara} (${target.westernNote})"
                    else -> "Sing slightly lower ↓ to reach ${target.swara} (${target.westernNote})"
                }
                Text(
                    text = feedback,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isHit) SwaraColors.Teal else SwaraColors.Ink,
                )
            }
            val low = if (practice.lowestHz > 0f) {
                VocalSignalAnalyzer.pitchToNoteAndSwara(practice.lowestHz).first
            } else {
                "—"
            }
            val high = if (practice.highestHz > 0f) {
                VocalSignalAnalyzer.pitchToNoteAndSwara(practice.highestHz).first
            } else {
                "—"
            }
            Text(
                text = "Score ${practice.scorePercent}% · ${practice.hits}/${practice.attempts} in tune · Range $low–$high",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
    }
}

private val videoFrameTargets = listOf(
    PublishTarget.REELS,
    PublishTarget.SHORTS,
    PublishTarget.WHATSAPP,
)

@Composable
private fun StageLightsPane(
    kind: SessionKind,
    title: String,
    lines: List<LyricLine>,
    activeLine: Int,
    publishTarget: PublishTarget,
    cameraController: SingerCameraController,
    audioStateFlow: StateFlow<AudioEngineState>,
    onSelectLine: (Int) -> Unit,
    onSelectTarget: (PublishTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StagePitchChip(audioStateFlow)
        if (kind == SessionKind.VIDEO) {
            VerticalVideoStageCard(
                title = title,
                target = publishTarget,
                activeLyric = lines.getOrNull(activeLine)?.text.orEmpty(),
                nextLyric = lines.getOrNull(activeLine + 1)?.text.orEmpty(),
                audioStateFlow = audioStateFlow,
                cameraController = cameraController,
                onSelectTarget = onSelectTarget,
                expanded = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            LyricStagePane(
                lines = lines,
                activeLine = activeLine,
                onSelectLine = onSelectLine,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LyricStagePane(
    lines: List<LyricLine>,
    activeLine: Int,
    onSelectLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previous = lines.getOrNull(activeLine - 1)
    val current = lines.getOrNull(activeLine)
    val next = lines.getOrNull(activeLine + 1)
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xCC0A1214),
        border = BorderStroke(1.dp, SwaraColors.Teal.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (previous != null) {
                Surface(
                    onClick = { onSelectLine(activeLine - 1) },
                    color = Color.Transparent,
                ) {
                    Text(
                        text = previous.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.42f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                onClick = { if (current != null) onSelectLine(activeLine) },
                color = Color.Transparent,
            ) {
                Text(
                    text = current?.text ?: "Sing. The words can wait until after this take.",
                    style = MaterialTheme.typography.displayMedium,
                    color = SwaraColors.Teal,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (next != null) {
                Surface(
                    onClick = { onSelectLine(activeLine + 1) },
                    color = Color.Transparent,
                ) {
                    Text(
                        text = next.text,
                        style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp),
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePitchChip(audioStateFlow: StateFlow<AudioEngineState>) {
    var note by remember { mutableStateOf("LISTEN") }
    var tuning by remember { mutableStateOf("Hold the note you want") }
    LaunchedEffect(audioStateFlow) {
        while (true) {
            val metrics = audioStateFlow.value.metrics
            if (metrics.pitchHz > 0f) {
                note = "${metrics.swaraName}  ·  ${metrics.noteName}  ·  ${metrics.pitchHz.roundToInt()} Hz"
                tuning = when {
                    kotlin.math.abs(metrics.centsOffset) <= 12 -> "In tune"
                    kotlin.math.abs(metrics.centsOffset) <= 35 && metrics.centsOffset < 0 -> "A little low"
                    kotlin.math.abs(metrics.centsOffset) <= 35 -> "A little high"
                    metrics.centsOffset < 0 -> "Low · lift the note"
                    else -> "High · ease the note"
                }
            }
            delay(380L)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0x990A1214),
        border = BorderStroke(1.dp, SwaraColors.Teal.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = note,
                style = MaterialTheme.typography.titleLarge,
                color = SwaraColors.Teal,
                maxLines = 2,
            )
            Text(
                text = tuning,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.84f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerticalVideoStageCard(
    title: String,
    target: PublishTarget,
    activeLyric: String,
    nextLyric: String,
    audioStateFlow: StateFlow<AudioEngineState>,
    cameraController: SingerCameraController,
    onSelectTarget: (PublishTarget) -> Unit,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val audioState = audioStateFlow.collectAsState()
    val effectiveTarget = if (target in videoFrameTargets) target else PublishTarget.REELS

    Surface(
        modifier = if (expanded) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (expanded) Color.Transparent else SwaraColors.Card,
        border = if (expanded) null else BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier
                .then(if (expanded) Modifier.fillMaxSize() else Modifier)
                .padding(if (expanded) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "9:16 Vertical Video Stage", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "1080p 9:16. Picture starts with your take, not the count-in.",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                videoFrameTargets.forEach { item ->
                    FilterChip(
                        selected = effectiveTarget == item,
                        onClick = { onSelectTarget(item) },
                        label = {
                            Text(
                                when (item) {
                                    PublishTarget.REELS -> "Reels 9:16"
                                    PublishTarget.SHORTS -> "Shorts 9:16"
                                    else -> "WhatsApp 9:16"
                                },
                            )
                        },
                    )
                }
            }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier.weight(1f, fill = true) else Modifier.height(320.dp)),
                contentAlignment = Alignment.Center,
            ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f),
                shape = RoundedCornerShape(18.dp),
                color = SwaraColors.Ink,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SingerCameraPreview(
                        controller = cameraController,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val m = audioState.value.metrics
                        val amp = (m.midLevel * 0.6f + m.bassLevel * 0.4f).coerceIn(0.08f, 1f)
                        val center = Offset(size.width / 2f, size.height * 0.44f)
                        drawCircle(
                            color = SwaraColors.Teal.copy(alpha = 0.18f),
                            radius = size.minDimension * (0.18f + amp * 0.16f),
                            center = center,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text = effectiveTarget.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = SwaraColors.Gold,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = activeLyric,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        if (nextLyric.isNotBlank()) {
                            Text(
                                text = nextLyric,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.68f),
                            )
                        }
                    }
                }
                }
            }
            }
        }
    }
}

private fun isMusicOnlyBacking(name: String, relativePath: String): Boolean {
    val text = "$name $relativePath".lowercase()
    return text.contains("instrumental") || text.contains("music only")
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicalSetupCard(
    keyName: String,
    scaleMode: ScaleMode,
    tempoBpm: Float,
    keyShiftSemitones: Int,
    playbackRate: Float,
    countInEnabled: Boolean,
    metronomeEnabled: Boolean,
    headphoneFx: Boolean,
    punchStartMs: Long,
    showBackingShift: Boolean,
    guidePlaying: Boolean,
    onKey: (String) -> Unit,
    onMode: (ScaleMode) -> Unit,
    onTempo: (Float) -> Unit,
    onShift: (Int) -> Unit,
    onRate: (Float) -> Unit,
    onCountIn: (Boolean) -> Unit,
    onMetronome: (Boolean) -> Unit,
    onHeadphoneFx: (Boolean) -> Unit,
    onPlayGuide: () -> Unit,
    onClearPunch: () -> Unit,
) {
    val copy = LocalAppCopy.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Start in time & in key", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "4-beat count-in, click, and a key that matches your range.",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MusicalKeys.ALL.forEach { key ->
                    FilterChip(
                        selected = key == keyName,
                        onClick = { onKey(key) },
                        label = { Text(key) },
                    )
                }
            }
            Text(
                text = copy.raga,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScaleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == scaleMode,
                        onClick = { onMode(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = countInEnabled,
                    onClick = { onCountIn(!countInEnabled) },
                    label = { Text(if (countInEnabled) "Count-in on" else "Count-in off") },
                )
                FilterChip(
                    selected = metronomeEnabled,
                    onClick = { onMetronome(!metronomeEnabled) },
                    label = { Text(if (metronomeEnabled) "Click on" else "Click off") },
                )
                FilterChip(
                    selected = headphoneFx,
                    onClick = { onHeadphoneFx(!headphoneFx) },
                    label = { Text(copy.headphoneFx) },
                )
                FilterChip(
                    selected = guidePlaying,
                    onClick = onPlayGuide,
                    label = { Text(if (guidePlaying) copy.stopGuide else copy.playGuide) },
                )
            }
            Text(
                text = "Click tempo ${tempoBpm.roundToInt()} BPM",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Slider(
                value = tempoBpm,
                onValueChange = onTempo,
                valueRange = 60f..140f,
            )
            if (showBackingShift) {
                Text(
                    text = "Backing pitch ${if (keyShiftSemitones >= 0) "+" else ""}$keyShiftSemitones · speed ${(playbackRate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
                Slider(
                    value = keyShiftSemitones.toFloat(),
                    onValueChange = { onShift(it.roundToInt()) },
                    valueRange = -6f..6f,
                    steps = 11,
                )
                Slider(
                    value = playbackRate,
                    onValueChange = onRate,
                    valueRange = 0.8f..1.2f,
                )
            }
            if (punchStartMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Punch-in armed at ${formatDuration(punchStartMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Gold,
                    )
                    TextButton(onClick = onClearPunch) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun TakeWaveform(
    peaks: FloatArray,
    startFraction: Float,
    endFraction: Float,
    playing: Boolean,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        drawRect(SwaraColors.Paper)
        val keepLeft = size.width * startFraction
        val keepRight = size.width * endFraction
        drawRect(
            color = SwaraColors.Teal.copy(alpha = if (playing) 0.22f else 0.14f),
            topLeft = Offset(keepLeft, 0f),
            size = Size((keepRight - keepLeft).coerceAtLeast(1f), size.height),
        )
        val step = size.width / peaks.size.coerceAtLeast(1).toFloat()
        val mid = size.height / 2f
        peaks.forEachIndexed { index, peak ->
            val x = index * step + step / 2f
            val half = (peak.coerceIn(0.06f, 1f) * size.height * 0.42f)
            val inside = x in keepLeft..keepRight
            drawLine(
                color = if (inside) SwaraColors.Teal else SwaraColors.Muted.copy(alpha = 0.35f),
                start = Offset(x, mid - half),
                end = Offset(x, mid + half),
                strokeWidth = (step * 0.62f).coerceIn(1.5.dp.toPx(), 4.dp.toPx()),
                cap = StrokeCap.Round,
            )
        }
        drawLine(SwaraColors.Gold, Offset(keepLeft, 4.dp.toPx()), Offset(keepLeft, size.height - 4.dp.toPx()), 2.dp.toPx())
        drawLine(SwaraColors.Gold, Offset(keepRight, 4.dp.toPx()), Offset(keepRight, size.height - 4.dp.toPx()), 2.dp.toPx())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakeEditCard(
    take: RecordedTakeUi,
    wavFile: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPrefer: () -> Unit,
    onTrim: (Long, Long) -> Unit,
    onPunch: (Long) -> Unit,
) {
    var startMs by remember(take.id, take.sourceInMs) { mutableStateOf(take.sourceInMs.toFloat()) }
    var endMs by remember(take.id, take.sourceInMs, take.durationMs) {
        mutableStateOf((take.sourceInMs + take.durationMs).toFloat())
    }
    val maxMs = take.sourceDurationMs.toFloat().coerceAtLeast(1f)
    var peaks by remember(take.id, wavFile.path) { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(take.id, wavFile.path) {
        peaks = withContext(Dispatchers.IO) { WavPeaks.fromFile(wavFile) }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (take.preferred) SwaraColors.TealWash else if (isPlaying) SwaraColors.TealWash else SwaraColors.Card,
        border = BorderStroke(1.dp, if (take.preferred || isPlaying) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = take.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = take.durationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDelete) { Text("Delete", color = SwaraColors.Clay) }
                    Button(onClick = onPlay) { Text(if (isPlaying) "Stop" else "Play") }
                }
            }
            Text(
                text = "Keep ${formatDuration(startMs.toLong())} to ${formatDuration(endMs.toLong())}",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            if (peaks.isNotEmpty()) {
                TakeWaveform(
                    peaks = peaks,
                    startFraction = (startMs / maxMs).coerceIn(0f, 1f),
                    endFraction = (endMs / maxMs).coerceIn(0f, 1f),
                    playing = isPlaying,
                )
            }
            Text(
                text = "Cut the start",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Gold,
            )
            Slider(value = startMs, onValueChange = { startMs = it.coerceIn(0f, endMs - 250f) }, valueRange = 0f..maxMs)
            Text(
                text = "Cut the end",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Gold,
            )
            Slider(value = endMs, onValueChange = { endMs = it.coerceIn(startMs + 250f, maxMs) }, valueRange = 0f..maxMs)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val start = startMs.toLong().coerceAtLeast(0L)
                        val duration = (endMs.toLong() - start).coerceAtLeast(250L)
                        onTrim(start, duration)
                    },
                ) { Text("Keep this section") }
                OutlinedButton(onClick = { onPunch(startMs.toLong()) }) { Text("Punch from here") }
                FilterChip(
                    selected = take.preferred,
                    onClick = onPrefer,
                    label = { Text(if (take.preferred) "This take" else "Use this take") },
                )
            }
        }
    }
}

private fun writeSampleDroneWav(file: File, sampleRate: Int, durationSeconds: Int, keyName: String = "C") {
    val totalSamples = sampleRate * durationSeconds
    val totalAudioBytes = totalSamples * 2L
    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(0L)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + totalAudioBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioBytes.toInt())
        }
        raf.write(header.array())
        val tonic = MusicalKeys.tonicC3Hz(keyName)
        val octave = tonic * 2.0
        val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            if (chunk.remaining() < 2) {
                raf.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
            val t = i.toDouble() / sampleRate.toDouble()
            val swell = 0.82 + 0.18 * sin(2.0 * PI * 0.20 * t)
            val wave = (
                0.72 * sin(2.0 * PI * tonic * t) +
                    0.28 * sin(2.0 * PI * octave * t)
                ) * swell
            val pcm = (wave * 9500.0).toInt().coerceIn(-32767, 32767).toShort()
            chunk.putShort(pcm)
        }
        if (chunk.position() > 0) {
            raf.write(chunk.array(), 0, chunk.position())
        }
    }
}

private fun writeGuideMelodyWav(
    file: File,
    sampleRate: Int,
    keyName: String,
    mode: ScaleMode,
) {
    val scale = MusicalKeys.scaleForKey(keyName, mode)
    val noteMs = 420
    val gapMs = 80
    val totalMs = scale.size * (noteMs + gapMs)
    val totalSamples = (sampleRate * totalMs) / 1000
    val totalAudioBytes = totalSamples * 2L
    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(0L)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + totalAudioBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioBytes.toInt())
        }
        raf.write(header.array())
        val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        val noteSamples = (sampleRate * noteMs) / 1000
        val gapSamples = (sampleRate * gapMs) / 1000
        var written = 0
        fun putSample(value: Short) {
            if (chunk.remaining() < 2) {
                raf.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
            chunk.putShort(value)
            written += 1
        }
        scale.forEach { note ->
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / sampleRate.toDouble()
                val env = when {
                    i < noteSamples / 12 -> i.toDouble() / (noteSamples / 12.0)
                    i > noteSamples * 3 / 4 -> ((noteSamples - i).toDouble() / (noteSamples / 4.0))
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
                val wave = sin(2.0 * PI * note.hz * t) * env
                putSample((wave * 11_000.0).toInt().coerceIn(-32767, 32767).toShort())
            }
            repeat(gapSamples) { putSample(0) }
        }
        while (written < totalSamples) putSample(0)
        if (chunk.position() > 0) {
            raf.write(chunk.array(), 0, chunk.position())
        }
    }
}

private fun ScaleMode.label(): String = when (this) {
    ScaleMode.MAJOR -> "Major"
    ScaleMode.MINOR -> "Minor"
    ScaleMode.MOHANAM -> "Mohanam"
    ScaleMode.BHAIRAV -> "Bhairav"
    ScaleMode.YAMAN -> "Yaman"
}

private fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Warm-up"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

private fun GearRoute.label(): String = when (this) {
    GearRoute.WIRED -> "Headphones"
    GearRoute.USB -> "USB mic"
    GearRoute.BLUETOOTH -> "Bluetooth"
    GearRoute.SPEAKER -> "Speaker"
    GearRoute.BUILTIN_MIC -> "Phone mic"
    GearRoute.UNKNOWN -> "Headphones"
}

private fun VocalPreset.label(): String = when (this) {
    VocalPreset.RAW -> "Keep dry"
    VocalPreset.CLOSE -> "Voice forward"
    VocalPreset.STAGE -> "Soft hall"
    VocalPreset.RADIO -> "Phone room clean"
    VocalPreset.CUSTOM -> "Custom"
}

private fun VocalPreset.description(): String = when (this) {
    VocalPreset.RAW -> "Keep dry: almost the mic. Light rumble only. No hall, no punch."
    VocalPreset.CLOSE -> "Voice forward: you sit in front. Track ducks. Bright and close."
    VocalPreset.STAGE -> "Soft hall: long notes bloom in a room. Delay on the tail."
    VocalPreset.RADIO -> "Phone room clean: hiss and esses down, music steps back, radio punch."
    VocalPreset.CUSTOM -> "Your sliders on Mix & Save. Tap to open them."
}

private fun VocalPreset.mixHearLabel(): String = label()

private fun VocalPreset.chipColors(): Pair<Color, Color> =
    when (this) {
        VocalPreset.CLOSE -> SwaraColors.Teal to SwaraColors.TealWash
        VocalPreset.STAGE -> SwaraColors.Gold to SwaraColors.GoldWash
        VocalPreset.RADIO -> SwaraColors.Rose to SwaraColors.RoseWash
        VocalPreset.RAW -> SwaraColors.Muted to SwaraColors.Card
        VocalPreset.CUSTOM -> SwaraColors.Ink to SwaraColors.Card
    }

private fun listeningCopy(output: GearRoute): String = when (output) {
    GearRoute.WIRED -> "Best for singing in time."
    GearRoute.USB -> "Direct monitoring through a USB mic or interface."
    GearRoute.BLUETOOTH -> "Good for playback. Singing along over Bluetooth feels late."
    GearRoute.SPEAKER -> "Fine for solo takes. Use headphones when singing over a backing track."
    GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN -> "Plug in headphones before you sing."
}
