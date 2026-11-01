package com.swaralab.feature.studio

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
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
)

data class BackingTrackUi(
    val name: String,
    val durationText: String,
    val relativePath: String,
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
    val audio: AudioEngine,
) : ViewModel() {
    private val uiState = MutableStateFlow<StudioUi?>(null)
    val ui: StateFlow<StudioUi?> = uiState.asStateFlow()
    private var activeLine = 0
    private var selectedPartId: String? = null
    private var stageNote: String? = null

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
            stageNote = null
            refresh()
        }
    }

    fun saveLyrics(text: String, note: String? = null) {
        viewModelScope.launch {
            activeLine = 0
            stageNote = note
            updateLyrics(sessionId, text)
            refresh()
        }
    }

    fun selectLine(index: Int) {
        val current = uiState.value ?: return
        if (index !in current.lines.indices) return
        activeLine = index
        uiState.value = current.copy(activeLine = index)
    }

    fun stepLine(delta: Int) {
        val current = uiState.value ?: return
        if (current.lines.isEmpty()) return
        val next = (current.activeLine + delta).coerceIn(0, current.lines.lastIndex)
        activeLine = next
        uiState.value = current.copy(activeLine = next)
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
                importBackingTrack(sessionId, relativePath, durationMs, fileName)
                stageNote = "Backing track loaded ($fileName). It will play while you sing."
                refresh()
            } else {
                stageNote = "Could not load that audio file."
                uiState.value = uiState.value?.copy(stageNote = stageNote)
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
                    writeSampleDroneWav(destination, sampleRate = 48_000, durationSeconds = 16)
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                importBackingTrack(sessionId, relativePath, durationMs, "Tanpura Drone · C (Sa)")
                stageNote = "Sample Tanpura backing loaded. Tap Preview or hit Sing."
                refresh()
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
            if (audio.state.value.status == EngineStatus.RECORDING) {
                val finished = audio.stopTake()
                if (finished != null) {
                    commitRecordedTake(sessionId, current.selectedPartId, finished)
                    stageNote = "Saved take (${formatDuration(finished.durationMs)}). Tap Play below to hear it."
                } else {
                    stageNote = "Take stopped."
                }
                refresh()
            } else {
                audio.arm(
                    ArmRequest(
                        sessionId = sessionId,
                        output = current.route,
                        backingRelativePath = current.backing?.relativePath,
                    ),
                )
                stageNote = when (audio.startTake()) {
                    is TakeControl.Started -> "Recording live. Sing into the mic and tap Stop when done."
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

    fun onPermissionDenied() {
        val current = uiState.value ?: return
        stageNote = "Microphone permission is needed to record your voice."
        uiState.value = current.copy(stageNote = stageNote)
    }

    fun togglePlayClip(relativePath: String) {
        viewModelScope.launch {
            audio.playClip(sessionId, relativePath)
        }
    }

    fun stopAudioOnExit() {
        audio.disarm()
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
                )
            }
            audio.arm(
                ArmRequest(
                    sessionId = document.id,
                    output = route,
                    backingRelativePath = backingClip?.sourceRelativePath,
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
                        durationText = formatDuration(clip.durationMs),
                        relativePath = clip.sourceRelativePath,
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
            audio: AudioEngine,
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
                    audio = audio,
                )
            }
        }
    }
}

private val studioPresets = listOf(
    VocalPreset.RAW,
    VocalPreset.CLOSE,
    VocalPreset.STAGE,
    VocalPreset.RADIO,
)

private val listeningRoutes = listOf(
    GearRoute.WIRED,
    GearRoute.USB,
    GearRoute.BLUETOOTH,
    GearRoute.SPEAKER,
)

@OptIn(ExperimentalLayoutApi::class)
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
    audio: AudioEngine,
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
            audio = audio,
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val playingRelativePath by remember(viewModel.audio) {
        viewModel.audio.state.map { it.playingRelativePath }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val state = ui ?: return
    val context = LocalContext.current

    var editingTitle by remember { mutableStateOf(false) }
    var draftTitle by remember(state.title) { mutableStateOf(state.title) }
    var editingLyrics by remember { mutableStateOf(false) }
    var draftLyrics by remember(state.rawLyrics) { mutableStateOf(state.rawLyrics) }
    var pickingRoute by remember { mutableStateOf(false) }

    fun readClipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        return item?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(viewModel) {
        viewModel.audio.state
            .map { (it.status == EngineStatus.RECORDING) to (it.elapsedMs / 250L) }
            .distinctUntilChanged()
            .collect { (recording, quarterSec) ->
                if (recording) {
                    viewModel.syncTimedLyric(quarterSec * 250L)
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioOnExit()
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

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
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
                ChoiceCard(
                    title = route.label(),
                    body = listeningCopy(route),
                    selected = state.route == route,
                    onClick = {
                        viewModel.selectRoute(route)
                        pickingRoute = false
                    },
                )
            }
        }

        if (state.kind == SessionKind.PRACTICE) {
            WarmupScaleCoachCard(
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = "Backing track", style = MaterialTheme.typography.titleMedium)
                        val backing = state.backing
                        Text(
                            text = if (backing != null) {
                                "${backing.name} · ${backing.durationText}"
                            } else {
                                "Optional: upload an instrumental or karaoke track from your phone."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwaraColors.Muted,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.backing == null) {
                            TextButton(onClick = viewModel::loadSampleBacking) {
                                Text("Sample drone")
                            }
                        } else {
                            val isPlayingBacking = playingRelativePath == state.backing.relativePath
                            TextButton(onClick = { viewModel.togglePlayClip(state.backing.relativePath) }) {
                                Text(if (isPlayingBacking) "Stop" else "Preview")
                            }
                        }
                        TextButton(
                            onClick = {
                                backingFileLauncher.launch(arrayOf("audio/*"))
                            },
                        ) {
                            Text(if (state.backing == null) "Upload track" else "Change")
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Lyrics", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        ) {
                            Text("Paste")
                        }
                        TextButton(
                            onClick = {
                                lyricsFileLauncher.launch(
                                    arrayOf("text/*", "application/octet-stream", "*/*"),
                                )
                            },
                        ) {
                            Text("Upload file")
                        }
                        TextButton(
                            onClick = {
                                draftLyrics = state.rawLyrics
                                editingLyrics = !editingLyrics
                            },
                        ) {
                            Text(if (editingLyrics) "Close" else if (state.lines.isEmpty()) "Write" else "Edit")
                        }
                    }
                }
                if (editingLyrics) {
                    OutlinedTextField(
                        value = draftLyrics,
                        onValueChange = { draftLyrics = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        label = { Text("Paste or type lyrics here (one line per row)") },
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
                        text = "Copy lyrics from anywhere and tap Paste, upload a .txt / .lrc file, or type your lines below.",
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
                            text = "Line ${state.activeLine + 1} of ${state.lines.size}",
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
                                maxLines = 1,
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

        RecordingControlBar(
            audioStateFlow = viewModel.audio.state,
            stageNote = state.stageNote,
            onSingClick = { currentlyRecording ->
                if (currentlyRecording) {
                    viewModel.toggleRecording()
                } else {
                    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.toggleRecording()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
        )

        LiveVocalMonitorCard(
            audioStateFlow = viewModel.audio.state,
        )

        if (state.takes.isNotEmpty()) {
            Text(text = "Recorded takes", style = MaterialTheme.typography.titleLarge)
            state.takes.forEach { take ->
                val isPlaying = playingRelativePath == take.relativePath
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPlaying) SwaraColors.TealWash else SwaraColors.Card,
                    border = BorderStroke(1.dp, if (isPlaying) SwaraColors.Teal else SwaraColors.Line),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { viewModel.deleteTake(take.id) }) {
                                Text("Delete", color = SwaraColors.Clay)
                            }
                            Button(onClick = { viewModel.togglePlayClip(take.relativePath) }) {
                                Text(if (isPlaying) "Stop" else "Play")
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Voice style", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onPublish) {
                Text("FX sliders")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presetsToShow = if (state.preset == VocalPreset.CUSTOM) {
                studioPresets + VocalPreset.CUSTOM
            } else {
                studioPresets
            }
            presetsToShow.forEach { preset ->
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
                )
            }
        }
        Text(
            text = state.preset.description(),
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )

        Text(text = "Voices", style = MaterialTheme.typography.titleLarge)
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

        OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
            Text("Mix & Save song")
        }
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
    onSingClick: (Boolean) -> Unit,
) {
    val isRecording by remember(audioStateFlow) {
        audioStateFlow.map { it.status == EngineStatus.RECORDING }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val elapsedSeconds by remember(audioStateFlow) {
        audioStateFlow.map { it.elapsedMs / 1000L }.distinctUntilChanged()
    }.collectAsState(initial = 0L)

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
                    .clip(CircleShape),
                color = if (isRecording) SwaraColors.Clay else SwaraColors.Teal,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isRecording) "Stop" else "Sing",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRecording) {
                        "Recording live · ${formatDuration(elapsedSeconds * 1000L)}"
                    } else {
                        "Stage microphone ready"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (isRecording) SwaraColors.Clay else SwaraColors.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stageNote
                        ?: if (isRecording) {
                            "Sing along with your lyrics. Tap Stop to save the take."
                        } else {
                            "Tap Sing to record your voice live."
                        },
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (calmActive) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Live Pitch & Sound Graph",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        color = SwaraColors.Ink,
                    )
                    Text(
                        text = if (calmMetrics.pitchHz > 0f) {
                            val tuning = when {
                                abs(calmMetrics.centsOffset) <= 12 -> "In tune"
                                calmMetrics.centsOffset < 0 -> "Flat (${calmMetrics.centsOffset}c)"
                                else -> "Sharp (+${calmMetrics.centsOffset}c)"
                            }
                            "${calmMetrics.pitchHz.roundToInt()} Hz · $tuning · ${calmMetrics.rmsDb.roundToInt()} dB"
                        } else {
                            "Sing or play a take to see your live pitch"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                }
                Surface(
                    modifier = Modifier.width(112.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (calmActive) SwaraColors.TealWash else SwaraColors.Paper,
                ) {
                    Text(
                        text = if (calmMetrics.noteName != "—") {
                            "${calmMetrics.noteName} · ${calmMetrics.swaraName}"
                        } else {
                            calmModeLabel
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (calmActive) SwaraColors.Teal else SwaraColors.Muted,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                shape = RoundedCornerShape(14.dp),
                color = SwaraColors.Paper,
                border = BorderStroke(1.dp, SwaraColors.Line),
            ) {
                PitchAndWaveformCanvas(audioState = audioState)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EqBandRow(
                    label = "Bass (Warmth)",
                    levelProvider = { audioState.value.metrics.bassLevel },
                    calmPercent = (calmMetrics.bassLevel * 100).roundToInt().coerceIn(0, 100),
                    color = SwaraColors.Teal,
                )
                EqBandRow(
                    label = "Vocal Mid (Clarity)",
                    levelProvider = { audioState.value.metrics.midLevel },
                    calmPercent = (calmMetrics.midLevel * 100).roundToInt().coerceIn(0, 100),
                    color = SwaraColors.Gold,
                )
                EqBandRow(
                    label = "Treble (Air & Presence)",
                    levelProvider = { audioState.value.metrics.trebleLevel },
                    calmPercent = (calmMetrics.trebleLevel * 100).roundToInt().coerceIn(0, 100),
                    color = SwaraColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun PitchAndWaveformCanvas(
    audioState: State<AudioEngineState>,
) {
    val path = remember { Path() }
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)) {
        val metrics = audioState.value.metrics
        val width = size.width
        val height = size.height
        listOf(0.22f, 0.5f, 0.78f).forEach { frac ->
            val y = height * frac
            drawLine(
                color = SwaraColors.Line,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val wave = metrics.waveformHistory
        val pitches = metrics.pitchHistory
        val slots = 42
        val stepX = if (slots > 1) width / (slots - 1).toFloat() else width

        wave.forEachIndexed { idx, amp ->
            val x = idx * stepX
            val barHalf = (amp.coerceIn(0.03f, 1f) * height * 0.44f)
            drawLine(
                color = SwaraColors.Teal.copy(alpha = 0.35f),
                start = Offset(x, (height / 2f) - barHalf),
                end = Offset(x, (height / 2f) + barHalf),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        path.reset()
        var penDown = false
        pitches.forEachIndexed { idx, normPitch ->
            val x = idx * stepX
            if (normPitch > 0.01f) {
                val y = height * (1f - normPitch.coerceIn(0.05f, 0.95f))
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                drawCircle(
                    color = SwaraColors.Gold,
                    radius = 2.8.dp.toPx(),
                    center = Offset(x, y),
                )
            } else {
                penDown = false
            }
        }
        drawPath(
            path = path,
            color = SwaraColors.Gold,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun EqBandRow(
    label: String,
    levelProvider: () -> Float,
    calmPercent: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.46f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
        Canvas(
            modifier = Modifier
                .weight(0.40f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            drawRect(color = SwaraColors.Line)
            val fillWidth = size.width * levelProvider().coerceIn(0.03f, 1f)
            drawRect(
                color = color,
                size = Size(fillWidth, size.height),
            )
        }
        Text(
            text = "$calmPercent%",
            modifier = Modifier.width(46.dp),
            maxLines = 1,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = SwaraColors.Ink,
        )
    }
}

private data class ScaleTarget(
    val swara: String,
    val note: String,
    val hz: Float,
)

private val cMajorSwaraScale = listOf(
    ScaleTarget("Sa", "C4", 261.63f),
    ScaleTarget("Re", "D4", 293.66f),
    ScaleTarget("Ga", "E4", 329.63f),
    ScaleTarget("Ma", "F4", 349.23f),
    ScaleTarget("Pa", "G4", 392.00f),
    ScaleTarget("Dha", "A4", 440.00f),
    ScaleTarget("Ni", "B4", 493.88f),
    ScaleTarget("Sa'", "C5", 523.25f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarmupScaleCoachCard(
    audioStateFlow: StateFlow<AudioEngineState>,
    backing: BackingTrackUi?,
    playingRelativePath: String?,
    onEnableDrone: () -> Unit,
    onToggleDrone: (String) -> Unit,
) {
    var selectedIdx by remember { mutableStateOf(0) }
    var calmPitchHz by remember { mutableStateOf(0f) }
    val target = cMajorSwaraScale[selectedIdx]

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
                        text = "Target: ${target.swara} (${target.note} · ${target.hz.roundToInt()} Hz)",
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
                cMajorSwaraScale.forEachIndexed { idx, item ->
                    FilterChip(
                        selected = selectedIdx == idx,
                        onClick = { selectedIdx = idx },
                        label = { Text("${item.swara} (${item.note})") },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isHit) SwaraColors.TealWash else SwaraColors.Paper,
            ) {
                val feedback = when {
                    centsDiff == null -> "Tap Sing below and hold ${target.swara} (${target.note})"
                    abs(centsDiff) <= 45 -> "Locked on ${target.swara} (${target.note})! Hold steady."
                    centsDiff < 0 -> "Sing slightly higher ↑ to reach ${target.swara} (${target.note})"
                    else -> "Sing slightly lower ↓ to reach ${target.swara} (${target.note})"
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
        }
    }
}

private val videoFrameTargets = listOf(
    PublishTarget.REELS,
    PublishTarget.SHORTS,
    PublishTarget.WHATSAPP,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerticalVideoStageCard(
    title: String,
    target: PublishTarget,
    activeLyric: String,
    nextLyric: String,
    audioStateFlow: StateFlow<AudioEngineState>,
    onSelectTarget: (PublishTarget) -> Unit,
) {
    val audioState = audioStateFlow.collectAsState()
    val effectiveTarget = if (target in videoFrameTargets) target else PublishTarget.REELS

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
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
                    Text(text = "9:16 Vertical Video Stage", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Live safe-frame preview with synced lyrics overlay",
                        maxLines = 1,
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

            // 9:16 vertical stage preview box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                shape = RoundedCornerShape(18.dp),
                color = SwaraColors.Ink,
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val m = audioState.value.metrics
                        val amp = (m.midLevel * 0.6f + m.bassLevel * 0.4f).coerceIn(0.08f, 1f)
                        val center = Offset(size.width / 2f, size.height * 0.44f)
                        drawCircle(
                            color = SwaraColors.Teal.copy(alpha = 0.25f),
                            radius = size.minDimension * (0.22f + amp * 0.22f),
                            center = center,
                        )
                        drawCircle(
                            color = SwaraColors.Gold.copy(alpha = 0.35f),
                            radius = size.minDimension * (0.14f + amp * 0.14f),
                            center = center,
                            style = Stroke(width = 2.dp.toPx()),
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun writeSampleDroneWav(file: File, sampleRate: Int, durationSeconds: Int) {
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
        // Pure harmonic C3 (130.81 Hz) + C4 (261.63 Hz) tanpura drone so pitch tracking is rock-steady
        val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            if (chunk.remaining() < 2) {
                raf.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
            val t = i.toDouble() / sampleRate.toDouble()
            val swell = 0.82 + 0.18 * sin(2.0 * PI * 0.20 * t)
            val wave = (
                0.72 * sin(2.0 * PI * 130.81 * t) +
                    0.28 * sin(2.0 * PI * 261.63 * t)
                ) * swell
            val pcm = (wave * 9500.0).toInt().coerceIn(-32767, 32767).toShort()
            chunk.putShort(pcm)
        }
        if (chunk.position() > 0) {
            raf.write(chunk.array(), 0, chunk.position())
        }
    }
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
    VocalPreset.RAW -> "Natural"
    VocalPreset.CLOSE -> "Studio"
    VocalPreset.STAGE -> "Hall"
    VocalPreset.RADIO -> "Bright"
    VocalPreset.CUSTOM -> "Custom"
}

private fun VocalPreset.description(): String = when (this) {
    VocalPreset.RAW -> "Your dry voice with no room added."
    VocalPreset.CLOSE -> "Warm and close, like a booth."
    VocalPreset.STAGE -> "A fuller room around long notes."
    VocalPreset.RADIO -> "Clear and forward on a phone speaker."
    VocalPreset.CUSTOM -> "Custom reverb, echo, warmth, and ducking sliders active."
}

private fun listeningCopy(output: GearRoute): String = when (output) {
    GearRoute.WIRED -> "Best for singing in time."
    GearRoute.USB -> "Direct monitoring through a USB mic or interface."
    GearRoute.BLUETOOTH -> "Good for playback. Singing along over Bluetooth feels late."
    GearRoute.SPEAKER -> "Fine for solo takes. Use headphones when singing over a backing track."
    GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN -> "Plug in headphones before you sing."
}
