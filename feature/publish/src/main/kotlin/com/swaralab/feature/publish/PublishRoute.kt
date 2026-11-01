package com.swaralab.feature.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
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
            uiState.value = PublishUi(
                target = publish.target,
                level = "Loudness is set for a phone feed.",
                frame = publish.frame.label(),
                safe = shareCopy(publish.target),
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
        TextButton(onClick = onBack) { Text("Back") }
        Text(text = "Share", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Pick one. The finished file comes in a later version.",
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )
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
    PublishTarget.MASTER -> "Full video"
    PublishTarget.REELS -> "Reels"
    PublishTarget.SHORTS -> "Shorts"
    PublishTarget.WHATSAPP -> "WhatsApp"
    PublishTarget.SQUARE -> "Square"
    PublishTarget.LANDSCAPE -> "Wide"
}

private fun shareCopy(target: PublishTarget): String = when (target) {
    PublishTarget.REELS -> "A vertical video, with the words kept clear of the Reels buttons."
    PublishTarget.SHORTS -> "A vertical video, with the words kept clear of the Shorts buttons."
    PublishTarget.WHATSAPP -> "A vertical video that fits a WhatsApp status."
    PublishTarget.SQUARE -> "A square picture for a post."
    PublishTarget.LANDSCAPE -> "A wide picture."
    PublishTarget.MASTER -> "The full vertical video, before it is cropped for an app."
}

private fun FrameFormat.label(): String = when (this) {
    FrameFormat.VERTICAL -> "Vertical"
    FrameFormat.SQUARE -> "Square"
    FrameFormat.LANDSCAPE -> "Wide"
}
