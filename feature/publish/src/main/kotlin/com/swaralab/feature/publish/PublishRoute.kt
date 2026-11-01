package com.swaralab.feature.publish

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.FrameFormat
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SafeFrames
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdatePublishSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PublishUi(
    val target: PublishTarget,
    val level: String,
    val frame: String,
    val safe: String,
    val status: String?,
)

class PublishViewModel(
    private val sessionId: String,
    private val loadSession: LoadSession,
    private val updatePublish: UpdatePublishSettings,
    private val render: RenderEngine,
) : ViewModel() {
    private val uiState = MutableStateFlow<PublishUi?>(null)
    val ui: StateFlow<PublishUi?> = uiState.asStateFlow()
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
            val document = loadSession(sessionId) ?: return@launch
            status = when (val result = render.export(document, ExportRequest(document.publish.target))) {
                is ExportResult.Written -> "Wrote ${result.relativePath}."
                is ExportResult.Waiting -> result.message
            }
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val publish = document.publish
            val insets = SafeFrames.forTarget(publish.target)
            uiState.value = PublishUi(
                target = publish.target,
                level = "${publish.loudnessLufs.toInt()} LUFS, true peak ${publish.truePeakDbtp.toInt()} dBTP.",
                frame = publish.frame.label(),
                safe = "On a ${SafeFrames.MASTER_WIDTH}×${SafeFrames.MASTER_HEIGHT} frame, keep lyrics ${insets.top} px from the top, ${insets.bottom} px from the bottom, and ${insets.right} px from the right.",
                status = status,
            )
        }
    }

    companion object {
        fun factory(
            sessionId: String,
            loadSession: LoadSession,
            updatePublish: UpdatePublishSettings,
            render: RenderEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PublishViewModel(sessionId, loadSession, updatePublish, render) }
        }
    }
}

@Composable
fun PublishRoute(
    sessionId: String,
    loadSession: LoadSession,
    updatePublish: UpdatePublishSettings,
    render: RenderEngine,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublishViewModel = viewModel(
        factory = PublishViewModel.factory(sessionId, loadSession, updatePublish, render),
    )
    val ui by viewModel.ui.collectAsState()
    val state = ui ?: return

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Session") }
        Text(text = "Share", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "One master, leveled for a phone feed.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            PublishTarget.entries.forEach { target ->
                FilterChip(
                    selected = state.target == target,
                    onClick = { viewModel.select(target) },
                    label = { Text(target.label()) },
                )
            }
        }
        Text(text = state.frame, style = MaterialTheme.typography.titleMedium)
        Text(text = state.level, style = MaterialTheme.typography.bodyLarge)
        Text(text = state.safe, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = viewModel::prepare) { Text("Prepare export") }
        state.status?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun PublishTarget.label(): String = when (this) {
    PublishTarget.MASTER -> "Master"
    PublishTarget.REELS -> "Reels"
    PublishTarget.SHORTS -> "Shorts"
    PublishTarget.WHATSAPP -> "WhatsApp"
    PublishTarget.SQUARE -> "Square"
    PublishTarget.LANDSCAPE -> "Wide"
}

private fun FrameFormat.label(): String = when (this) {
    FrameFormat.VERTICAL -> "Vertical 9:16"
    FrameFormat.SQUARE -> "Square 1:1"
    FrameFormat.LANDSCAPE -> "Wide 16:9"
}
