package com.swaralab.feature.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdateGear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudioUi(
    val title: String,
    val kind: String,
    val selected: GearRoute?,
    val listening: String,
    val timing: String,
    val rights: String,
    val chain: String,
    val saved: String,
)

class StudioViewModel(
    private val sessionId: String,
    private val loadSession: LoadSession,
    private val updateGear: UpdateGear,
    private val audio: AudioEngine,
) : ViewModel() {
    private val uiState = MutableStateFlow<StudioUi?>(null)
    val ui: StateFlow<StudioUi?> = uiState.asStateFlow()

    init {
        refresh()
    }

    fun select(route: GearRoute) {
        viewModelScope.launch {
            updateGear(sessionId, route)
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            audio.arm(ArmRequest(sessionId = document.id, output = document.gear.output))
            uiState.value = StudioUi(
                title = document.title,
                kind = document.kind.label(),
                selected = document.gear.output.takeUnless { it == GearRoute.UNKNOWN },
                listening = listeningCopy(document.gear.output),
                timing = timingCopy(document.latency.measuredRoundTripMs),
                rights = rightsCopy(document.kind, document.rights?.singerConfirmedRights == true),
                chain = "Voice chain starts at ${document.chain.preset.label()}. Effects sit on top of the dry vocal.",
                saved = "Saved on this phone · ${document.id.take(8)}",
            )
        }
    }

    companion object {
        fun factory(
            sessionId: String,
            loadSession: LoadSession,
            updateGear: UpdateGear,
            audio: AudioEngine,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { StudioViewModel(sessionId, loadSession, updateGear, audio) }
        }
    }
}

private val listeningRoutes = listOf(GearRoute.WIRED, GearRoute.USB, GearRoute.BLUETOOTH, GearRoute.SPEAKER)

@Composable
fun StudioRoute(
    sessionId: String,
    loadSession: LoadSession,
    updateGear: UpdateGear,
    audio: AudioEngine,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.factory(sessionId, loadSession, updateGear, audio),
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
        TextButton(onClick = onBack) { Text("Library") }
        Text(text = state.title, style = MaterialTheme.typography.headlineLarge)
        Text(text = state.kind, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Text(text = "Listening", style = MaterialTheme.typography.titleLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listeningRoutes.forEach { route ->
                FilterChip(
                    selected = state.selected == route,
                    onClick = { viewModel.select(route) },
                    label = { Text(route.label()) },
                )
            }
        }
        Text(text = state.listening, style = MaterialTheme.typography.bodyLarge)
        Text(text = state.timing, style = MaterialTheme.typography.bodyMedium)
        Text(text = state.rights, style = MaterialTheme.typography.bodyMedium)
        Text(text = state.chain, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = state.saved,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        TextButton(onClick = onPublish) { Text("Export setup") }
    }
}

private fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Practice"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

private fun GearRoute.label(): String = when (this) {
    GearRoute.WIRED -> "Wired"
    GearRoute.USB -> "USB"
    GearRoute.BLUETOOTH -> "Bluetooth"
    GearRoute.SPEAKER -> "Speaker"
    GearRoute.BUILTIN_MIC -> "Phone mic"
    GearRoute.UNKNOWN -> "Not chosen"
}

private fun VocalPreset.label(): String = when (this) {
    VocalPreset.RAW -> "Raw"
    VocalPreset.CLOSE -> "Close"
    VocalPreset.STAGE -> "Stage"
    VocalPreset.RADIO -> "Radio"
    VocalPreset.CUSTOM -> "Custom"
}

private fun listeningCopy(output: GearRoute): String = when (output) {
    GearRoute.WIRED, GearRoute.USB ->
        "This route can keep the vocal in time. A timing check will measure the delay before the first take."
    GearRoute.BLUETOOTH ->
        "Bluetooth can play a take back. Singing along on Bluetooth lands late."
    GearRoute.SPEAKER ->
        "The speaker stays quiet during a take so the backing track does not leak into the vocal."
    GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN ->
        "Choose wired or USB before a take."
}

private fun timingCopy(measuredRoundTripMs: Double?): String {
    return if (measuredRoundTripMs == null) {
        "Timing check has not been run."
    } else {
        "Measured delay ${measuredRoundTripMs.toInt()} ms."
    }
}

private fun rightsCopy(kind: SessionKind, confirmed: Boolean): String = when {
    confirmed -> "You confirmed you can use the imported track."
    kind == SessionKind.PRACTICE ->
        "Practice can start a cappella. A backing track joins after you confirm you can use it."
    else -> "A backing track joins this session after you confirm you can use it."
}
