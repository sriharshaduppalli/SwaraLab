package com.swaralab.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.swaralab.domain.usecase.DeleteSession
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.ui.SwaraColors
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
    private val deleteSession: DeleteSession,
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

    fun delete(id: String) {
        viewModelScope.launch {
            deleteSession(id)
            refresh()
        }
    }

    companion object {
        fun factory(
            listSessions: ListSessions,
            deleteSession: DeleteSession,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(listSessions, deleteSession) }
        }
    }
}

private fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Warm-up"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

@Composable
fun LibraryRoute(
    listSessions: ListSessions,
    deleteSession: DeleteSession,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(listSessions, deleteSession),
    )
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
        Text(text = "Songs", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Open a song to sing, edit its lyrics, or listen back to takes.",
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )
        if (rows.isEmpty()) {
            Text(
                text = "No songs yet. Start one from Sing.",
                style = MaterialTheme.typography.bodyLarge,
                color = SwaraColors.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            rows.forEach { row ->
                Surface(
                    onClick = { onOpen(row.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = SwaraColors.Card,
                    border = BorderStroke(1.dp, SwaraColors.Line),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = SwaraColors.Ink,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = row.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwaraColors.Muted,
                            )
                        }
                        TextButton(onClick = { viewModel.delete(row.id) }) {
                            Text("Delete", color = SwaraColors.Clay)
                        }
                    }
                }
            }
        }
    }
}
