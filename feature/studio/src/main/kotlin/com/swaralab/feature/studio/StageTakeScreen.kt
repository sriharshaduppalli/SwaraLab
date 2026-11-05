package com.swaralab.feature.studio

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swaralab.domain.audio.LiveMonitorEffect
import com.swaralab.domain.audio.LyricHighlight
import com.swaralab.domain.audio.StageTakeState
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.port.AudioEngineError
import com.swaralab.domain.port.ErrorRecovery
import com.swaralab.domain.port.recovery
import com.swaralab.ui.ErrorState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageTakeScreen(
    state: StageTakeState,
    lyrics: List<LyricLine>,
    highlight: LyricHighlight,
    voiceGain: Float,
    trackGain: Float,
    currentEffect: LiveMonitorEffect = LiveMonitorEffect.None,
    durationMs: Long,
    error: AudioEngineError?,
    waveform: List<Float>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onVoiceGain: (Float) -> Unit,
    onTrackGain: (Float) -> Unit,
    onEffectChange: (LiveMonitorEffect) -> Unit = {},
    onSave: () -> Unit,
    onShare: () -> Unit = {},
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPickTrack: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    videoSlot: (@Composable () -> Unit)? = null,
) {
    when (state) {
        StageTakeState.Idle -> Unit
        is StageTakeState.Ready -> ReadyStage(state, onStart, onBack, modifier)
        is StageTakeState.Countdown -> CountdownStage(state.beatsLeft, modifier)
        is StageTakeState.Recording -> RecordingStage(
            state = state,
            lyrics = lyrics,
            highlight = highlight,
            voiceGain = voiceGain,
            trackGain = trackGain,
            currentEffect = currentEffect,
            durationMs = durationMs,
            waveform = waveform,
            onPause = onPause,
            onStop = onStop,
            onVoiceGain = onVoiceGain,
            onTrackGain = onTrackGain,
            onEffectChange = onEffectChange,
            videoSlot = videoSlot,
            modifier = modifier,
        )
        is StageTakeState.Paused -> PausedStage(
            positionMs = state.positionMs,
            durationMs = durationMs,
            voiceGain = voiceGain,
            trackGain = trackGain,
            currentEffect = currentEffect,
            onResume = onResume,
            onStop = onStop,
            onVoiceGain = onVoiceGain,
            onTrackGain = onTrackGain,
            onEffectChange = onEffectChange,
            modifier = modifier,
        )
        is StageTakeState.Review -> ReviewStage(state, onSave, onShare, onRetry, modifier)
        is StageTakeState.Error -> ErrorStage(state.message, error, onRetry, onBack, onPickTrack, onRequestPermission, modifier)
    }
}

@Composable
private fun ReadyStage(
    state: StageTakeState.Ready,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.draft.track?.title ?: "Ready to sing", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart) { Text("Sing") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun CountdownStage(beatsLeft: Int, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$beatsLeft",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, fontWeight = FontWeight.Bold),
            color = SwaraColors.Teal,
        )
    }
}

@Composable
private fun RecordingStage(
    state: StageTakeState.Recording,
    lyrics: List<LyricLine>,
    highlight: LyricHighlight,
    voiceGain: Float,
    trackGain: Float,
    currentEffect: LiveMonitorEffect,
    durationMs: Long,
    waveform: List<Float>,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onVoiceGain: (Float) -> Unit,
    onTrackGain: (Float) -> Unit,
    onEffectChange: (LiveMonitorEffect) -> Unit,
    videoSlot: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    var showEffects by remember { mutableStateOf(false) }
    val total = durationMs.coerceAtLeast(1L)
    Column(modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        LinearProgressIndicator(
            progress = { (state.positionMs.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = SwaraColors.Teal,
            trackColor = SwaraColors.Line,
        )
        Text(
            text = "${formatDuration(state.positionMs)} / ${formatDuration(total)}",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            color = SwaraColors.Muted,
        )
        if (videoSlot != null) {
            Box(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp)) { videoSlot() }
        }
        LyricsList(
            lyrics = lyrics,
            currentIndex = highlight.lineIndex.coerceAtLeast(state.lyricIndex),
            modifier = Modifier.weight(1f),
        )
        VoiceBars(
            level = state.voiceLevel,
            history = waveform,
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showEffects = true }) { Text("Effects") }
            OutlinedButton(onClick = onPause) { Text("Pause") }
            Button(onClick = onStop) { Text("Stop") }
        }
        EffectsSheet(
            visible = showEffects,
            currentEffect = currentEffect,
            voiceGain = voiceGain,
            trackGain = trackGain,
            onEffectChange = onEffectChange,
            onVoiceGain = onVoiceGain,
            onTrackGain = onTrackGain,
            onDismiss = { showEffects = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PausedStage(
    positionMs: Long,
    durationMs: Long,
    voiceGain: Float,
    trackGain: Float,
    currentEffect: LiveMonitorEffect,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onVoiceGain: (Float) -> Unit,
    onTrackGain: (Float) -> Unit,
    onEffectChange: (LiveMonitorEffect) -> Unit,
    modifier: Modifier,
) {
    var showEffects by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Paused · ${formatDuration(positionMs)} / ${formatDuration(durationMs.coerceAtLeast(1L))}")
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { showEffects = true }) { Text("Effects") }
        Button(onClick = onResume) { Text("Resume") }
        TextButton(onClick = onStop) { Text("Stop") }
        EffectsSheet(
            visible = showEffects,
            currentEffect = currentEffect,
            voiceGain = voiceGain,
            trackGain = trackGain,
            onEffectChange = onEffectChange,
            onVoiceGain = onVoiceGain,
            onTrackGain = onTrackGain,
            onDismiss = { showEffects = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsSheet(
    visible: Boolean,
    currentEffect: LiveMonitorEffect,
    voiceGain: Float,
    trackGain: Float,
    onEffectChange: (LiveMonitorEffect) -> Unit,
    onVoiceGain: (Float) -> Unit,
    onTrackGain: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
    ) {
        EffectsPanel(
            currentEffect = currentEffect,
            onEffectChange = onEffectChange,
            voiceGain = voiceGain,
            onVoiceGainChange = onVoiceGain,
            trackGain = trackGain,
            onTrackGainChange = onTrackGain,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReviewStage(
    state: StageTakeState.Review,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val copy = LocalAppCopy.current
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Keep this take?", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = buildString {
                append(formatDuration(state.durationMs))
                state.score?.let { append(" · score ${it.toInt()}") }
            },
            color = SwaraColors.Muted,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text(copy.keepAndMix)
            }
            Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                Text(copy.shareTake)
            }
        }
        TextButton(onClick = onRetry) { Text("Sing again") }
    }
}

@Composable
private fun ErrorStage(
    message: String,
    error: AudioEngineError?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPickTrack: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier,
) {
    val recovery = error?.recovery() ?: ErrorRecovery.RETRY
    val retry = when (recovery) {
        ErrorRecovery.PERMISSION -> "Grant permission" to onRequestPermission
        ErrorRecovery.PICK_TRACK -> "Choose another track" to onPickTrack
        ErrorRecovery.FREE_SPACE -> "Retry after freeing space" to onRetry
        ErrorRecovery.RESTART -> "Restart" to onRetry
        ErrorRecovery.RETRY -> "Retry" to onRetry
    }
    ErrorState(
        title = "Recording error",
        message = message,
        retryLabel = retry.first,
        onRetry = retry.second,
        secondaryLabel = "Back",
        onSecondary = onBack,
        fillMax = true,
        modifier = modifier,
    )
}

@Composable
fun LyricsList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem(currentIndex.coerceAtMost(lyrics.lastIndex.coerceAtLeast(0))) }
        }
    }
    if (lyrics.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sing. Add words after this take if you want.", color = SwaraColors.Muted, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(modifier = modifier, state = listState, verticalArrangement = Arrangement.Center) {
        itemsIndexed(lyrics) { index, line ->
            val current = index == currentIndex
            Text(
                text = line.text,
                fontSize = if (current) 28.sp else 20.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) SwaraColors.Teal else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun VoiceBars(
    level: Float,
    history: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val bars = 32
        val width = size.width / bars
        repeat(bars) { i ->
            val sample = history.getOrNull(history.size - bars + i) ?: level
            val height = size.height * sample.coerceIn(0.04f, 1f)
            drawRect(
                color = SwaraColors.Teal,
                topLeft = Offset(i * width, size.height - height),
                size = Size(width * 0.62f, height),
            )
        }
    }
}
