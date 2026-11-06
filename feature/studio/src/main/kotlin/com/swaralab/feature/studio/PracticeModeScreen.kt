package com.swaralab.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swaralab.domain.audio.LoopRegion
import com.swaralab.domain.audio.PitchSample
import com.swaralab.ui.AnimatedMusicBackground
import com.swaralab.ui.SwaraColors

data class PracticeUiState(
    val currentPitch: PitchSample? = null,
    val currentLyric: String? = null,
    val isLooping: Boolean = false,
    val loop: LoopRegion? = null,
    val tempo: Float = 0.8f,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val backingBusy: Boolean = false,
    val backingProgress: Float = 0f,
)

@Composable
fun PracticeModeScreen(
    uiState: PracticeUiState,
    onTempoChange: (Float) -> Unit,
    onLoopToggle: () -> Unit,
    onSetLoopPoint: () -> Unit,
    onPlayToggle: () -> Unit,
    onExitPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedMusicBackground(intensity = 0.5f)
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Practice Mode", style = MaterialTheme.typography.titleLarge, color = Color.White)
                TextButton(onClick = onExitPractice) { Text("Exit") }
            }
            Spacer(Modifier.height(16.dp))
            PitchVisualizer(
                sample = uiState.currentPitch,
                modifier = Modifier.fillMaxWidth().height(70.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = uiState.currentLyric ?: "♪ ♪ ♪",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FilterChip(
                    selected = uiState.isLooping,
                    onClick = onLoopToggle,
                    label = { Text(if (uiState.isLooping) "Looping" else "Loop Off") },
                )
                OutlinedButton(onClick = onSetLoopPoint) {
                    Text(
                        when {
                            uiState.loop != null -> "Loop ${formatPracticeMs(uiState.loop.startMs)}–${formatPracticeMs(uiState.loop.endMs)}"
                            else -> "Set Loop Here"
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Tempo: ${"%.1f".format(uiState.tempo)}x", color = Color.White)
            Slider(
                value = uiState.tempo.coerceIn(0.5f, 1f),
                onValueChange = onTempoChange,
                valueRange = 0.5f..1.0f,
                enabled = !uiState.isPlaying,
            )
            if (uiState.backingBusy) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { uiState.backingProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.height(22.dp),
                        color = SwaraColors.Teal,
                    )
                    Text(" Processing track…", color = Color.White.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onPlayToggle,
                enabled = !uiState.backingBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when {
                        uiState.isPaused -> "Play"
                        uiState.isPlaying -> "Pause"
                        else -> "Play"
                    },
                )
            }
        }
    }
}

private fun formatPracticeMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}
