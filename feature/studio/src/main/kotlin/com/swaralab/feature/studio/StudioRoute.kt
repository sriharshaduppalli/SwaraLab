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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaralab.domain.audio.VocalMetrics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
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
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
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
                importBackingTrack(sessionId, relativePath, durationMs, "Tanpura Drone · C (Sa-Pa)")
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
        audio.stopPlayback()
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
            audio = audio,
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val audioState by viewModel.audio.state.collectAsState()
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

    val isRecording = audioState.status == EngineStatus.RECORDING

    LaunchedEffect(audioState.elapsedMs, isRecording) {
        if (isRecording) {
            viewModel.syncTimedLyric(audioState.elapsedMs)
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

        if (state.kind != SessionKind.PRACTICE) {
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
                            val isPlayingBacking = audioState.playingRelativePath == state.backing.relativePath
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
                    val windowStart = (state.activeLine - 1).coerceAtLeast(0)
                    val windowEnd = (windowStart + 2).coerceAtMost(state.lines.lastIndex)
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
                                style = if (active) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (active) SwaraColors.Teal else SwaraColors.Muted,
                            )
                        }
                    }
                }
            }
        }

        LiveVocalMonitorCard(
            metrics = audioState.metrics,
            isRecording = isRecording,
            elapsedText = formatDuration(audioState.elapsedMs),
            stageNote = state.stageNote,
            modeLabel = when {
                isRecording -> "LIVE MIC"
                audioState.playingRelativePath != null -> "PLAYBACK"
                else -> "READY"
            },
            onSingClick = {
                if (isRecording) {
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

        if (state.takes.isNotEmpty()) {
            Text(text = "Recorded takes", style = MaterialTheme.typography.titleLarge)
            state.takes.forEach { take ->
                val isPlaying = audioState.playingRelativePath == take.relativePath
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

@Composable
private fun LiveVocalMonitorCard(
    metrics: VocalMetrics,
    isRecording: Boolean,
    elapsedText: String,
    stageNote: String?,
    modeLabel: String,
    onSingClick: () -> Unit,
) {
    val isActive = isRecording || modeLabel == "PLAYBACK"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (isActive) SwaraColors.Teal else SwaraColors.Line),
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isRecording) "Recording Live · $elapsedText" else "Live Pitch & Sound Graph",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isRecording) SwaraColors.Clay else SwaraColors.Ink,
                    )
                    Text(
                        text = if (metrics.pitchHz > 0f) {
                            val tuning = when {
                                kotlin.math.abs(metrics.centsOffset) <= 10 -> "In tune"
                                metrics.centsOffset < 0 -> "Flat (${metrics.centsOffset}c)"
                                else -> "Sharp (+${metrics.centsOffset}c)"
                            }
                            "${metrics.pitchHz.toInt()} Hz · $tuning · ${metrics.rmsDb.toInt()} dB"
                        } else {
                            "Sing or play a take to see your pitch, Swara, and tone balance"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) SwaraColors.TealWash else SwaraColors.Paper,
                ) {
                    Text(
                        text = if (metrics.noteName != "—") {
                            "${metrics.noteName} · ${metrics.swaraName}"
                        } else {
                            modeLabel
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) SwaraColors.Teal else SwaraColors.Muted,
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
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)) {
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

                    val path = Path()
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EqBandRow(
                    label = "Bass (Warmth)",
                    level = metrics.bassLevel,
                    color = SwaraColors.Teal,
                )
                EqBandRow(
                    label = "Vocal Mid (Clarity)",
                    level = metrics.midLevel,
                    color = SwaraColors.Gold,
                )
                EqBandRow(
                    label = "Treble (Air & Presence)",
                    level = metrics.trebleLevel,
                    color = SwaraColors.Ink,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    onClick = onSingClick,
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
                Text(
                    text = stageNote
                        ?: if (isRecording) {
                            "Sing along with your lyrics above. Tap Stop to save the take."
                        } else {
                            "Tap Sing to start recording your voice live."
                        },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun EqBandRow(
    label: String,
    level: Float,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.45f),
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
        LinearProgressIndicator(
            progress = { level.coerceIn(0.03f, 1f) },
            modifier = Modifier
                .weight(0.42f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = SwaraColors.Line,
        )
        Text(
            text = "${(level * 100).toInt().coerceIn(0, 100)}%",
            modifier = Modifier.weight(0.13f),
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Ink,
        )
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
        // Warm C3 (130.81 Hz) + G3 (196.00 Hz) + C4 (261.63 Hz) tanpura drone
        val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            if (chunk.remaining() < 2) {
                raf.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
            val t = i.toDouble() / sampleRate.toDouble()
            val swell = 0.75 + 0.25 * sin(2.0 * PI * 0.25 * t)
            val wave = (
                0.50 * sin(2.0 * PI * 130.81 * t) +
                    0.32 * sin(2.0 * PI * 196.00 * t) +
                    0.18 * sin(2.0 * PI * 261.63 * t)
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
