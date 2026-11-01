package com.swaralab.feature.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.usecase.AddVoicePart
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

data class StudioUi(
    val title: String,
    val kind: SessionKind,
    val preset: VocalPreset,
    val lines: List<String>,
    val rawLyrics: String,
    val activeLine: Int,
    val parts: List<String>,
    val canAddPart: Boolean,
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
    private val audio: AudioEngine,
) : ViewModel() {
    private val uiState = MutableStateFlow<StudioUi?>(null)
    val ui: StateFlow<StudioUi?> = uiState.asStateFlow()
    private var activeLine = 0
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

    fun saveLyrics(text: String) {
        viewModelScope.launch {
            activeLine = 0
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

    fun addSecondVoice() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val voices = document.tracks.count { it.role != TrackRole.BACKING }
            val (role, name) = when (voices) {
                1 -> TrackRole.HARMONY to "Part 2"
                else -> TrackRole.DOUBLE to "Harmony"
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

    fun tapMic() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            audio.arm(ArmRequest(sessionId = sessionId, output = current.route))
            stageNote = when (audio.startTake()) {
                is TakeControl.Started -> "Recording."
                is TakeControl.Refused ->
                    "Plug in headphones first so the music is not recorded with your voice."
                TakeControl.TransportUnavailable ->
                    "Your lyrics and voice style are saved. Live recording lands in the next update."
            }
            uiState.value = current.copy(stageNote = stageNote)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val route = document.gear.output.takeUnless { it == GearRoute.UNKNOWN } ?: GearRoute.WIRED
            if (document.gear.output == GearRoute.UNKNOWN) {
                updateGear(sessionId, GearRoute.WIRED)
            }
            audio.arm(ArmRequest(sessionId = document.id, output = route))
            val lines = document.lyrics.lines.map { it.text }
            val voiceTracks = document.tracks.filter { it.role != TrackRole.BACKING }
            val clampedLine = activeLine.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            activeLine = clampedLine
            uiState.value = StudioUi(
                title = document.title,
                kind = document.kind,
                preset = document.chain.preset.takeUnless { it == VocalPreset.CUSTOM } ?: VocalPreset.RAW,
                lines = lines,
                rawLyrics = document.lyrics.rawText,
                activeLine = clampedLine,
                parts = voiceTracks.mapIndexed { index, track ->
                    if (index == 0) "Part 1 · Lead" else track.name
                },
                canAddPart = voiceTracks.size < 3,
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

@Composable
fun StudioRoute(
    sessionId: String,
    loadSession: LoadSession,
    renameSession: RenameSession,
    updateGear: UpdateGear,
    updateVocalPreset: UpdateVocalPreset,
    updateLyrics: UpdateLyrics,
    addVoicePart: AddVoicePart,
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
            audio = audio,
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val state = ui ?: return
    var editingTitle by remember { mutableStateOf(false) }
    var draftTitle by remember(state.title) { mutableStateOf(state.title) }
    var editingLyrics by remember { mutableStateOf(false) }
    var draftLyrics by remember(state.rawLyrics) { mutableStateOf(state.rawLyrics) }
    var pickingRoute by remember { mutableStateOf(false) }

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
                    TextButton(
                        onClick = {
                            draftLyrics = state.rawLyrics
                            editingLyrics = !editingLyrics
                        },
                    ) {
                        Text(if (editingLyrics) "Close" else if (state.lines.isEmpty()) "Add lyrics" else "Edit")
                    }
                }
                if (editingLyrics) {
                    OutlinedTextField(
                        value = draftLyrics,
                        onValueChange = { draftLyrics = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        label = { Text("One line per row") },
                    )
                    Button(
                        onClick = {
                            viewModel.saveLyrics(draftLyrics)
                            editingLyrics = false
                        },
                    ) {
                        Text("Save lyrics")
                    }
                } else if (state.lines.isEmpty()) {
                    Text(
                        text = "Paste or write the lines you want in front of you while you sing.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Muted,
                    )
                } else {
                    state.lines.forEachIndexed { index, line ->
                        val active = index == state.activeLine
                        Surface(
                            onClick = { viewModel.selectLine(index) },
                            color = if (active) SwaraColors.TealWash else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = line,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.parts.forEach { part ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SwaraColors.TealWash,
                ) {
                    Text(
                        text = part,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Teal,
                    )
                }
            }
            if (state.canAddPart) {
                OutlinedButton(onClick = viewModel::addSecondVoice) {
                    Text(if (state.parts.size == 1) "+ Duet part" else "+ Harmony")
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = viewModel::tapMic,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape),
                color = SwaraColors.Teal,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sing",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            state.stageNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
        }

        OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
            Text("Share format")
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
    VocalPreset.CUSTOM -> "Custom voice settings."
}

private fun listeningCopy(output: GearRoute): String = when (output) {
    GearRoute.WIRED -> "Best for singing in time."
    GearRoute.USB -> "Direct monitoring through a USB mic or interface."
    GearRoute.BLUETOOTH -> "Good for playback. Singing along over Bluetooth feels late."
    GearRoute.SPEAKER -> "Muted while you sing so the track does not bleed into the mic."
    GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN -> "Plug in headphones before you sing."
}
