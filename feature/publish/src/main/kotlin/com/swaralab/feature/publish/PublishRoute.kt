package com.swaralab.feature.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateVocalChain
import com.swaralab.domain.usecase.UpdateVocalSyncOffset
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class TrackMixRowUi(
    val id: String,
    val label: String,
    val clipCount: Int,
    val gainDb: Float,
    val mute: Boolean,
)

data class PublishUi(
    val title: String,
    val target: PublishTarget,
    val tracks: List<TrackMixRowUi>,
    val chain: VocalChain,
    val syncOffsetMs: Float,
    val exportedRelativePath: String?,
    val exportedFileName: String?,
    val status: String?,
    val busy: Boolean = false,
)

class PublishViewModel(
    private val sessionId: String,
    private val projectsDir: File,
    private val loadSession: LoadSession,
    private val updatePublish: UpdatePublishSettings,
    private val updateTrackMix: UpdateTrackMix,
    private val updateVocalChain: UpdateVocalChain,
    private val updateVocalSyncOffset: UpdateVocalSyncOffset,
    private val render: RenderEngine,
    val audio: AudioEngine,
) : ViewModel() {
    private val uiState = MutableStateFlow<PublishUi?>(null)
    val ui: StateFlow<PublishUi?> = uiState.asStateFlow()
    private var exportedRelativePath: String? = null
    private var status: String? = null

    init {
        refresh()
    }

    fun select(target: PublishTarget) {
        viewModelScope.launch {
            updatePublish(sessionId, target)
            status = null
            refresh()
        }
    }

    fun setTrackGain(trackId: String, gainDb: Float) {
        val current = uiState.value ?: return
        val row = current.tracks.firstOrNull { it.id == trackId } ?: return
        viewModelScope.launch {
            updateTrackMix(sessionId, trackId, gainDb, row.mute)
            refresh()
        }
    }

    fun toggleTrackMute(trackId: String) {
        val current = uiState.value ?: return
        val row = current.tracks.firstOrNull { it.id == trackId } ?: return
        viewModelScope.launch {
            updateTrackMix(sessionId, trackId, row.gainDb, !row.mute)
            refresh()
        }
    }

    fun setSyncOffset(offsetMs: Float) {
        viewModelScope.launch {
            updateVocalSyncOffset(sessionId, offsetMs.toDouble())
            refresh()
        }
    }

    fun updateChainSliders(
        reverb: Float = uiState.value?.chain?.reverb ?: 0f,
        delay: Float = uiState.value?.chain?.delay ?: 0f,
        compression: Float = uiState.value?.chain?.compression ?: 0f,
        denoise: Float = uiState.value?.chain?.denoise ?: 0f,
        backingDuckDb: Float = uiState.value?.chain?.backingDuckDb ?: 0f,
    ) {
        viewModelScope.launch {
            updateVocalChain(
                id = sessionId,
                reverb = reverb,
                delay = delay,
                compression = compression,
                denoise = denoise,
                backingDuckDb = backingDuckDb,
            )
            refresh()
        }
    }

    fun prepare() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (current.busy) return@launch
            audio.stopPlayback()
            uiState.value = current.copy(busy = true, status = "Mixing backing music & vocal tracks...")
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(document, ExportRequest(document.publish.target))) {
                is ExportResult.Written -> {
                    exportedRelativePath = result.relativePath
                    status = "Master mix ready (${result.durationMs / 1000}s). Tap Play master or Save to phone."
                }
                is ExportResult.Waiting -> {
                    status = result.message
                }
            }
            refresh()
        }
    }

    fun togglePlayExport() {
        val path = exportedRelativePath ?: return
        viewModelScope.launch {
            audio.playClip(sessionId, path)
        }
    }

    fun copyExportToUri(writeBytes: (File) -> Boolean) {
        val path = exportedRelativePath ?: return
        val source = File(File(projectsDir, sessionId), path)
        if (!source.isFile) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { writeBytes(source) }
            status = if (ok) {
                "Saved master WAV file to your phone."
            } else {
                "Could not save the file."
            }
            refresh()
        }
    }

    fun stopAudioOnExit() {
        audio.stopPlayback()
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val publish = document.publish
            val safeTitle = document.title
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "song" }
            var voiceIndex = 0
            val trackRows = document.tracks
                .filter { it.role != TrackRole.BACKING || it.clips.isNotEmpty() }
                .map { track ->
                    val label = if (track.role == TrackRole.BACKING) {
                        "Backing music"
                    } else {
                        voiceIndex += 1
                        if (voiceIndex == 1) "Part 1 · Lead vocal" else track.name
                    }
                    TrackMixRowUi(
                        id = track.id,
                        label = label,
                        clipCount = track.clips.size,
                        gainDb = track.gainDb,
                        mute = track.mute,
                    )
                }
            uiState.value = PublishUi(
                title = document.title,
                target = publish.target,
                tracks = trackRows,
                chain = document.chain,
                syncOffsetMs = document.latency.compensationMs.toFloat(),
                exportedRelativePath = exportedRelativePath,
                exportedFileName = "$safeTitle-${publish.target.name.lowercase()}.wav",
                status = status,
                busy = false,
            )
        }
    }

    companion object {
        fun factory(
            sessionId: String,
            projectsDir: File,
            loadSession: LoadSession,
            updatePublish: UpdatePublishSettings,
            updateTrackMix: UpdateTrackMix,
            updateVocalChain: UpdateVocalChain,
            updateVocalSyncOffset: UpdateVocalSyncOffset,
            render: RenderEngine,
            audio: AudioEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PublishViewModel(
                    sessionId = sessionId,
                    projectsDir = projectsDir,
                    loadSession = loadSession,
                    updatePublish = updatePublish,
                    updateTrackMix = updateTrackMix,
                    updateVocalChain = updateVocalChain,
                    updateVocalSyncOffset = updateVocalSyncOffset,
                    render = render,
                    audio = audio,
                )
            }
        }
    }
}

@Composable
fun PublishRoute(
    sessionId: String,
    projectsDir: File,
    loadSession: LoadSession,
    updatePublish: UpdatePublishSettings,
    updateTrackMix: UpdateTrackMix,
    updateVocalChain: UpdateVocalChain,
    updateVocalSyncOffset: UpdateVocalSyncOffset,
    render: RenderEngine,
    audio: AudioEngine,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublishViewModel = viewModel(
        factory = PublishViewModel.factory(
            sessionId = sessionId,
            projectsDir = projectsDir,
            loadSession = loadSession,
            updatePublish = updatePublish,
            updateTrackMix = updateTrackMix,
            updateVocalChain = updateVocalChain,
            updateVocalSyncOffset = updateVocalSyncOffset,
            render = render,
            audio = audio,
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val audioState by viewModel.audio.state.collectAsState()
    val state = ui ?: return
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioOnExit()
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav"),
    ) { uri ->
        if (uri != null) {
            viewModel.copyExportToUri { source ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                    } != null
                }.getOrDefault(false)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to stage") }
        Text(text = "Mix & Save", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Balance your backing track and vocal parts, dial in reverb and echo, and export your finished master.",
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Track Volumes & Sync", style = MaterialTheme.typography.titleLarge)
                state.tracks.forEach { track ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${track.label} (${track.clipCount})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (track.gainDb >= 0f) "+${track.gainDb.roundToInt()} dB" else "${track.gainDb.roundToInt()} dB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SwaraColors.Muted,
                                )
                                FilterChip(
                                    selected = track.mute,
                                    onClick = { viewModel.toggleTrackMute(track.id) },
                                    label = { Text(if (track.mute) "Muted" else "Mute") },
                                )
                            }
                        }
                        Slider(
                            value = track.gainDb,
                            onValueChange = { viewModel.setTrackGain(track.id, it) },
                            valueRange = -18f..6f,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Vocal sync offset",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val ms = state.syncOffsetMs.roundToInt()
                            Text(
                                text = if (ms >= 0) "+$ms ms" else "$ms ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwaraColors.Muted,
                            )
                            if (ms != 0) {
                                TextButton(onClick = { viewModel.setSyncOffset(0f) }) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                    Slider(
                        value = state.syncOffsetMs,
                        onValueChange = { viewModel.setSyncOffset(it) },
                        valueRange = -250f..250f,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Vocal Effects Sliders", style = MaterialTheme.typography.titleLarge)
                EffectSliderRow(
                    label = "Reverb (Room & Hall)",
                    valueText = "${(state.chain.reverb * 100).roundToInt()}%",
                    value = state.chain.reverb,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(reverb = it) },
                )
                EffectSliderRow(
                    label = "Echo (Delay)",
                    valueText = "${(state.chain.delay * 100).roundToInt()}%",
                    value = state.chain.delay,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(delay = it) },
                )
                EffectSliderRow(
                    label = "Warmth & Compression",
                    valueText = "${(state.chain.compression * 100).roundToInt()}%",
                    value = state.chain.compression,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(compression = it) },
                )
                EffectSliderRow(
                    label = "Noise Clean (Gate)",
                    valueText = "${(state.chain.denoise * 100).roundToInt()}%",
                    value = state.chain.denoise,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(denoise = it) },
                )
                EffectSliderRow(
                    label = "Auto-Duck Backing Music",
                    valueText = "${state.chain.backingDuckDb.roundToInt()} dB",
                    value = state.chain.backingDuckDb,
                    valueRange = 0f..9f,
                    onValueChange = { viewModel.updateChainSliders(backingDuckDb = it) },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = viewModel::prepare,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.exportedRelativePath == null) {
                            "Mix backing & vocals"
                        } else {
                            "Remix with current sliders"
                        },
                    )
                }
                if (state.exportedRelativePath != null) {
                    val isPlaying = audioState.playingRelativePath == state.exportedRelativePath
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = viewModel::togglePlayExport,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isPlaying) "Stop preview" else "Play master")
                        }
                        Button(
                            onClick = {
                                saveFileLauncher.launch(state.exportedFileName ?: "swaralab-mix.wav")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save to phone")
                        }
                    }
                }
                state.status?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Teal,
                    )
                }
            }
        }

        Text(text = "Target format", style = MaterialTheme.typography.titleLarge)
        PublishTarget.entries.forEach { target ->
            ChoiceCard(
                title = target.label(),
                body = shareCopy(target),
                selected = state.target == target,
                onClick = { viewModel.select(target) },
            )
        }
    }
}

@Composable
private fun EffectSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

private fun PublishTarget.label(): String = when (this) {
    PublishTarget.MASTER -> "Full studio master"
    PublishTarget.REELS -> "Reels"
    PublishTarget.SHORTS -> "Shorts"
    PublishTarget.WHATSAPP -> "WhatsApp"
    PublishTarget.SQUARE -> "Square"
    PublishTarget.LANDSCAPE -> "Wide"
}

private fun shareCopy(target: PublishTarget): String = when (target) {
    PublishTarget.REELS -> "Vertical 9:16 framing, with lyrics kept clear of Reels buttons."
    PublishTarget.SHORTS -> "Vertical 9:16 framing, with lyrics kept clear of Shorts buttons."
    PublishTarget.WHATSAPP -> "Leveled for WhatsApp status and voice/audio sharing."
    PublishTarget.SQUARE -> "Square 1:1 feed post framing."
    PublishTarget.LANDSCAPE -> "Wide 16:9 framing."
    PublishTarget.MASTER -> "Full vocal + backing master WAV with your custom sliders applied."
}
