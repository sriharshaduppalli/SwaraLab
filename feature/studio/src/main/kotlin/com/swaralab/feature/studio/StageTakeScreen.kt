package com.swaralab.feature.studio

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swaralab.domain.audio.LiveMonitorEffect
import com.swaralab.domain.audio.PitchSample
import com.swaralab.domain.audio.LyricHighlight
import com.swaralab.domain.audio.StageTakeState
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.port.AudioEngineError
import com.swaralab.domain.port.ErrorRecovery
import com.swaralab.domain.port.recovery
import com.swaralab.ui.AnimatedMusicBackground
import com.swaralab.ui.ErrorState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.PulsingRecordButton
import com.swaralab.ui.SoftGlowOrb
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.boothHaptic
import com.swaralab.ui.pressScale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    livePitch: PitchSample? = null,
    keyShiftSemitones: Int = 0,
    playbackRate: Float = 1f,
    showBackingShift: Boolean = false,
    backingBusy: Boolean = false,
    backingProgress: Float = 0f,
    onKeyShift: (Int) -> Unit = {},
    onPlaybackRate: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    videoSlot: (@Composable () -> Unit)? = null,
) {
    when (state) {
        StageTakeState.Idle -> Unit
        is StageTakeState.Ready -> ReadyStage(
            state = state,
            onStart = onStart,
            onBack = onBack,
            showBackingShift = showBackingShift,
            keyShiftSemitones = keyShiftSemitones,
            playbackRate = playbackRate,
            onKeyShift = onKeyShift,
            onPlaybackRate = onPlaybackRate,
            backingBusy = backingBusy,
            backingProgress = backingProgress,
            modifier = modifier,
        )
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
            livePitch = livePitch,
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
    showBackingShift: Boolean,
    keyShiftSemitones: Int,
    playbackRate: Float,
    onKeyShift: (Int) -> Unit,
    onPlaybackRate: (Float) -> Unit,
    backingBusy: Boolean,
    backingProgress: Float,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedMusicBackground(intensity = 0.85f)
        SoftGlowOrb(modifier = Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-60).dp))
        SoftGlowOrb(
            color = SwaraColors.Gold,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-40).dp, y = 80.dp),
        )
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.draft.track?.title ?: "Ready to sing",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (showBackingShift) {
                Spacer(Modifier.height(20.dp))
                KeyTempoControls(
                    semitones = keyShiftSemitones,
                    tempo = playbackRate,
                    onSemitonesChange = onKeyShift,
                    onTempoChange = onPlaybackRate,
                )
                Text(
                    "Backing uses this key and speed when you sing.",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(36.dp))
            if (backingBusy) {
                CircularProgressIndicator(
                    progress = { backingProgress.coerceIn(0f, 1f) },
                    color = SwaraColors.Teal,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    "Processing track…",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
            }
            PulsingRecordButton(
                onClick = { if (!backingBusy) onStart() },
                label = "Sing",
                color = SwaraColors.Teal,
            )
        }
    }
}

@Composable
private fun KeyTempoControls(
    semitones: Int,
    tempo: Float,
    onSemitonesChange: (Int) -> Unit,
    onTempoChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Key: ${if (semitones > 0) "+" else ""}$semitones",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = semitones.toFloat(),
            onValueChange = { onSemitonesChange(it.roundToInt()) },
            valueRange = -6f..6f,
            steps = 11,
        )
        Text(
            "Tempo: ${"%.1f".format(tempo)}x",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = tempo,
            onValueChange = onTempoChange,
            valueRange = 0.5f..1.5f,
        )
    }
}

@Composable
private fun CountdownStage(beatsLeft: Int, modifier: Modifier) {
    val scale by animateFloatAsState(
        targetValue = 1f + (4 - beatsLeft).coerceIn(0, 3) * 0.04f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "count-scale",
    )
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedMusicBackground(intensity = 1f)
        Text(
            text = "$beatsLeft",
            modifier = Modifier.scale(scale),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, fontWeight = FontWeight.Bold),
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
    livePitch: PitchSample?,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onVoiceGain: (Float) -> Unit,
    onTrackGain: (Float) -> Unit,
    onEffectChange: (LiveMonitorEffect) -> Unit,
    videoSlot: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    var showEffects by remember { mutableStateOf(false) }
    val view = LocalView.current
    val total = durationMs.coerceAtLeast(1L)
    val currentIndex = highlight.lineIndex.coerceAtLeast(state.lyricIndex)
    Box(modifier.fillMaxSize()) {
        AnimatedMusicBackground(intensity = 0.8f + state.voiceLevel * 0.5f, voiceLevel = state.voiceLevel)
        SoftGlowOrb(modifier = Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-60).dp))
        SoftGlowOrb(
            color = Color(0xFFBB86FC),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-40).dp, y = 80.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = "${formatDuration(state.positionMs)} / ${formatDuration(total)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp).clip(CircleShape).alpha(0.75f),
                color = SwaraColors.Teal,
                trackColor = Color.White.copy(alpha = 0.16f),
            )
            if (videoSlot != null) {
                Box(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 8.dp)) { videoSlot() }
            }
            Spacer(Modifier.weight(1f))
            PitchVisualizer(
                sample = livePitch,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            LyricsCenterView(
                lyrics = lyrics,
                currentIndex = currentIndex,
                progress = highlight.progressInLine,
            )
            Spacer(Modifier.weight(1f))
            VoiceBars(
                level = state.voiceLevel,
                history = waveform,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().alpha(0.92f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "FX",
                    color = Color.White,
                    modifier = Modifier
                        .pressScale()
                        .clip(CircleShape)
                        .clickable { showEffects = true }
                        .padding(12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .pressScale(0.94f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            view.boothHaptic(HapticFeedbackConstants.LONG_PRESS)
                            onStop()
                        },
                ) {
                    RadialVoice(level = state.voiceLevel, history = waveform, modifier = Modifier.size(88.dp))
                    Text("■", color = Color(0xFFFF1744), style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    "‖",
                    color = Color.White,
                    modifier = Modifier
                        .pressScale()
                        .clip(CircleShape)
                        .clickable {
                            view.boothHaptic()
                            onPause()
                        }
                        .padding(12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.height(36.dp))
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
    val view = LocalView.current
    Box(modifier.fillMaxSize()) {
        AnimatedMusicBackground(intensity = 0.55f)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Paused · ${formatDuration(positionMs)} / ${formatDuration(durationMs.coerceAtLeast(1L))}",
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { showEffects = true }) { Text("Effects") }
        Button(
            onClick = {
                view.boothHaptic()
                onResume()
            },
        ) { Text("Resume") }
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
        containerColor = SwaraColors.Card.copy(alpha = 0.94f),
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
internal fun PitchVisualizer(
    sample: PitchSample?,
    modifier: Modifier = Modifier,
) {
    val voiced = sample != null &&
        sample.frequencyHz > 70f &&
        sample.confidence >= 0.6f &&
        sample.centsOffset != null
    Canvas(modifier.height(72.dp).fillMaxWidth()) {
        val centerY = size.height / 2
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 2f,
        )
        if (!voiced) return@Canvas
        val cents = sample!!.centsOffset!!.coerceIn(-50f, 50f)
        val offsetY = (cents / 50f) * (size.height / 2) * -1f
        val x = size.width * 0.7f
        drawCircle(
            color = when {
                abs(cents) < 15 -> Color(0xFF00E5FF)
                abs(cents) < 35 -> Color(0xFFFFC107)
                else -> Color(0xFFFF5252)
            },
            radius = 12f,
            center = Offset(x, centerY + offsetY),
        )
    }
}

@Composable
private fun ScoreBar(label: String, value: Float) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White)
            Text("${value.toInt()}", color = Color.White.copy(alpha = 0.8f))
        }
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = when {
                value >= 80 -> Color(0xFF00E5FF)
                value >= 60 -> Color(0xFFFFC107)
                else -> Color(0xFFFF5252)
            },
            trackColor = Color.White.copy(alpha = 0.16f),
        )
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
    val view = LocalView.current
    val detailed = state.detailed
    val check by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "keep-check",
    )
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).scale(check), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(72.dp)) {
                drawCircle(SwaraColors.Teal.copy(alpha = 0.18f))
                drawCircle(
                    color = SwaraColors.Teal,
                    radius = size.minDimension * 0.42f,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            Text("✓", color = SwaraColors.Teal, style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.analyzing && detailed == null) {
                "—"
            } else {
                "${(detailed?.overall ?: state.score ?: 0f).toInt()}"
            },
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Overall score", color = SwaraColors.Muted)
        Text(
            text = formatDuration(state.durationMs),
            color = SwaraColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.analyzing && detailed == null) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = SwaraColors.Teal, modifier = Modifier.size(32.dp))
            Text(
                "Analyzing…",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (detailed != null) {
            Spacer(Modifier.height(20.dp))
            ScoreBar("Pitch accuracy", detailed.pitchAccuracy)
            ScoreBar("Timing", detailed.timing)
            ScoreBar("Stability", detailed.stability)
            if (detailed.sections.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sections",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                )
                detailed.sections.forEach { section ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${section.name}  ${formatDuration(section.startMs)}–${formatDuration(section.endMs)}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "P ${section.pitch.toInt()} · T ${section.timing.toInt()}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    view.boothHaptic()
                    onSave()
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(copy.keepAndMix)
            }
            Button(
                onClick = {
                    view.boothHaptic()
                    onShare()
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(copy.shareTake)
            }
        }
        TextButton(onClick = onRetry) { Text("Try again") }
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
fun AnimatedLyricLine(
    text: String,
    isActive: Boolean,
    progress: Float = 0f,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "lyricScale",
    )
    val color by animateColorAsState(
        targetValue = if (isActive) SwaraColors.Teal else Color.White.copy(alpha = 0.65f),
        label = "lyricColor",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            fontSize = if (isActive) 32.sp else 22.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        if (isActive) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0.08f, 1f) },
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                color = SwaraColors.Teal,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
fun LyricsCenterView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    progress: Float,
) {
    if (lyrics.isEmpty()) {
        Text(
            "Sing. Add words after this take if you want.",
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        return
    }
    val index = currentIndex.coerceAtLeast(0)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        lyrics.getOrNull(index - 1)?.let {
            Text(
                it.text,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
        lyrics.getOrNull(index)?.let {
            AnimatedLyricLine(text = it.text, isActive = true, progress = progress)
        }
        lyrics.getOrNull(index + 1)?.let {
            Text(
                it.text,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun LyricsList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem(currentIndex.coerceAtMost(lyrics.lastIndex.coerceAtLeast(0))) }
        }
    }
    if (lyrics.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sing. Add words after this take if you want.", color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyrics) { index, line ->
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                AnimatedLyricLine(
                    text = line.text,
                    isActive = index == currentIndex,
                    progress = progress,
                )
            }
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

@Composable
private fun RadialVoice(
    level: Float,
    history: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val ticks = 28
        val cx = size.width / 2f
        val cy = size.height / 2f
        val inner = size.minDimension * 0.32f
        val outer = size.minDimension * 0.48f
        repeat(ticks) { i ->
            val sample = history.getOrNull(history.size - ticks + i) ?: level
            val len = inner + (outer - inner) * sample.coerceIn(0.12f, 1f)
            val angle = (i / ticks.toFloat()) * Math.PI.toFloat() * 2f - (Math.PI.toFloat() / 2f)
            drawLine(
                color = SwaraColors.Teal.copy(alpha = 0.85f),
                start = Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
                end = Offset(cx + cos(angle) * len, cy + sin(angle) * len),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = SwaraColors.Teal.copy(alpha = 0.22f),
            radius = inner * (0.92f + level * 0.18f),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
