package com.swaralab.feature.publish

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.ExportKind
import com.swaralab.ui.AppCopy
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.VocalHearGraph
import com.swaralab.ui.VocalHearReadout
import kotlin.math.roundToInt

@Composable
internal fun MixDeskSections(
    state: PublishUi,
    viewModel: PublishViewModel,
    audioState: AudioEngineState,
    copy: AppCopy,
    context: Context,
    onSaveFile: (String) -> Unit,
) {

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
                                FilterChip(
                                    selected = track.solo,
                                    onClick = { viewModel.toggleTrackSolo(track.id) },
                                    label = { Text("Solo") },
                                )
                            }
                        }
                        Slider(
                            value = track.gainDb,
                            onValueChange = { viewModel.setTrackGain(track.id, it) },
                            valueRange = -18f..6f,
                        )
                        Text(
                            text = when {
                                track.pan < -0.05f -> "Pan L ${(-track.pan * 100).roundToInt()}%"
                                track.pan > 0.05f -> "Pan R ${(track.pan * 100).roundToInt()}%"
                                else -> "Pan center"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwaraColors.Muted,
                        )
                        Slider(
                            value = track.pan,
                            onValueChange = { viewModel.setTrackPan(track.id, it) },
                            valueRange = -1f..1f,
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
                Text(text = "Hear the mix", style = MaterialTheme.typography.titleLarge)
                VocalHearGraph(
                    readout = VocalHearReadout(
                        waveform = audioState.metrics.waveformHistory,
                        pitchHistory = audioState.metrics.pitchHistory,
                        bass = audioState.metrics.bassLevel,
                        mid = audioState.metrics.midLevel,
                        air = audioState.metrics.trebleLevel,
                        rmsDb = audioState.metrics.rmsDb,
                        pitchHz = audioState.metrics.pitchHz,
                        noteName = audioState.metrics.noteName,
                        swaraName = audioState.metrics.swaraName,
                        cents = audioState.metrics.centsOffset,
                        active = audioState.playingRelativePath != null,
                    ),
                )
                EffectSliderRow(
                    label = "Pitch to key (${state.keyName})",
                    valueText = "${(state.chain.pitchCorrect * 100).roundToInt()}%",
                    value = state.chain.pitchCorrect,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(pitchCorrect = it) },
                )
                EffectSliderRow(
                    label = "Pitch speed",
                    valueText = "${(state.chain.pitchSpeed * 100).roundToInt()}%",
                    value = state.chain.pitchSpeed,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(pitchSpeed = it) },
                )
                var showMoreMix by remember { mutableStateOf(false) }
                TextButton(onClick = { showMoreMix = !showMoreMix }) {
                    Text(if (showMoreMix) "Hide mix controls" else "More mix controls")
                }
                if (showMoreMix) {
                EffectSliderRow(
                    label = "Tone (warm ↔ air)",
                    valueText = if (state.chain.tone >= 0f) {
                        "Air ${(state.chain.tone * 100).roundToInt()}%"
                    } else {
                        "Warm ${(-state.chain.tone * 100).roundToInt()}%"
                    },
                    value = state.chain.tone,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.updateChainSliders(tone = it) },
                )
                EffectSliderRow(
                    label = "De-ess",
                    valueText = "${(state.chain.deEss * 100).roundToInt()}%",
                    value = state.chain.deEss,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(deEss = it) },
                )
                EffectSliderRow(
                    label = "Thicken",
                    valueText = "${(state.chain.thicken * 100).roundToInt()}%",
                    value = state.chain.thicken,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(thicken = it) },
                )
                EffectSliderRow(
                    label = "Effect amount",
                    valueText = "${(state.chain.effectAmount * 100).roundToInt()}%",
                    value = state.chain.effectAmount,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(effectAmount = it) },
                )
                EffectSliderRow(
                    label = "Fade out",
                    valueText = "${(state.chain.fadeOut * 100).roundToInt()}%",
                    value = state.chain.fadeOut,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(fadeOut = it) },
                )
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
                    label = "Noise clean",
                    valueText = if (state.chain.denoise <= 0.02f) "Off" else "${(state.chain.denoise * 100).roundToInt()}%",
                    value = state.chain.denoise,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(denoise = it) },
                )
                EffectSliderRow(
                    label = "Noise gate",
                    valueText = if (state.chain.noiseGate <= 0.02f) "Off" else "${(state.chain.noiseGate * 100).roundToInt()}%",
                    value = state.chain.noiseGate,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(noiseGate = it) },
                )
                EffectSliderRow(
                    label = "Breath",
                    valueText = if (state.chain.breathReduction <= 0.02f) "Off" else "${(state.chain.breathReduction * 100).roundToInt()}%",
                    value = state.chain.breathReduction,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(breathReduction = it) },
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
                if (state.busy) {
                    LinearProgressIndicator(
                        progress = { state.exportProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.exportKind(ExportKind.VOCALS) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Vocals") }
                    OutlinedButton(
                        onClick = { viewModel.exportKind(ExportKind.BACKING) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Backing") }
                    OutlinedButton(
                        onClick = { viewModel.exportKind(ExportKind.COMPACT) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Compact") }
                }
                if (state.hasCameraVideo) {
                    Button(
                        onClick = { viewModel.exportKind(ExportKind.VIDEO) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(copy.shareCamera) }
                }
                OutlinedButton(
                    onClick = { viewModel.exportKind(ExportKind.LYRIC_VIDEO) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(copy.lyricCard) }
                Text(text = "Stem export", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = state.stems.mix,
                        onClick = { viewModel.setStemFlag(mix = !state.stems.mix) },
                        label = { Text("Final mix") },
                    )
                    FilterChip(
                        selected = state.stems.leadDry,
                        onClick = { viewModel.setStemFlag(leadDry = !state.stems.leadDry) },
                        label = { Text("Lead dry") },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = state.stems.vocals,
                        onClick = { viewModel.setStemFlag(vocals = !state.stems.vocals) },
                        label = { Text("All vocals") },
                    )
                    FilterChip(
                        selected = state.stems.layers,
                        onClick = { viewModel.setStemFlag(layers = !state.stems.layers) },
                        label = { Text("Layers") },
                    )
                    FilterChip(
                        selected = state.stems.backing,
                        onClick = { viewModel.setStemFlag(backing = !state.stems.backing) },
                        label = { Text("Backing") },
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.exportKind(ExportKind.STEMS) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export stems") }
                OutlinedButton(
                    onClick = { viewModel.shareInvite(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Invite a friend to sing the other part") }
                if (state.exportedRelativePath != null) {
                    val isPlaying = audioState.playingRelativePath == state.exportedRelativePath
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!state.exportedMime.contains("zip")) {
                            OutlinedButton(
                                onClick = viewModel::togglePlayExport,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (isPlaying) "Stop" else "Play")
                            }
                        }
                        Button(
                            onClick = { viewModel.shareExportToApps(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Share")
                        }
                        OutlinedButton(
                            onClick = {
                                onSaveFile(state.exportedFileName ?: "swaralab-mix.wav")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save")
                        }
                    }
                }
                state.status?.let { message ->
                    val failed = message.contains("Could") ||
                        message.contains("Record at least") ||
                        message.contains("Sing a camera") ||
                        message.contains("Add a backing")
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) SwaraColors.Clay else SwaraColors.Teal,
                    )
                }
            }
        }

        Text(text = "Target format", style = MaterialTheme.typography.titleLarge)
        PublishTarget.entries.forEach { target ->
            val (accent, wash) = when (target) {
                PublishTarget.REELS, PublishTarget.SHORTS ->
                    SwaraColors.Rose to SwaraColors.RoseWash
                PublishTarget.WHATSAPP, PublishTarget.SQUARE ->
                    SwaraColors.Gold to SwaraColors.GoldWash
                else -> SwaraColors.Teal to SwaraColors.TealWash
            }
            ChoiceCard(
                title = target.label(),
                body = shareCopy(target),
                selected = state.target == target,
                accent = accent,
                accentWash = wash,
                onClick = { viewModel.select(target) },
            )
        }
}
