package com.swaralab.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.swaralab.app.media.PlaybackManager
import com.swaralab.app.media.PlaybackState
import com.swaralab.domain.port.Analytics
import com.swaralab.domain.port.AnalyticsEvent
import com.swaralab.domain.port.NoOpAnalytics
import com.swaralab.domain.model.ConversionStatus
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.port.TakeCatalog
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.EmptyState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.StatusMark
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.LocalTakeShare
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ListenUi(
    val title: String,
    val detail: String,
    val playFile: File?,
    val durationMs: Long,
    val score: Float?,
)

class ListenViewModel(
    private val sessionId: String,
    private val projectsDir: File,
    private val loadSession: LoadSession,
    private val takeCatalog: TakeCatalog?,
    val playback: PlaybackManager,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {
    private val uiState = MutableStateFlow<ListenUi?>(null)
    val ui: StateFlow<ListenUi?> = uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val document = loadSession(sessionId) ?: return
        val clip = document.tracks
            .filter { it.role != TrackRole.BACKING }
            .flatMap { it.clips }
            .let { clips -> clips.lastOrNull { it.preferred } ?: clips.lastOrNull() }
        val indexed = takeCatalog?.listTakes(sessionId).orEmpty()
            .firstOrNull { it.dryWavRelativePath == clip?.sourceRelativePath }
        val sessionRoot = File(projectsDir, sessionId)
        val wav = clip?.sourceRelativePath?.let { File(sessionRoot, it) }
        val aac = indexed?.aacRelativePath
            ?.takeIf { indexed.conversion == ConversionStatus.DONE }
            ?.let { File(sessionRoot, it) }
        val playFile = when {
            aac?.isFile == true -> aac
            wav?.isFile == true -> wav
            else -> null
        }
        uiState.value = ListenUi(
            title = indexed?.title?.takeIf { it.isNotBlank() } ?: document.title,
            detail = clip?.label ?: document.kind.name,
            playFile = playFile,
            durationMs = clip?.durationMs ?: indexed?.durationMs ?: 0L,
            score = indexed?.score,
        )
    }

    fun togglePlay() {
        val file = uiState.value?.playFile ?: return
        when (playback.playbackState.value) {
            PlaybackState.Playing -> playback.pause()
            PlaybackState.Paused -> playback.resume()
            PlaybackState.Ended -> playback.resume()
            else -> {
                playback.play(file)
                analytics.log(AnalyticsEvent.RECORDING_PLAYED, mapOf("has_file" to "true"))
            }
        }
    }

    fun seek(positionMs: Long) = playback.seekTo(positionMs)

    override fun onCleared() {
        playback.release()
        super.onCleared()
    }

    companion object {
        fun factory(
            sessionId: String,
            projectsDir: File,
            loadSession: LoadSession,
            takeCatalog: TakeCatalog?,
            playback: PlaybackManager,
            analytics: Analytics = NoOpAnalytics,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ListenViewModel(sessionId, projectsDir, loadSession, takeCatalog, playback, analytics) }
        }
    }
}

@Composable
fun ListenRoute(
    sessionId: String,
    projectsDir: File,
    loadSession: LoadSession,
    takeCatalog: TakeCatalog?,
    playback: PlaybackManager,
    analytics: Analytics = NoOpAnalytics,
    onBack: () -> Unit,
    onMix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ListenViewModel = viewModel(
        factory = ListenViewModel.factory(sessionId, projectsDir, loadSession, takeCatalog, playback, analytics),
    )
    val ui by viewModel.ui.collectAsState()
    val playState by viewModel.playback.playbackState.collectAsState()
    val position by viewModel.playback.position.collectAsState()
    val playerDuration by viewModel.playback.duration.collectAsState()
    val copy = LocalAppCopy.current
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        onDispose { viewModel.playback.release() }
    }
    val state = ui
    val playing = playState is PlaybackState.Playing
    val duration = playerDuration.takeIf { it > 0L } ?: (state?.durationMs ?: 1L).coerceAtLeast(1L)
    val slider = position.coerceIn(0L, duration)

    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere(BoothRoom.SONGS)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(copy.back) }
            if (state?.playFile != null) {
                TextButton(onClick = { LocalTakeShare.open(context, state.playFile, state.title) }) {
                    Text(copy.shareTake)
                }
            }
        }
        if (state?.playFile == null) {
            EmptyState(
                title = copy.noTakeYet,
                message = copy.emptySongsBody,
                mark = StatusMark.MIC,
                actionLabel = copy.back,
                onAction = onBack,
                fillMax = true,
            )
        } else {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(168.dp)
                .background(SwaraColors.TealWash, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", style = MaterialTheme.typography.displayLarge, color = SwaraColors.Teal)
        }
        Spacer(Modifier.height(20.dp))
            Text(state.title, style = MaterialTheme.typography.headlineSmall, color = SwaraColors.Ink)
            Text(state.detail, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Muted)
            Spacer(Modifier.height(20.dp))
            RecordingPlayerControls(
                isPlaying = playing,
                position = slider,
                duration = duration,
                playLabel = copy.listenTake,
                stopLabel = copy.stop,
                onPlayPause = viewModel::togglePlay,
                onSeek = viewModel::seek,
            )
            state.score?.let { score ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "${copy.inTune} · ${score.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SwaraColors.Teal,
                )
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onMix,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SwaraColors.Card,
                border = BorderStroke(1.dp, SwaraColors.Teal),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(copy.mixSave, color = SwaraColors.Teal, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun RecordingPlayerControls(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    playLabel: String,
    stopLabel: String,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Slider(
            value = position.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${formatListen(position)} / ${formatListen(duration)}",
            style = MaterialTheme.typography.bodySmall,
            color = SwaraColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        Surface(onClick = onPlayPause, shape = CircleShape, color = SwaraColors.Teal) {
            Text(
                text = if (isPlaying) stopLabel else playLabel,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                color = SwaraColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun formatListen(durationMs: Long): String {
    val total = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = total / 60L
    val seconds = total % 60L
    return "%d:%02d".format(minutes, seconds)
}
