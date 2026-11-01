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
import com.swaralab.domain.usecase.CreateSession
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraWordmark
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
                val document = createSession(kind, sessionTitle(kind))
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

private fun sessionTitle(kind: SessionKind): String {
    val clock = LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val name = when (kind) {
        SessionKind.PRACTICE -> "Practice"
        SessionKind.SONG -> "Song"
        SessionKind.VIDEO -> "Video"
    }
    return "$name · $clock"
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
        SwaraWordmark()
        Text(
            text = "Step in, put the words in front of you, and shape your voice.",
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        ChoiceCard(
            title = "Solo or Duet Song",
            body = "Lyrics on screen, Studio or Hall voice, and a second part when you want a duet.",
            onClick = { viewModel.start(SessionKind.SONG) },
        )
        ChoiceCard(
            title = "Vertical Video",
            body = "Sing for Reels, Shorts, or WhatsApp with the lyrics kept inside the frame.",
            onClick = { viewModel.start(SessionKind.VIDEO) },
        )
        ChoiceCard(
            title = "Vocal Warm-up",
            body = "Work on a line a cappella and listen back.",
            onClick = { viewModel.start(SessionKind.PRACTICE) },
        )
    }
}
