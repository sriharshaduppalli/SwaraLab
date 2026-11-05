package com.swaralab.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swaralab.domain.audio.LiveMonitorEffect
import com.swaralab.ui.SwaraColors
import kotlin.math.roundToInt

@Composable
fun EffectsPanel(
    currentEffect: LiveMonitorEffect,
    onEffectChange: (LiveMonitorEffect) -> Unit,
    voiceGain: Float,
    onVoiceGainChange: (Float) -> Unit,
    trackGain: Float,
    onTrackGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp)) {
        Text("Headphones only. The dry take stays clean.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = currentEffect is LiveMonitorEffect.None,
                onClick = { onEffectChange(LiveMonitorEffect.None) },
                label = { Text("Clean") },
            )
            FilterChip(
                selected = currentEffect is LiveMonitorEffect.Reverb,
                onClick = { onEffectChange(LiveMonitorEffect.Reverb(0.6f, 0.4f)) },
                label = { Text("Reverb") },
            )
            FilterChip(
                selected = currentEffect is LiveMonitorEffect.Pitch,
                onClick = { onEffectChange(LiveMonitorEffect.Pitch(2f)) },
                label = { Text("Pitch +") },
            )
        }
        Text(
            "Pitch + brightens headphones. It does not change the saved WAV.",
            style = MaterialTheme.typography.bodySmall,
            color = SwaraColors.Muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Voice ${(voiceGain * 100).roundToInt()}%")
        Slider(value = voiceGain, onValueChange = onVoiceGainChange, valueRange = 0f..2f)
        Text("Track ${(trackGain * 100).roundToInt()}%")
        Slider(value = trackGain, onValueChange = onTrackGainChange, valueRange = 0f..1.5f)
    }
}
