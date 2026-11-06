package com.swaralab.feature.studio

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.audio.BackingPlayback
import com.swaralab.domain.audio.LiveLyricFollower
import com.swaralab.domain.audio.LyricLanguage
import com.swaralab.domain.audio.MusicalKeys
import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.audio.VocalMetrics
import com.swaralab.domain.audio.VocalSignalAnalyzer
import com.swaralab.domain.audio.LyricHighlight
import com.swaralab.domain.audio.WavPeaks
import com.swaralab.domain.model.ScaleMode
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.model.MusicalSettings
import com.swaralab.domain.model.PracticeStats
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.InstrumentalResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.TakeControl
import com.swaralab.domain.usecase.AddVoicePart
import com.swaralab.domain.usecase.CommitRecordedTake
import com.swaralab.domain.usecase.DeleteRecordedClip
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.PreferRecordedClip
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.TrimRecordedClip
import com.swaralab.domain.usecase.UpdateGear
import com.swaralab.domain.usecase.UpdateLyrics
import com.swaralab.domain.usecase.UpdateMusicalSettings
import com.swaralab.domain.usecase.UpdatePracticeStats
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateVocalPreset
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.AppCopy
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dedicated recording control bar. Only observes `isRecording` and 1-second timer ticks
 * (`elapsedMs / 1000L`), so the Sing/Stop button never recomposes on fast audio meter frames.
 */
@Composable
internal fun RecordingControlBar(
    audioStateFlow: StateFlow<AudioEngineState>,
    stageNote: String?,
    copy: AppCopy,
    lines: List<LyricLine>,
    activeLine: Int,
    onSingClick: (Boolean) -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
) {
    val isRecording by remember(audioStateFlow) {
        audioStateFlow.map {
            it.status == EngineStatus.RECORDING ||
                it.status == EngineStatus.COUNTING ||
                it.status == EngineStatus.PAUSED
        }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    val isCounting by remember(audioStateFlow) {
        audioStateFlow.map { it.status == EngineStatus.COUNTING }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val isPaused by remember(audioStateFlow) {
        audioStateFlow.map { it.status == EngineStatus.PAUSED }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val countInBeat by remember(audioStateFlow) {
        audioStateFlow.map { it.countInBeat }.distinctUntilChanged()
    }.collectAsState(initial = 0)
    val elapsedSeconds by remember(audioStateFlow) {
        audioStateFlow.map { it.elapsedMs / 1000L }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
    val micHealth by remember(audioStateFlow) {
        audioStateFlow.map { engine ->
            when {
                engine.status != EngineStatus.RECORDING -> 0
                engine.peak > 0.89f -> 2
                engine.maxPeak < 0.012f && engine.elapsedMs > 2000L -> 1
                else -> 0
            }
        }.distinctUntilChanged()
    }.collectAsState(initial = 0)

    val recPulse = rememberInfiniteTransition(label = "recPulse")
    val pulseScale by recPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recPulseScale",
    )

    Column(Modifier.fillMaxWidth()) {
    if (micHealth > 0) {
        Text(
            text = if (micHealth == 2) copy.micHot else copy.micSilent,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Clay,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isRecording) SwaraColors.TealWash else SwaraColors.Card,
        border = BorderStroke(1.dp, if (isRecording) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                onClick = { onSingClick(isRecording) },
                modifier = Modifier
                    .size(72.dp)
                    .scale(if (isRecording && !isCounting) pulseScale else 1f)
                    .clip(CircleShape),
                color = if (isRecording) SwaraColors.Clay else SwaraColors.Teal,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            isCounting -> "$countInBeat"
                            isRecording -> copy.stop
                            else -> copy.sing
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = when {
                            isCounting -> if (countInBeat <= 1) copy.singOnOne else "$countInBeat"
                            isPaused -> "Paused · ${formatDuration(elapsedSeconds * 1000L)}"
                            isRecording -> "${copy.recordingLive} · ${formatDuration(elapsedSeconds * 1000L)}"
                            else -> copy.tapToSing
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (isRecording) SwaraColors.Clay else SwaraColors.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stageNote
                        ?: if (isRecording) copy.recordingLive else copy.tapToSing,
                    minLines = 2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
            if (isRecording && !isCounting) {
                TextButton(onClick = { if (isPaused) onResume() else onPause() }) {
                    Text(if (isPaused) "Resume" else "Pause")
                }
            }
        }
    }
    }
}

/**
 * Reads `audioState.value` inside the `Canvas` draw scope so high-frequency waveform/pitch/EQ
 * updates only trigger GPU draw invalidation without recomposing or re-laying out text.
 * Text readouts update at a calm 400 ms cadence.
 */
@Composable
internal fun LiveVocalMonitorCard(
    audioStateFlow: StateFlow<AudioEngineState>,
) {
    val audioState = audioStateFlow.collectAsState()
    var calmMetrics by remember { mutableStateOf(VocalMetrics()) }
    var calmModeLabel by remember { mutableStateOf("READY") }
    var calmActive by remember { mutableStateOf(false) }

    LaunchedEffect(audioStateFlow) {
        while (true) {
            val current = audioStateFlow.value
            val rec = current.status == EngineStatus.RECORDING
            val play = current.playingRelativePath != null
            calmActive = rec || play
            calmModeLabel = when {
                rec -> "LIVE MIC"
                play -> "PLAYBACK"
                else -> "READY"
            }
            calmMetrics = current.metrics
            delay(380L)
        }
    }

    val hasPitch = calmMetrics.pitchHz > 0f
    val tuningLabel = when {
        !hasPitch -> "Listen, then sing"
        abs(calmMetrics.centsOffset) <= 12 -> "In tune"
        abs(calmMetrics.centsOffset) <= 35 && calmMetrics.centsOffset < 0 -> "A little low"
        abs(calmMetrics.centsOffset) <= 35 -> "A little high"
        calmMetrics.centsOffset < 0 -> "Low · lift the note"
        else -> "High · ease the note"
    }
    val voiceLevel = voiceLevelFromDb(calmMetrics.rmsDb)
    val voiceTone = when {
        voiceLevel < 0.22f -> SwaraColors.Muted
        voiceLevel > 0.88f -> SwaraColors.Clay
        else -> SwaraColors.Teal
    }
    val pitchTone = when {
        !hasPitch -> SwaraColors.Gold
        abs(calmMetrics.centsOffset) <= 12 -> SwaraColors.Teal
        abs(calmMetrics.centsOffset) <= 35 -> SwaraColors.Gold
        else -> SwaraColors.Clay
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (calmActive) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Live pitch",
                style = MaterialTheme.typography.titleLarge,
                color = SwaraColors.Ink,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (hasPitch) pitchTone.copy(alpha = 0.16f) else SwaraColors.Paper,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = if (hasPitch) {
                            "${calmMetrics.swaraName}  ·  ${calmMetrics.noteName}"
                        } else {
                            calmModeLabel
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (hasPitch) pitchTone else SwaraColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (hasPitch) {
                            "$tuningLabel  ·  ${calmMetrics.pitchHz.roundToInt()} Hz  ·  ${calmMetrics.rmsDb.roundToInt()} dB"
                        } else {
                            "Sing a held note. Sa and the Western name will sit here."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Ink,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = SwaraColors.Paper,
                    border = BorderStroke(1.dp, SwaraColors.Line),
                ) {
                    PitchAndWaveformCanvas(
                        audioState = audioState,
                        pitchColor = pitchTone,
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp, top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("High", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text("Mid", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text("Low", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqBandRow(
                    label = "Voice",
                    hint = when {
                        voiceLevel < 0.22f -> "Closer"
                        voiceLevel > 0.88f -> "Hot"
                        else -> "Good"
                    },
                    levelProvider = { voiceLevelFromDb(audioState.value.metrics.rmsDb) },
                    color = voiceTone,
                )
                EqBandRow(
                    label = "Low",
                    hint = "Warmth",
                    levelProvider = { audioState.value.metrics.bassLevel },
                    color = SwaraColors.Teal,
                )
                EqBandRow(
                    label = "Mid",
                    hint = "Words",
                    levelProvider = { audioState.value.metrics.midLevel },
                    color = SwaraColors.Gold,
                )
                EqBandRow(
                    label = "Air",
                    hint = "Shine",
                    levelProvider = { audioState.value.metrics.trebleLevel },
                    color = SwaraColors.Rose,
                )
            }
        }
    }
}

@Composable
internal fun PitchAndWaveformCanvas(
    audioState: State<AudioEngineState>,
    pitchColor: Color,
) {
    val path = remember { Path() }
    Canvas(modifier = Modifier.fillMaxSize().padding(start = 36.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        val metrics = audioState.value.metrics
        val width = size.width
        val height = size.height
        val waveFloor = height * 0.78f
        val pitchHeight = height * 0.74f
        listOf(0.08f, 0.5f, 0.92f).forEach { frac ->
            val y = pitchHeight * frac
            drawLine(
                color = SwaraColors.Line.copy(alpha = 0.7f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawRect(
            color = SwaraColors.Teal.copy(alpha = 0.10f),
            topLeft = Offset(0f, pitchHeight * 0.38f),
            size = Size(width, pitchHeight * 0.24f),
        )

        val wave = metrics.waveformHistory
        val pitches = metrics.pitchHistory
        val slots = maxOf(wave.size, pitches.size, 2)
        val stepX = width / (slots - 1).toFloat()

        wave.forEachIndexed { idx, amp ->
            val x = idx * stepX
            val bar = (amp.coerceIn(0.04f, 1f) * (height - waveFloor) * 0.9f)
            drawLine(
                color = SwaraColors.Teal.copy(alpha = 0.45f),
                start = Offset(x, height),
                end = Offset(x, height - bar),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        path.reset()
        var penDown = false
        var lastPoint: Offset? = null
        pitches.forEachIndexed { idx, normPitch ->
            val x = idx * stepX
            if (normPitch > 0.01f) {
                val y = pitchHeight * (1f - normPitch.coerceIn(0.05f, 0.95f))
                val point = Offset(x, y)
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                lastPoint = point
            } else {
                penDown = false
            }
        }
        drawPath(
            path = path,
            color = pitchColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        lastPoint?.let { point ->
            drawCircle(color = pitchColor.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = point)
            drawCircle(color = pitchColor, radius = 4.5.dp.toPx(), center = point)
        }
    }
}

@Composable
internal fun EqBandRow(
    label: String,
    hint: String,
    levelProvider: () -> Float,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Ink,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = SwaraColors.Line.copy(alpha = 0.55f))
                drawRect(
                    color = SwaraColors.Teal.copy(alpha = 0.16f),
                    topLeft = Offset(size.width * 0.28f, 0f),
                    size = Size(size.width * 0.52f, size.height),
                )
                val fillWidth = size.width * levelProvider().coerceIn(0.02f, 1f)
                drawRect(
                    color = color,
                    size = Size(fillWidth, size.height),
                )
            }
        }
        Text(
            text = hint,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

internal fun voiceLevelFromDb(rmsDb: Float): Float {
    return ((rmsDb + 48f) / 42f).coerceIn(0f, 1f)
}

private data class ScaleTarget(
    val swara: String,
    val note: String,
    val hz: Float,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WarmupScaleCoachCard(
    keyName: String,
    scaleMode: ScaleMode,
    practice: PracticeStats,
    audioStateFlow: StateFlow<AudioEngineState>,
    backing: BackingTrackUi?,
    playingRelativePath: String?,
    onEnableDrone: () -> Unit,
    onToggleDrone: (String) -> Unit,
) {
    var selectedIdx by remember { mutableStateOf(0) }
    var calmPitchHz by remember { mutableStateOf(0f) }
    val scale = remember(keyName, scaleMode) { MusicalKeys.scaleForKey(keyName, scaleMode) }
    val target = scale.getOrElse(selectedIdx.coerceIn(0, scale.lastIndex)) { scale.first() }

    LaunchedEffect(audioStateFlow) {
        while (true) {
            calmPitchHz = audioStateFlow.value.metrics.pitchHz
            delay(380L)
        }
    }

    val centsDiff = if (calmPitchHz > 60f) {
        (1200.0 * (ln(calmPitchHz.toDouble() / target.hz.toDouble()) / ln(2.0))).roundToInt()
    } else {
        null
    }
    val isHit = centsDiff != null && abs(centsDiff) <= 45

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (isHit) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Swara & Scale Coach", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Target: ${target.swara} (${target.westernNote} · ${target.hz.roundToInt()} Hz)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Gold,
                    )
                }
                if (backing == null) {
                    OutlinedButton(onClick = onEnableDrone) {
                        Text("Load Tanpura")
                    }
                } else {
                    val isPlayingDrone = playingRelativePath == backing.relativePath
                    OutlinedButton(onClick = { onToggleDrone(backing.relativePath) }) {
                        Text(if (isPlayingDrone) "Stop Tanpura" else "Play Tanpura")
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                scale.forEachIndexed { idx, item ->
                    FilterChip(
                        selected = selectedIdx == idx,
                        onClick = { selectedIdx = idx },
                        label = { Text("${item.swara} (${item.westernNote})") },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isHit) SwaraColors.TealWash else SwaraColors.Paper,
            ) {
                val feedback = when {
                    centsDiff == null -> "Tap Sing below and hold ${target.swara} (${target.westernNote})"
                    abs(centsDiff) <= 45 -> "Locked on ${target.swara} (${target.westernNote})! Hold steady."
                    centsDiff < 0 -> "Sing slightly higher ↑ to reach ${target.swara} (${target.westernNote})"
                    else -> "Sing slightly lower ↓ to reach ${target.swara} (${target.westernNote})"
                }
                Text(
                    text = feedback,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isHit) SwaraColors.Teal else SwaraColors.Ink,
                )
            }
            val low = if (practice.lowestHz > 0f) {
                VocalSignalAnalyzer.pitchToNoteAndSwara(practice.lowestHz).first
            } else {
                "—"
            }
            val high = if (practice.highestHz > 0f) {
                VocalSignalAnalyzer.pitchToNoteAndSwara(practice.highestHz).first
            } else {
                "—"
            }
            Text(
                text = "Score ${practice.scorePercent}% · ${practice.hits}/${practice.attempts} in tune · Range $low–$high",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
    }
}

internal val videoFrameTargets = listOf(
    PublishTarget.REELS,
    PublishTarget.SHORTS,
    PublishTarget.WHATSAPP,
)

@Composable
internal fun StageLightsPane(
    kind: SessionKind,
    title: String,
    lines: List<LyricLine>,
    activeLine: Int,
    publishTarget: PublishTarget,
    cameraController: SingerCameraController,
    audioStateFlow: StateFlow<AudioEngineState>,
    onSelectLine: (Int) -> Unit,
    onSelectTarget: (PublishTarget) -> Unit,
    highlight: LyricHighlight = LyricHighlight(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StagePitchChip(audioStateFlow)
        if (kind == SessionKind.VIDEO) {
            VerticalVideoStageCard(
                title = title,
                target = publishTarget,
                activeLyric = lines.getOrNull(activeLine)?.text.orEmpty(),
                nextLyric = lines.getOrNull(activeLine + 1)?.text.orEmpty(),
                audioStateFlow = audioStateFlow,
                cameraController = cameraController,
                onSelectTarget = onSelectTarget,
                expanded = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            LyricStagePane(
                lines = lines,
                activeLine = activeLine,
                onSelectLine = onSelectLine,
                highlight = highlight,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun LyricStagePane(
    lines: List<LyricLine>,
    activeLine: Int,
    onSelectLine: (Int) -> Unit,
    highlight: LyricHighlight = LyricHighlight(),
    modifier: Modifier = Modifier,
) {
    val previous = lines.getOrNull(activeLine - 1)
    val current = lines.getOrNull(activeLine)
    val next = lines.getOrNull(activeLine + 1)
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xCC0A1214),
        border = BorderStroke(1.dp, SwaraColors.Teal.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (previous != null) {
                Surface(
                    onClick = { onSelectLine(activeLine - 1) },
                    color = Color.Transparent,
                ) {
                    Text(
                        text = previous.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.42f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                onClick = { if (current != null) onSelectLine(activeLine) },
                color = Color.Transparent,
            ) {
                HighlightedLyricLine(
                    line = current,
                    wordIndex = highlight.wordIndex,
                    progress = highlight.progressInLine,
                )
            }
            if (next != null) {
                Surface(
                    onClick = { onSelectLine(activeLine + 1) },
                    color = Color.Transparent,
                ) {
                    Text(
                        text = next.text,
                        style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp),
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightedLyricLine(
    line: LyricLine?,
    wordIndex: Int,
    progress: Float,
) {
    if (line == null) {
        Text(
            text = "Sing. The words can wait until after this take.",
            style = MaterialTheme.typography.displayMedium,
            color = SwaraColors.Teal,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val words = line.words
    if (words.isEmpty()) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.displayMedium,
            color = SwaraColors.Teal,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = buildString {
                words.forEachIndexed { index, word ->
                    if (index > 0) append(' ')
                    append(word.text)
                }
            },
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = if (wordIndex >= 0) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = SwaraColors.Teal,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (wordIndex >= 0) {
            Text(
                text = words.getOrNull(wordIndex)?.text.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = SwaraColors.Gold,
                textAlign = TextAlign.Center,
            )
        }
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        color = SwaraColors.Teal,
        trackColor = SwaraColors.Teal.copy(alpha = 0.2f),
    )
}

@Composable
internal fun StagePitchChip(audioStateFlow: StateFlow<AudioEngineState>) {
    var note by remember { mutableStateOf("LISTEN") }
    var tuning by remember { mutableStateOf("Hold the note you want") }
    LaunchedEffect(audioStateFlow) {
        while (true) {
            val metrics = audioStateFlow.value.metrics
            if (metrics.pitchHz > 0f) {
                note = "${metrics.swaraName}  ·  ${metrics.noteName}  ·  ${metrics.pitchHz.roundToInt()} Hz"
                tuning = when {
                    kotlin.math.abs(metrics.centsOffset) <= 12 -> "In tune"
                    kotlin.math.abs(metrics.centsOffset) <= 35 && metrics.centsOffset < 0 -> "A little low"
                    kotlin.math.abs(metrics.centsOffset) <= 35 -> "A little high"
                    metrics.centsOffset < 0 -> "Low · lift the note"
                    else -> "High · ease the note"
                }
            }
            delay(380L)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0x990A1214),
        border = BorderStroke(1.dp, SwaraColors.Teal.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = note,
                style = MaterialTheme.typography.titleLarge,
                color = SwaraColors.Teal,
                maxLines = 2,
            )
            Text(
                text = tuning,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.84f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VerticalVideoStageCard(
    title: String,
    target: PublishTarget,
    activeLyric: String,
    nextLyric: String,
    audioStateFlow: StateFlow<AudioEngineState>,
    cameraController: SingerCameraController,
    onSelectTarget: (PublishTarget) -> Unit,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val audioState = audioStateFlow.collectAsState()
    val effectiveTarget = if (target in videoFrameTargets) target else PublishTarget.REELS

    Surface(
        modifier = if (expanded) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (expanded) Color.Transparent else SwaraColors.Card,
        border = if (expanded) null else BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier
                .then(if (expanded) Modifier.fillMaxSize() else Modifier)
                .padding(if (expanded) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "9:16 Vertical Video Stage", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "1080p 9:16. Picture starts with your take, not the count-in.",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                videoFrameTargets.forEach { item ->
                    FilterChip(
                        selected = effectiveTarget == item,
                        onClick = { onSelectTarget(item) },
                        label = {
                            Text(
                                when (item) {
                                    PublishTarget.REELS -> "Reels 9:16"
                                    PublishTarget.SHORTS -> "Shorts 9:16"
                                    else -> "WhatsApp 9:16"
                                },
                            )
                        },
                    )
                }
            }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier.weight(1f, fill = true) else Modifier.height(320.dp)),
                contentAlignment = Alignment.Center,
            ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f),
                shape = RoundedCornerShape(18.dp),
                color = SwaraColors.Ink,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SingerCameraPreview(
                        controller = cameraController,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val m = audioState.value.metrics
                        val amp = (m.midLevel * 0.6f + m.bassLevel * 0.4f).coerceIn(0.08f, 1f)
                        val center = Offset(size.width / 2f, size.height * 0.44f)
                        drawCircle(
                            color = SwaraColors.Teal.copy(alpha = 0.18f),
                            radius = size.minDimension * (0.18f + amp * 0.16f),
                            center = center,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text = effectiveTarget.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = SwaraColors.Gold,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = activeLyric,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        if (nextLyric.isNotBlank()) {
                            Text(
                                text = nextLyric,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.68f),
                            )
                        }
                    }
                }
                }
            }
            }
        }
    }
}

internal fun isMusicOnlyBacking(name: String, relativePath: String): Boolean {
    val text = "$name $relativePath".lowercase()
    return text.contains("instrumental") || text.contains("music only")
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MusicalSetupCard(
    keyName: String,
    scaleMode: ScaleMode,
    tempoBpm: Float,
    keyShiftSemitones: Int,
    playbackRate: Float,
    countInEnabled: Boolean,
    metronomeEnabled: Boolean,
    headphoneFx: Boolean,
    punchStartMs: Long,
    punchEndMs: Long = 0L,
    showBackingShift: Boolean,
    guidePlaying: Boolean,
    onKey: (String) -> Unit,
    onMode: (ScaleMode) -> Unit,
    onTempo: (Float) -> Unit,
    onShift: (Int) -> Unit,
    onRate: (Float) -> Unit,
    onCountIn: (Boolean) -> Unit,
    onMetronome: (Boolean) -> Unit,
    onHeadphoneFx: (Boolean) -> Unit,
    onPlayGuide: () -> Unit,
    onClearPunch: () -> Unit,
    voiceGain: Float = 1f,
    trackGain: Float = 0.8f,
    backingDurationMs: Long = 0L,
    seekEnabled: Boolean = true,
    onVoiceGain: (Float) -> Unit = {},
    onTrackGain: (Float) -> Unit = {},
    onSeek: (Long) -> Unit = {},
) {
    val copy = LocalAppCopy.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Start in time & in key", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "4-beat count-in, click, and a key that matches your range.",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MusicalKeys.ALL.forEach { key ->
                    FilterChip(
                        selected = key == keyName,
                        onClick = { onKey(key) },
                        label = { Text(key) },
                    )
                }
            }
            Text(
                text = copy.raga,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScaleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == scaleMode,
                        onClick = { onMode(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = countInEnabled,
                    onClick = { onCountIn(!countInEnabled) },
                    label = { Text(if (countInEnabled) "Count-in on" else "Count-in off") },
                )
                FilterChip(
                    selected = metronomeEnabled,
                    onClick = { onMetronome(!metronomeEnabled) },
                    label = { Text(if (metronomeEnabled) "Click on" else "Click off") },
                )
                FilterChip(
                    selected = headphoneFx,
                    onClick = { onHeadphoneFx(!headphoneFx) },
                    label = { Text(copy.headphoneFx) },
                )
                FilterChip(
                    selected = guidePlaying,
                    onClick = onPlayGuide,
                    label = { Text(if (guidePlaying) copy.stopGuide else copy.playGuide) },
                )
            }
            Text(
                text = "Click tempo ${tempoBpm.roundToInt()} BPM",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Slider(
                value = tempoBpm,
                onValueChange = onTempo,
                valueRange = 60f..140f,
            )
            if (showBackingShift) {
                Text(
                    text = "Backing pitch ${if (keyShiftSemitones >= 0) "+" else ""}$keyShiftSemitones · speed ${(playbackRate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
                Slider(
                    value = keyShiftSemitones.toFloat(),
                    onValueChange = { onShift(it.roundToInt()) },
                    valueRange = -6f..6f,
                    steps = 11,
                )
                Slider(
                    value = playbackRate,
                    onValueChange = onRate,
                    valueRange = 0.5f..1.5f,
                )
            }
            if (punchStartMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (punchEndMs > punchStartMs) {
                            "Punch-in ${formatDuration(punchStartMs)}–${formatDuration(punchEndMs)}"
                        } else {
                            "Punch-in armed at ${formatDuration(punchStartMs)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Gold,
                    )
                    TextButton(onClick = onClearPunch) { Text("Clear") }
                }
            }
            if (backingDurationMs > 0L) {
                Text(
                    text = if (seekEnabled) {
                        "Start at ${formatDuration(punchStartMs)} · seek before you sing"
                    } else {
                        "Seek is locked while you record"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
                Slider(
                    value = punchStartMs.toFloat().coerceIn(0f, backingDurationMs.toFloat()),
                    onValueChange = { if (seekEnabled) onSeek(it.toLong()) },
                    valueRange = 0f..backingDurationMs.toFloat(),
                    enabled = seekEnabled,
                )
            }
            Text(
                text = "Hear mix · voice ${(voiceGain * 100).roundToInt()}% · track ${(trackGain * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Slider(value = voiceGain, onValueChange = onVoiceGain, valueRange = 0f..2f)
            Slider(value = trackGain, onValueChange = onTrackGain, valueRange = 0f..2f)
        }
    }
}

@Composable
internal fun TakeWaveform(
    peaks: FloatArray,
    startFraction: Float,
    endFraction: Float,
    playing: Boolean,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        drawRect(SwaraColors.Paper)
        val keepLeft = size.width * startFraction
        val keepRight = size.width * endFraction
        drawRect(
            color = SwaraColors.Teal.copy(alpha = if (playing) 0.22f else 0.14f),
            topLeft = Offset(keepLeft, 0f),
            size = Size((keepRight - keepLeft).coerceAtLeast(1f), size.height),
        )
        val step = size.width / peaks.size.coerceAtLeast(1).toFloat()
        val mid = size.height / 2f
        peaks.forEachIndexed { index, peak ->
            val x = index * step + step / 2f
            val half = (peak.coerceIn(0.06f, 1f) * size.height * 0.42f)
            val inside = x in keepLeft..keepRight
            drawLine(
                color = if (inside) SwaraColors.Teal else SwaraColors.Muted.copy(alpha = 0.35f),
                start = Offset(x, mid - half),
                end = Offset(x, mid + half),
                strokeWidth = (step * 0.62f).coerceIn(1.5.dp.toPx(), 4.dp.toPx()),
                cap = StrokeCap.Round,
            )
        }
        drawLine(SwaraColors.Gold, Offset(keepLeft, 4.dp.toPx()), Offset(keepLeft, size.height - 4.dp.toPx()), 2.dp.toPx())
        drawLine(SwaraColors.Gold, Offset(keepRight, 4.dp.toPx()), Offset(keepRight, size.height - 4.dp.toPx()), 2.dp.toPx())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayersMixerCard(
    parts: List<VoicePartUi>,
    selectedPartId: String?,
    canAddPart: Boolean,
    addLayerLabel: String,
    onSelect: (String) -> Unit,
    onMix: (String, Float?, Float?, Boolean?, Boolean?) -> Unit,
    onChain: (String, VocalPreset?) -> Unit,
    onAdd: (TrackRole) -> Unit,
    onRecord: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Vocal layers", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Record Lead, then stack Double, Harmony, and Ad-lib. Mix here before Mix & Save.",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            parts.forEach { part ->
                val selected = selectedPartId == part.id
                LayerEditorRow(
                    layer = part,
                    selected = selected,
                    onSelect = { onSelect(part.id) },
                    onVolumeChange = { onMix(part.id, it, null, null, null) },
                    onMute = { onMix(part.id, null, null, !part.mute, null) },
                    onSolo = { onMix(part.id, null, null, null, !part.solo) },
                    onRecord = { onRecord(part.id) },
                    onChain = { onChain(part.id, it) },
                )
                if (selected) {
                    Text(
                        text = when {
                            part.pan < -0.05f -> "Pan L ${(-part.pan * 100).roundToInt()}%"
                            part.pan > 0.05f -> "Pan R ${(part.pan * 100).roundToInt()}%"
                            else -> "Pan center"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                    Slider(
                        value = part.pan,
                        onValueChange = { onMix(part.id, null, it, null, null) },
                        valueRange = -1f..1f,
                    )
                }
            }
            if (canAddPart) {
                TextButton(
                    onClick = { onAdd(TrackRole.DOUBLE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ $addLayerLabel")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAdd(TrackRole.HARMONY) }) { Text("+ Harmony") }
                    OutlinedButton(onClick = { onAdd(TrackRole.ADLIB) }) { Text("+ Ad-lib") }
                    OutlinedButton(onClick = { onAdd(TrackRole.EXTRA) }) { Text("+ Extra") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TakeEditCard(
    take: RecordedTakeUi,
    wavFile: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPrefer: () -> Unit,
    onTrim: (Long, Long) -> Unit,
    onPunch: (Long) -> Unit,
    onPunchRegion: (Long, Long) -> Unit = { start, _ -> onPunch(start) },
    onSplit: (Long) -> Unit = {},
    onMove: (Long) -> Unit = {},
    onFades: (Long, Long) -> Unit = { _, _ -> },
    onRestore: () -> Unit = {},
) {
    var startMs by remember(take.id, take.sourceInMs) { mutableStateOf(take.sourceInMs.toFloat()) }
    var endMs by remember(take.id, take.sourceInMs, take.durationMs) {
        mutableStateOf((take.sourceInMs + take.durationMs).toFloat())
    }
    var placedMs by remember(take.id, take.timelineStartMs) { mutableStateOf(take.timelineStartMs.toFloat()) }
    var fadeInMs by remember(take.id, take.fadeInMs) { mutableStateOf(take.fadeInMs.toFloat()) }
    var fadeOutMs by remember(take.id, take.fadeOutMs) { mutableStateOf(take.fadeOutMs.toFloat()) }
    val maxMs = take.sourceDurationMs.toFloat().coerceAtLeast(1f)
    var peaks by remember(take.id, wavFile.path) { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(take.id, wavFile.path) {
        peaks = withContext(Dispatchers.IO) { WavPeaks.fromFile(wavFile) }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = when {
            !take.active -> SwaraColors.Card
            take.preferred || isPlaying -> SwaraColors.TealWash
            else -> SwaraColors.Card
        },
        border = BorderStroke(1.dp, if (take.preferred && take.active || isPlaying) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = take.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = take.durationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwaraColors.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDelete) { Text("Delete", color = SwaraColors.Clay) }
                    Button(onClick = onPlay) { Text(if (isPlaying) "Stop" else "Play") }
                }
            }
            Text(
                text = "Keep ${formatDuration(startMs.toLong())} to ${formatDuration(endMs.toLong())}",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            if (peaks.isNotEmpty()) {
                TakeWaveform(
                    peaks = peaks,
                    startFraction = (startMs / maxMs).coerceIn(0f, 1f),
                    endFraction = (endMs / maxMs).coerceIn(0f, 1f),
                    playing = isPlaying,
                )
            }
            Text(
                text = "Cut the start",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Gold,
            )
            Slider(value = startMs, onValueChange = { startMs = it.coerceIn(0f, endMs - 250f) }, valueRange = 0f..maxMs)
            Text(
                text = "Cut the end",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Gold,
            )
            Slider(value = endMs, onValueChange = { endMs = it.coerceIn(startMs + 250f, maxMs) }, valueRange = 0f..maxMs)
            Text(
                text = "Place on timeline ${formatDuration(placedMs.toLong())}",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Slider(
                value = placedMs,
                onValueChange = { placedMs = it },
                onValueChangeFinished = { onMove(placedMs.toLong().coerceAtLeast(0L)) },
                valueRange = 0f..120_000f,
            )
            Text(
                text = "Fade in ${fadeInMs.roundToInt()} ms · fade out ${fadeOutMs.roundToInt()} ms",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Slider(
                value = fadeInMs,
                onValueChange = { fadeInMs = it },
                onValueChangeFinished = { onFades(fadeInMs.toLong(), fadeOutMs.toLong()) },
                valueRange = 0f..400f,
            )
            Slider(
                value = fadeOutMs,
                onValueChange = { fadeOutMs = it },
                onValueChangeFinished = { onFades(fadeInMs.toLong(), fadeOutMs.toLong()) },
                valueRange = 0f..400f,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val start = startMs.toLong().coerceAtLeast(0L)
                        val duration = (endMs.toLong() - start).coerceAtLeast(250L)
                        onTrim(start, duration)
                    },
                ) { Text("Keep this section") }
                OutlinedButton(onClick = { onPunch(startMs.toLong()) }) { Text("Punch from here") }
                OutlinedButton(
                    onClick = { onPunchRegion(startMs.toLong(), endMs.toLong()) },
                ) { Text("Re-record this window") }
                OutlinedButton(
                    onClick = { onSplit(((startMs + endMs) / 2f).toLong()) },
                ) { Text("Split") }
                FilterChip(
                    selected = take.preferred && take.active,
                    onClick = onPrefer,
                    label = { Text(if (take.preferred && take.active) "This take" else "Use this take") },
                )
                if (!take.active) {
                    OutlinedButton(onClick = onRestore) { Text("Bring back") }
                }
            }
        }
    }
}

internal fun writeSampleDroneWav(file: File, sampleRate: Int, durationSeconds: Int, keyName: String = "C") {
    val totalSamples = sampleRate * durationSeconds
    val totalAudioBytes = totalSamples * 2L
    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(0L)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + totalAudioBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioBytes.toInt())
        }
        raf.write(header.array())
        val tonic = MusicalKeys.tonicC3Hz(keyName)
        val octave = tonic * 2.0
        val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            if (chunk.remaining() < 2) {
                raf.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
            val t = i.toDouble() / sampleRate.toDouble()
            val swell = 0.82 + 0.18 * sin(2.0 * PI * 0.20 * t)
            val wave = (
                0.72 * sin(2.0 * PI * tonic * t) +
                    0.28 * sin(2.0 * PI * octave * t)
                ) * swell
            val pcm = (wave * 9500.0).toInt().coerceIn(-32767, 32767).toShort()
            chunk.putShort(pcm)
        }
        if (chunk.position() > 0) {
            raf.write(chunk.array(), 0, chunk.position())
        }
    }
}

internal fun writeGuideMelodyWav(
    file: File,
    sampleRate: Int,
    keyName: String,
    mode: ScaleMode,
) {
    val scale = MusicalKeys.scaleForKey(keyName, mode)
    val noteMs = 420
    val gapMs = 80
    val totalMs = scale.size * (noteMs + gapMs)
    val totalSamples = (sampleRate * totalMs) / 1000
    val totalAudioBytes = totalSamples * 2L
    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(0L)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + totalAudioBytes).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioBytes.toInt())
        }
        raf.write(header.array())
        val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        val noteSamples = (sampleRate * noteMs) / 1000
        val gapSamples = (sampleRate * gapMs) / 1000
        var written = 0
        fun putSample(value: Short) {
            if (chunk.remaining() < 2) {
                raf.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
            chunk.putShort(value)
            written += 1
        }
        scale.forEach { note ->
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / sampleRate.toDouble()
                val env = when {
                    i < noteSamples / 12 -> i.toDouble() / (noteSamples / 12.0)
                    i > noteSamples * 3 / 4 -> ((noteSamples - i).toDouble() / (noteSamples / 4.0))
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
                val wave = sin(2.0 * PI * note.hz * t) * env
                putSample((wave * 11_000.0).toInt().coerceIn(-32767, 32767).toShort())
            }
            repeat(gapSamples) { putSample(0) }
        }
        while (written < totalSamples) putSample(0)
        if (chunk.position() > 0) {
            raf.write(chunk.array(), 0, chunk.position())
        }
    }
}

internal fun ScaleMode.label(): String = when (this) {
    ScaleMode.MAJOR -> "Major"
    ScaleMode.MINOR -> "Minor"
    ScaleMode.MOHANAM -> "Mohanam"
    ScaleMode.BHAIRAV -> "Bhairav"
    ScaleMode.YAMAN -> "Yaman"
}

internal fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Warm-up"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

internal fun GearRoute.label(): String = when (this) {
    GearRoute.WIRED -> "Headphones"
    GearRoute.USB -> "USB mic"
    GearRoute.BLUETOOTH -> "Bluetooth"
    GearRoute.SPEAKER -> "Speaker"
    GearRoute.BUILTIN_MIC -> "Phone mic"
    GearRoute.UNKNOWN -> "Headphones"
}

internal fun VocalPreset.label(): String = when (this) {
    VocalPreset.RAW -> "Dry"
    VocalPreset.CLOSE -> "Clean"
    VocalPreset.STAGE -> "Warm"
    VocalPreset.RADIO -> "Radio"
    VocalPreset.BRIGHT -> "Bright"
    VocalPreset.CUSTOM -> "Custom"
}

internal fun VocalPreset.description(): String = when (this) {
    VocalPreset.RAW -> "Dry: almost the mic. Light rumble only. No hall, no punch."
    VocalPreset.CLOSE -> "Clean: EQ, light compression, a little air. Track ducks."
    VocalPreset.STAGE -> "Warm: soft hall and body behind the notes."
    VocalPreset.RADIO -> "Radio: de-esser, compression, sparkle. Music steps back."
    VocalPreset.BRIGHT -> "Bright: presence and air, tighter hall, less rumble."
    VocalPreset.CUSTOM -> "Your sliders on Mix & Save. Tap to open them."
}

internal fun VocalPreset.mixHearLabel(): String = label()

internal fun VocalPreset.chipColors(): Pair<Color, Color> =
    when (this) {
        VocalPreset.CLOSE -> SwaraColors.Teal to SwaraColors.TealWash
        VocalPreset.STAGE -> SwaraColors.Gold to SwaraColors.GoldWash
        VocalPreset.RADIO -> SwaraColors.Rose to SwaraColors.RoseWash
        VocalPreset.BRIGHT -> SwaraColors.Ink to SwaraColors.GoldWash
        VocalPreset.RAW -> SwaraColors.Muted to SwaraColors.Card
        VocalPreset.CUSTOM -> SwaraColors.Ink to SwaraColors.Card
    }

internal fun listeningCopy(output: GearRoute): String = when (output) {
    GearRoute.WIRED -> "Best for singing in time."
    GearRoute.USB -> "Direct monitoring through a USB mic or interface."
    GearRoute.BLUETOOTH -> "Good for playback. Singing along over Bluetooth feels late."
    GearRoute.SPEAKER -> "Fine for solo takes. Use headphones when singing over a backing track."
    GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN -> "Plug in headphones before you sing."
}
