package com.swaralab.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.defaultTitle
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.ui.ChoiceCard
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val createSession: CreateSession,
) : ViewModel() {
    private val openedIds = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opened: SharedFlow<String> = openedIds.asSharedFlow()
    private var busy = false

    fun start(kind: SessionKind) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val document = createSession(kind, defaultTitle(kind))
                openedIds.emit(document.id)
            } finally {
                busy = false
            }
        }
    }

    companion object {
        fun factory(createSession: CreateSession): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(createSession) }
        }
    }
}

@Composable
fun HomeRoute(
    createSession: CreateSession,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(createSession))
    LaunchedEffect(viewModel) {
        viewModel.opened.collect(onOpen)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "SwaraLab", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Record a vocal, keep the session, and prepare a file worth sharing.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(8.dp))
        ChoiceCard(
            title = "Practice",
            body = "Sing a line, listen back, and do it again.",
            onClick = { viewModel.start(SessionKind.PRACTICE) },
        )
        ChoiceCard(
            title = "Song",
            body = "Start an original, or a track you already have the right to use.",
            onClick = { viewModel.start(SessionKind.SONG) },
        )
        ChoiceCard(
            title = "Video",
            body = "Frame a vertical performance for Reels, Shorts, or WhatsApp.",
            onClick = { viewModel.start(SessionKind.VIDEO) },
        )
    }
}
