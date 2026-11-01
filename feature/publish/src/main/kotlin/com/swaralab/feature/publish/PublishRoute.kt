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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PublishUi(
    val title: String,
    val target: PublishTarget,
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

    fun prepare() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (current.busy) return@launch
            uiState.value = current.copy(busy = true, status = "Mixing vocal tracks...")
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(document, ExportRequest(document.publish.target))) {
                is ExportResult.Written -> {
                    exportedRelativePath = result.relativePath
                    status = "Master mix ready (${result.durationMs / 1000}s). Tap Play master or Save to device."
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
            uiState.value = PublishUi(
                title = document.title,
                target = publish.target,
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
            render: RenderEngine,
            audio: AudioEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PublishViewModel(
                    sessionId = sessionId,
                    projectsDir = projectsDir,
                    loadSession = loadSession,
                    updatePublish = updatePublish,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to stage") }
        Text(text = "Mix & Save", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Choose a destination format, mix your recorded vocal takes with your chosen voice style, and save the finished audio.",
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = viewModel::prepare,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.exportedRelativePath == null) "Mix vocal master" else "Remix vocal master")
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
    PublishTarget.MASTER -> "Full vocal master WAV with your selected voice style applied."
}
