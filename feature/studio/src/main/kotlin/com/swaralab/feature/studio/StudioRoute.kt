package com.swaralab.feature.studio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

data class StudioUi(
    val title: String,
    val kind: SessionKind,
    val preset: VocalPreset,
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
    private val loadSession: LoadSession,
    private val renameSession: RenameSession,
    private val updateGear: UpdateGear,
    private val updateVocalPreset: UpdateVocalPreset,
    private val updateLyrics: UpdateLyrics,
    private val addVoicePart: AddVoicePart,
    private val commitRecordedTake: CommitRecordedTake,
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
                audio.arm(ArmRequest(sessionId = sessionId, output = current.route))
                stageNote = when (audio.startTake()) {
                    is TakeControl.Started -> "Recording live. Sing into the mic and tap Stop when done."
                    is TakeControl.Refused ->
                        "Plug in headphones first so the backing track does not bleed into the mic."
                    TakeControl.TransportUnavailable ->
                        "Could not open the microphone on this device."
                }
                uiState.value = current.copy(stageNote = stageNote)
            }
        }
    }

    fun onPermissionDenied() {
        val current = uiState.value ?: return
        stageNote = "Microphone permission is needed to record your voice."
        uiState.value = current.copy(stageNote = stageNote)
    }

    fun togglePlayTake(relativePath: String) {
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
            val backingClip = document.tracks
                .firstOrNull { it.role == TrackRole.BACKING }
                ?.clips
                ?.firstOrNull()
                ?.sourceRelativePath
            audio.arm(
                ArmRequest(
                    sessionId = document.id,
                    output = route,
                    backingRelativePath = backingClip,
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
                preset = document.chain.preset.takeUnless { it == VocalPreset.CUSTOM } ?: VocalPreset.RAW,
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
            loadSession: LoadSession,
            renameSession: RenameSession,
            updateGear: UpdateGear,
            updateVocalPreset: UpdateVocalPreset,
            updateLyrics: UpdateLyrics,
            addVoicePart: AddVoicePart,
            commitRecordedTake: CommitRecordedTake,
            audio: AudioEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StudioViewModel(
                    sessionId = sessionId,
                    loadSession = loadSession,
                    renameSession = renameSession,
                    updateGear = updateGear,
                    updateVocalPreset = updateVocalPreset,
                    updateLyrics = updateLyrics,
                    addVoicePart = addVoicePart,
                    commitRecordedTake = commitRecordedTake,
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
    loadSession: LoadSession,
    renameSession: RenameSession,
    updateGear: UpdateGear,
    updateVocalPreset: UpdateVocalPreset,
    updateLyrics: UpdateLyrics,
    addVoicePart: AddVoicePart,
    commitRecordedTake: CommitRecordedTake,
    audio: AudioEngine,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.factory(
            sessionId = sessionId,
            loadSession = loadSession,
            renameSession = renameSession,
            updateGear = updateGear,
            updateVocalPreset = updateVocalPreset,
            updateLyrics = updateLyrics,
            addVoicePart = addVoicePart,
            commitRecordedTake = commitRecordedTake,
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
                        label = { Text("One line per row (or [mm:ss.xx] LRC lines)") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                lyricsFileLauncher.launch(
                                    arrayOf("text/*", "application/octet-stream", "*/*"),
                                )
                            },
                        ) {
                            Text("Upload .txt / .lrc")
                        }
                    }
                } else if (state.lines.isEmpty()) {
                    Text(
                        text = "Upload a lyrics file (.txt or .lrc) or write the lines you want in front of you while you sing.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                lyricsFileLauncher.launch(
                                    arrayOf("text/*", "application/octet-stream", "*/*"),
                                )
                            },
                        ) {
                            Text("Upload lyrics file")
                        }
                        OutlinedButton(
                            onClick = {
                                draftLyrics = state.rawLyrics
                                editingLyrics = true
                            },
                        ) {
                            Text("Paste or write")
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
                    state.lines.forEachIndexed { index, line ->
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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

        Text(text = "Voice style", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            studioPresets.forEach { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { viewModel.selectPreset(preset) },
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

        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = if (isRecording) SwaraColors.TealWash else SwaraColors.Card,
            border = BorderStroke(1.dp, if (isRecording) SwaraColors.Teal else SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isRecording) {
                    Text(
                        text = "Recording live · ${formatDuration(audioState.elapsedMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SwaraColors.Clay,
                    )
                    LinearProgressIndicator(
                        progress = { audioState.peak.coerceIn(0.04f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = SwaraColors.Teal,
                        trackColor = SwaraColors.Line,
                    )
                }
                Surface(
                    onClick = {
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
                    modifier = Modifier
                        .size(88.dp)
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
                    text = state.stageNote
                        ?: if (isRecording) {
                            "Sing along with your lyrics above. Tap Stop to save the take."
                        } else {
                            "Tap Sing to start recording your voice live."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
        }

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
                        Column {
                            Text(text = take.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = take.durationText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwaraColors.Muted,
                            )
                        }
                        Button(onClick = { viewModel.togglePlayTake(take.relativePath) }) {
                            Text(if (isPlaying) "Stop" else "Play")
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
            Text("Share format")
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
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
    VocalPreset.CUSTOM -> "Custom voice settings."
}

private fun listeningCopy(output: GearRoute): String = when (output) {
    GearRoute.WIRED -> "Best for singing in time."
    GearRoute.USB -> "Direct monitoring through a USB mic or interface."
    GearRoute.BLUETOOTH -> "Good for playback. Singing along over Bluetooth feels late."
    GearRoute.SPEAKER -> "Fine for solo takes. Use headphones when singing over a backing track."
    GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN -> "Plug in headphones before you sing."
}
