package com.swaralab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
import com.swaralab.domain.model.TakeNote
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.port.TakeCatalog
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.ui.EmptyState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.StatusMark
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.LocalTakeShare
import com.swaralab.ui.BoothCanvas
import com.swaralab.ui.FavoriteButton
import com.swaralab.ui.GlassSurface
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
    val takeId: String? = null,
    val favorite: Boolean = false,
    val notes: List<TakeNote> = emptyList(),
    val sendingNote: Boolean = false,
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
            takeId = indexed?.id,
            favorite = indexed?.favorite == true,
            notes = indexed?.notes.orEmpty(),
        )
    }

    fun addNote(text: String) {
        val current = uiState.value ?: return
        val takeId = current.takeId ?: return
        val catalog = takeCatalog ?: return
        if (text.isBlank() || current.sendingNote) return
        uiState.value = current.copy(sendingNote = true)
        viewModelScope.launch {
            val note = runCatching { catalog.addNote(sessionId, takeId, text) }.getOrNull()
            val latest = uiState.value ?: current
            uiState.value = latest.copy(
                sendingNote = false,
                notes = if (note != null) latest.notes + note else latest.notes,
            )
        }
    }

    fun deleteNote(noteId: String) {
        val current = uiState.value ?: return
        val takeId = current.takeId ?: return
        val catalog = takeCatalog ?: return
        val previous = current.notes
        uiState.value = current.copy(notes = previous.filterNot { it.id == noteId })
        viewModelScope.launch {
            runCatching { catalog.deleteNote(sessionId, takeId, noteId) }
                .onFailure { uiState.value = (uiState.value ?: current).copy(notes = previous) }
        }
    }

    fun toggleFavorite() {
        val current = uiState.value ?: return
        val takeId = current.takeId ?: return
        val catalog = takeCatalog ?: return
        val next = !current.favorite
        uiState.value = current.copy(favorite = next)
        viewModelScope.launch {
            runCatching { catalog.toggleFavorite(sessionId, takeId) }
                .onFailure { uiState.value = current }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showNotes by remember { mutableStateOf(false) }
    val state = ui
    val playing = playState is PlaybackState.Playing
    val duration = playerDuration.takeIf { it > 0L } ?: (state?.durationMs ?: 1L).coerceAtLeast(1L)
    val slider = position.coerceIn(0L, duration)

    BoothCanvas(intensity = 0.55f, modifier = modifier) {
    Column(
        Modifier
            .fillMaxSize()
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
        var showHeart by remember { mutableStateOf(false) }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(168.dp)
                .background(SwaraColors.TealWash, CircleShape)
                .pointerInput(state.takeId, state.favorite) {
                    detectTapGestures(
                        onTap = { viewModel.togglePlay() },
                        onDoubleTap = {
                            if (state.takeId != null) {
                                viewModel.toggleFavorite()
                            }
                            showHeart = true
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", style = MaterialTheme.typography.displayLarge, color = SwaraColors.Teal)
            if (showHeart) {
                Text(
                    "♥",
                    color = Color(0xFFFF4081),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.graphicsLayer { alpha = 0.95f },
                )
                LaunchedEffect(Unit) {
                    delay(600)
                    showHeart = false
                }
            }
        }
        if (state.takeId != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FavoriteButton(favorite = state.favorite, onClick = viewModel::toggleFavorite)
                TextButton(onClick = { showNotes = true }) {
                    Text("${copy.takeNotes}${if (state.notes.isEmpty()) "" else " · ${state.notes.size}"}")
                }
            }
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
            GlassSurface(
                onClick = onMix,
                modifier = Modifier.fillMaxWidth(),
                selected = true,
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
    if (showNotes && state?.takeId != null) {
        TakeNotesSheet(
            notes = state.notes,
            sending = state.sendingNote,
            onSend = viewModel::addNote,
            onDelete = viewModel::deleteNote,
            onDismiss = { showNotes = false },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TakeNotesSheet(
    notes: List<TakeNote>,
    sending: Boolean,
    onSend: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = LocalAppCopy.current
    var text by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SwaraColors.Card.copy(alpha = 0.96f),
        modifier = Modifier.fillMaxHeight(0.85f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                copy.takeNotes,
                style = MaterialTheme.typography.titleLarge,
                color = SwaraColors.Ink,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (notes.isEmpty()) {
                EmptyState(
                    title = copy.noNotesYet,
                    message = copy.noNotesBody,
                    mark = StatusMark.SONG,
                    fillMax = false,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(SwaraColors.Teal.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("♪", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SwaraColors.Teal)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    formatRelativeTime(note.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwaraColors.Muted,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(note.text, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Ink)
                            }
                            TextButton(onClick = { onDelete(note.id) }) { Text(copy.delete) }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(copy.addNoteHint) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (text.isNotBlank() && !sending) {
                            onSend(text)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank() && !sending,
                ) {
                    Text(copy.sendNote, color = if (text.isNotBlank()) SwaraColors.Teal else SwaraColors.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> "${diff / 86_400_000}d"
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
