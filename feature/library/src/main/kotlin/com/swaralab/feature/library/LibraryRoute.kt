package com.swaralab.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.ui.ChoiceCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class LibraryRow(
    val id: String,
    val title: String,
    val detail: String,
)

class LibraryViewModel(
    private val listSessions: ListSessions,
) : ViewModel() {
    private val rowsState = MutableStateFlow<List<LibraryRow>>(emptyList())
    val rows: StateFlow<List<LibraryRow>> = rowsState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            rowsState.value = listSessions().map { summary ->
                val day = Instant.ofEpochMilli(summary.updatedAtEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .format(format)
                LibraryRow(
                    id = summary.id,
                    title = summary.title,
                    detail = "${summary.kind.label()} · $day",
                )
            }
        }
    }

    companion object {
        fun factory(listSessions: ListSessions): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(listSessions) }
        }
    }
}

private fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Practice"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

@Composable
fun LibraryRoute(
    listSessions: ListSessions,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(listSessions))
    val rows by viewModel.rows.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Library", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Songs you start stay on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        if (rows.isEmpty()) {
            Text(
                text = "A practice, a song, or a video will show up here with its session folder.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            rows.forEach { row ->
                ChoiceCard(title = row.title, body = row.detail, onClick = { onOpen(row.id) })
            }
        }
    }
}
