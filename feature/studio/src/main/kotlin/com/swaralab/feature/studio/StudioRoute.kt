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
import androidx.compose.runtime.key
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
import com.swaralab.domain.model.SingMode
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.Analytics
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.NoOpAnalytics
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.InstrumentalResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.port.userMessage
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CleanUnusedTakes
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.RestoreSession
import com.swaralab.domain.usecase.SessionAutoSave
import com.swaralab.domain.usecase.SaveTake
import com.swaralab.domain.usecase.SplitRecordedClip
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateRecordedClip
import com.swaralab.domain.usecase.UpdateTrackChain
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdateMusicalSettings
import com.swaralab.domain.usecase.UpdatePracticeStats
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.MediaPermissions
import com.swaralab.ui.PermissionRationale
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.AppCopy
import com.swaralab.domain.audio.StageTakeState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.LocalTakeShare
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

private val studioPresets = listOf(
    VocalPreset.CLOSE,
    VocalPreset.STAGE,
    VocalPreset.RADIO,
    VocalPreset.BRIGHT,
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
    saveTake: SaveTake = SaveTake(commitRecordedTake),
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
    takeCatalog: com.swaralab.domain.port.TakeCatalog? = null,
    takeConversion: com.swaralab.domain.port.TakeConversion? = null,
    analytics: Analytics = NoOpAnalytics,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    initialSing: String = "",
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
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val pendingKeep by viewModel.pendingKeep.collectAsState()
    val stageTake by viewModel.stageTake.collectAsState()
    val recoverableDraft by viewModel.recoverableDraft.collectAsState()
    val liveEffect by viewModel.liveEffect.collectAsState()
    val backingPrep by viewModel.backingPrep.collectAsState()
    val practiceLooping by viewModel.practiceLooping.collectAsState()
    val practiceLoop by viewModel.practiceLoop.collectAsState()
    val playingRelativePath by remember(viewModel.audio) {
        viewModel.audio.state.map { it.playingRelativePath }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val mixPosition by remember(viewModel.audio) {
        viewModel.audio.state.map { it.positionMs }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
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
    val engineStatus by remember(viewModel.audio) {
        viewModel.audio.state.map { it.status }.distinctUntilChanged()
    }.collectAsState(initial = EngineStatus.IDLE)
    val isLiveTake = engineStatus == EngineStatus.RECORDING
    val isCapturing = engineStatus == EngineStatus.RECORDING ||
        engineStatus == EngineStatus.COUNTING ||
        engineStatus == EngineStatus.PAUSED
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
    var showSingChooser by remember { mutableStateOf(false) }
    var showMicRationale by remember { mutableStateOf(false) }
    var showReadRationale by remember { mutableStateOf(false) }
    var recordAfterImport by remember { mutableStateOf(false) }
    var showBackingDesk by remember { mutableStateOf(false) }
    var karaokePrep by remember { mutableStateOf(false) }

    val studioOverlayOpen = editingTitle || editingLyrics || pickingRoute || showMusical
    BackHandler(isCapturing) { confirmLeave = true }
    BackHandler(pendingKeep != null && !isCapturing) { }
    BackHandler(!isCapturing && pendingKeep == null && karaokePrep) { karaokePrep = false }
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
                    val elapsed = viewModel.audio.state.value.positionMs.takeIf { it > 0L }
                        ?: (tenthSec * 100L)
                    viewModel.syncTimedLyric(elapsed)
                    viewModel.followSungLyric(100L)
                    viewModel.checkPunchEnd(elapsed)
                    if (state.kind == SessionKind.PRACTICE) viewModel.tickPractice()
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.stop()
            cameraController.release()
            viewModel.stopAudioOnExit()
        }
    }

    LaunchedEffect(captureFailed) {
        if (captureFailed) viewModel.onCaptureFailed()
    }
    LaunchedEffect(viewModel) {
        viewModel.audio.errors.collect { error ->
            viewModel.onEngineError(error.userMessage())
        }
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
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushAutoSave()
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
        showMicRationale = false
        if (grants[Manifest.permission.RECORD_AUDIO] != false &&
            MediaPermissions.hasRecordAudio(context)
        ) {
            viewModel.toggleRecording()
        } else {
            viewModel.onPermissionDenied(copy.micNeeded)
        }
    }

    val audioReadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        showReadRationale = false
        backingFileLauncher.launch(arrayOf("audio/*"))
    }

    fun neededRecordPermissions(): List<String> = buildList {
        if (!MediaPermissions.hasRecordAudio(context)) add(Manifest.permission.RECORD_AUDIO)
        if (state.kind == SessionKind.VIDEO &&
            context.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.CAMERA)
        }
    }

    fun requestAndRecord() {
        val needed = neededRecordPermissions()
        if (needed.isEmpty()) {
            viewModel.toggleRecording()
        } else if (needed.contains(Manifest.permission.RECORD_AUDIO)) {
            showMicRationale = true
        } else {
            recordPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun pickBacking() {
        if (MediaPermissions.hasAudioRead(context)) {
            backingFileLauncher.launch(arrayOf("audio/*"))
        } else {
            showReadRationale = true
        }
    }

    var appliedInitialSing by remember { mutableStateOf(false) }
    var openedPractice by remember { mutableStateOf(false) }
    LaunchedEffect(state.kind) {
        if (state.kind == SessionKind.PRACTICE && !openedPractice) {
            openedPractice = true
            karaokePrep = true
        }
    }
    LaunchedEffect(initialSing, state.backing?.relativePath) {
        if (appliedInitialSing || initialSing.isBlank()) return@LaunchedEffect
        val mode = SingMode.entries.firstOrNull { it.name.equals(initialSing, ignoreCase = true) }
            ?: return@LaunchedEffect
        if (mode == SingMode.FROM_PHONE && state.backing == null) {
            rightsOk = true
            showBackingDesk = true
            recordAfterImport = true
            pickBacking()
            appliedInitialSing = true
            return@LaunchedEffect
        }
        appliedInitialSing = true
        if (mode == SingMode.LYRICS) {
            editingLyrics = true
        }
        viewModel.prepareSingMode(
            mode = mode,
            onNeedSongFromPhone = {
                rightsOk = true
                showBackingDesk = true
                recordAfterImport = true
                pickBacking()
            },
            onNeedLyrics = { editingLyrics = true },
            onReadyToRecord = { karaokePrep = true },
        )
    }
    LaunchedEffect(state.backing?.relativePath, recordAfterImport) {
        if (recordAfterImport && state.backing != null) {
            recordAfterImport = false
            karaokePrep = true
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
    recoverableDraft?.let { draft ->
        DraftRecoveryDialog(
            draft = draft,
            onRecover = viewModel::recoverDraft,
            onDiscard = viewModel::discardDraft,
        )
    }
    val showKaraoke = isCapturing || pendingKeep != null || karaokePrep ||
        stageTake is com.swaralab.domain.audio.StageTakeState.Error
    if (!showKaraoke) pendingKeep?.let { pending ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(copy.keepTake) },
            text = {
                Text(
                    buildString {
                        if (pending.silent) append("That pass was almost silent. ")
                        append(copy.keepTakeBody)
                        pending.score?.let { append(" Score ${it.toInt()} of 100.") }
                    },
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { viewModel.keepPendingTake(onPublish) }) {
                        Text(copy.keepAndMix)
                    }
                    TextButton(onClick = { viewModel.keepPendingTake() }) {
                        Text(copy.keepTake)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::retryPendingTake) { Text("Retry") }
                    TextButton(onClick = viewModel::discardPendingTake) { Text(copy.discardTake) }
                }
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
    if (showKaraoke) {
        val engine by viewModel.audio.state.collectAsState()
        if (state.kind == SessionKind.PRACTICE && stageTake !is StageTakeState.Error) {
            val lyric = state.lines.getOrNull(state.lyricHighlight.lineIndex.coerceAtLeast(0))?.text
                ?: state.lines.getOrNull(state.activeLine)?.text
            PracticeModeScreen(
                uiState = PracticeUiState(
                    currentPitch = engine.metrics.livePitch,
                    currentLyric = lyric,
                    isLooping = practiceLooping,
                    loop = practiceLoop,
                    tempo = state.playbackRate.coerceIn(0.5f, 1f),
                    isPlaying = engineStatus == EngineStatus.RECORDING,
                    isPaused = engineStatus == EngineStatus.PAUSED,
                    backingBusy = backingPrep.busy,
                    backingProgress = backingPrep.progress,
                ),
                onTempoChange = { viewModel.setMusical(playbackRate = it.coerceIn(0.5f, 1f)) },
                onLoopToggle = viewModel::togglePracticeLoop,
                onSetLoopPoint = viewModel::setPracticeLoopPoint,
                onPlayToggle = {
                    when (engineStatus) {
                        EngineStatus.PAUSED -> viewModel.resumeTake()
                        EngineStatus.RECORDING -> viewModel.pauseTake()
                        else -> {
                            karaokePrep = false
                            requestAndRecord()
                        }
                    }
                },
                onExitPractice = {
                    viewModel.exitPractice()
                    karaokePrep = false
                },
                modifier = Modifier.weight(1f),
            )
        } else StageTakeScreen(
            state = stageTake,
            lyrics = state.lines,
            highlight = state.lyricHighlight,
            voiceGain = state.voiceGain,
            trackGain = state.trackGain,
            currentEffect = liveEffect,
            durationMs = state.backing?.durationMs ?: 0L,
            error = engine.lastError,
            waveform = engine.metrics.waveformHistory,
            metrics = engine.metrics,
            livePitch = engine.metrics.livePitch,
            keyShiftSemitones = state.keyShiftSemitones,
            playbackRate = state.playbackRate,
            showBackingShift = state.backing != null,
            backingBusy = backingPrep.busy,
            backingProgress = backingPrep.progress,
            onKeyShift = { viewModel.setMusical(keyShiftSemitones = it) },
            onPlaybackRate = { viewModel.setMusical(playbackRate = it) },
            onStart = {
                karaokePrep = false
                requestAndRecord()
            },
            onPause = viewModel::pauseTake,
            onResume = viewModel::resumeTake,
            onStop = { viewModel.toggleRecording() },
            onSeek = viewModel::seekReady,
            onSeekLyric = viewModel::seekToLyricLine,
            onVoiceGain = viewModel::setVoiceGain,
            onTrackGain = viewModel::setTrackGain,
            onEffectChange = viewModel::setLiveEffect,
            onSave = { viewModel.keepPendingTake(onPublish) },
            onShare = {
                val relative = pendingKeep?.finished?.relativePath
                    ?: (stageTake as? StageTakeState.Review)?.dryVocalPath
                val file = relative?.let { File(File(projectsDir, sessionId), it) }
                LocalTakeShare.open(context, file, state.title)
            },
            onRetry = {
                viewModel.discardPendingTake()
                karaokePrep = true
                viewModel.retryTake()
            },
            onBack = {
                if (karaokePrep && !isCapturing && pendingKeep == null) {
                    karaokePrep = false
                } else {
                    onBack()
                }
            },
            onPickTrack = { pickBacking() },
            onRequestPermission = { recordPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
            modifier = Modifier.weight(1f),
            videoSlot = if (state.kind == SessionKind.VIDEO) {
                {
                    StageLightsPane(
                        kind = state.kind,
                        title = state.title,
                        lines = state.lines,
                        activeLine = state.activeLine,
                        publishTarget = state.publishTarget,
                        cameraController = cameraController,
                        audioStateFlow = viewModel.audio.state,
                        onSelectLine = viewModel::selectLine,
                        onSelectTarget = viewModel::selectVideoTarget,
                        highlight = state.lyricHighlight,
                    )
                }
            } else {
                null
            },
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

        val canUndo by viewModel.canUndo.collectAsState()
        val canRedo by viewModel.canRedo.collectAsState()
        EditorActionBar(
            copy = copy,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = viewModel::undoEdit,
            onRedo = viewModel::redoEdit,
            onExport = onPublish,
        )
        TimelineOverview(
            takes = state.takes,
            durationMs = studioDurationMs(state),
            positionMs = mixPosition,
            punchStartMs = state.punchStartMs,
            punchEndMs = state.punchEndMs,
            onSeek = viewModel::seekMix,
            onMoveClip = viewModel::moveTake,
        )
        LiveVocalMonitorCard(audioStateFlow = viewModel.audio.state)
        LayersMixerCard(
            parts = state.parts,
            selectedPartId = state.selectedPartId,
            canAddPart = state.canAddPart,
            addLayerLabel = copy.addVocalLayer,
            onSelect = viewModel::selectPart,
            onMix = { id, gain, pan, mute, solo -> viewModel.setLayerMix(id, gain, pan, mute, solo) },
            onChain = viewModel::setLayerChain,
            onAdd = viewModel::addLayer,
            onRecord = { id ->
                viewModel.selectPart(id)
                val take = state.takes.firstOrNull { it.preferred && it.active }
                    ?: state.takes.firstOrNull { it.active }
                if (take != null) {
                    viewModel.punchFrom(take, mixPosition)
                    karaokePrep = true
                } else {
                    karaokePrep = true
                }
            },
        )

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
            punchEndMs = state.punchEndMs,
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
            voiceGain = state.voiceGain,
            trackGain = state.trackGain,
            backingDurationMs = state.backing?.durationMs ?: 0L,
            seekEnabled = !isCapturing,
            onVoiceGain = viewModel::setVoiceGain,
            onTrackGain = viewModel::setTrackGain,
            onSeek = viewModel::seekReady,
        )
        if (state.headphoneFx) {
            Text(
                text = copy.headphoneFxHint,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
        Text(
            text = copy.tapToSing,
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )

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
        } else if (showBackingDesk) {
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
                                        pickBacking()
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
        } else if (state.backing != null) {
            Surface(
                onClick = { showBackingDesk = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SwaraColors.Card,
                border = BorderStroke(1.dp, SwaraColors.Line),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Song from this phone", style = MaterialTheme.typography.labelLarge, color = SwaraColors.Teal)
                    Text(
                        text = "${state.backing.name} · ${state.backing.durationText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Ink,
                    )
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

        if (editingLyrics || state.lines.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = copy.lyrics, style = MaterialTheme.typography.titleLarge)
                if (editingLyrics) {
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
                }
                if (editingLyrics) {
                    key(draftLyrics) {
                    LyricsEditor(
                        initialLines = if (draftLyrics.isBlank()) {
                            emptyList()
                        } else if (state.rawLyrics == draftLyrics) {
                            state.lines
                        } else {
                            draftLyrics.lines().filter { it.isNotBlank() }.mapIndexed { index, text ->
                                LyricLine(
                                    id = "edit-$index",
                                    text = text.trim(),
                                    startMs = index * 5_000L,
                                )
                            }
                        },
                        onSave = { encoded ->
                            draftLyrics = encoded
                            viewModel.saveLyrics(encoded)
                            editingLyrics = false
                        },
                        extraActions = {
                            OutlinedButton(
                                onClick = {
                                    val pasted = readClipboardText()
                                    if (pasted != null) {
                                        draftLyrics = if (draftLyrics.isBlank()) pasted else "$draftLyrics\n$pasted"
                                    }
                                },
                            ) {
                                Text(copy.paste)
                            }
                            OutlinedButton(
                                onClick = {
                                    lyricsFileLauncher.launch(
                                        arrayOf("text/*", "application/octet-stream", "*/*"),
                                    )
                                },
                            ) {
                                Text("File")
                            }
                            TextButton(onClick = viewModel::loadSampleLyrics) {
                                Text("Sample")
                            }
                        },
                    )
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
                            TextButton(
                                onClick = {
                                    draftLyrics = state.rawLyrics
                                    editingLyrics = true
                                },
                            ) {
                                Text("Edit")
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
        }

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
                        Text(copy.mixSave)
                    }
                }
            }
        }

        if (state.takes.isNotEmpty()) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::cleanUnusedTakes,
                    enabled = state.takes.any { !it.active },
                ) {
                    Text(copy.cleanUnusedTakes)
                }
            }
            state.takes.forEachIndexed { index, take ->
                val previous = state.takes.getOrNull(index + 1)
                TakeEditCard(
                    take = take,
                    wavFile = File(File(projectsDir, sessionId), take.relativePath),
                    isPlaying = playingRelativePath == take.relativePath,
                    onPlay = { viewModel.togglePlayTake(take) },
                    onDelete = { pendingDeleteTake = take.id },
                    onPrefer = { viewModel.preferTake(take.id) },
                    onTrim = { start, duration -> viewModel.trimTake(take.id, start, duration) },
                    onPunch = { start -> viewModel.punchFrom(take, start) },
                    onPunchRegion = { start, end -> viewModel.punchRegion(take, start, end) },
                    onSplit = { at -> viewModel.splitTake(take.id, at) },
                    onSplitPlayhead = {
                        val at = take.sourceInMs + (mixPosition - take.timelineStartMs)
                            .coerceIn(250L, (take.durationMs - 250L).coerceAtLeast(250L))
                        viewModel.splitTake(take.id, at)
                    },
                    onCompare = previous?.let { other -> { viewModel.toggleCompareTake(take, other) } },
                    onMove = { at -> viewModel.moveTake(take.id, at) },
                    onFades = { fadeIn, fadeOut -> viewModel.setTakeFades(take.id, fadeIn, fadeOut) },
                    onRestore = { viewModel.restoreTake(take.id) },
                )
            }
        }
        }

    }
    }
    if (!showKaraoke) {
        EditorTransportBar(
            isPlaying = playingRelativePath != null,
            positionMs = mixPosition,
            durationMs = studioDurationMs(state),
            onPlayPause = {
                if (playingRelativePath != null) {
                    viewModel.stopMixPlayback()
                } else {
                    viewModel.hearPreferredWithTrack()
                }
            },
            onSeek = viewModel::seekMix,
        )
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
                showSingChooser = true
            }
        },
        onPause = viewModel::pauseTake,
        onResume = viewModel::resumeTake,
    )
    }
    if (showSingChooser) {
        SingChooserSheet(
            copy = copy,
            onDismiss = { showSingChooser = false },
            onSelect = { mode ->
                showSingChooser = false
                viewModel.prepareSingMode(
                    mode = mode,
                    onNeedSongFromPhone = {
                        rightsOk = true
                        showBackingDesk = true
                        recordAfterImport = true
                        pickBacking()
                    },
                    onNeedLyrics = { editingLyrics = true },
                    onReadyToRecord = { karaokePrep = true },
                )
            },
        )
    }
    if (showMicRationale) {
        PermissionRationale(
            title = copy.micNeeded,
            message = copy.micNeeded,
            grantLabel = copy.grantPermission,
            denyLabel = copy.notNow,
            onGrant = {
                recordPermissionLauncher.launch(neededRecordPermissions().toTypedArray())
            },
            onDeny = { showMicRationale = false },
        )
    }
    if (showReadRationale) {
        PermissionRationale(
            title = copy.singFromPhone,
            message = copy.storageNeeded,
            grantLabel = copy.grantPermission,
            denyLabel = copy.notNow,
            onGrant = {
                audioReadLauncher.launch(MediaPermissions.audioReadPermission())
            },
            onDeny = {
                showReadRationale = false
                backingFileLauncher.launch(arrayOf("audio/*"))
            },
        )
    }
    }
}
