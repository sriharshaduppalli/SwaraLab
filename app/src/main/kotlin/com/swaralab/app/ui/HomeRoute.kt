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
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.usecase.CreateSession
import com.swaralab.domain.usecase.ListSessions
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraWordmark
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val createSession: CreateSession,
    private val listSessions: ListSessions,
) : ViewModel() {
    private val openedIds = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opened: SharedFlow<String> = openedIds.asSharedFlow()
    private val lastState = MutableStateFlow<Pair<String, String>?>(null)
    val lastSong: StateFlow<Pair<String, String>?> = lastState.asStateFlow()
    private var busy = false

    init {
        viewModelScope.launch { refreshLast() }
    }

    fun start(kind: SessionKind) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val recentEmpty = listSessions().firstOrNull { summary ->
                    summary.kind == kind &&
                        summary.takeCount == 0 &&
                        System.currentTimeMillis() - summary.updatedAtEpochMs < 24 * 60 * 60 * 1000L
                }
                if (recentEmpty != null) {
                    openedIds.emit(recentEmpty.id)
                } else {
                    val document = createSession(kind, sessionTitle(kind))
                    openedIds.emit(document.id)
                }
            } finally {
                busy = false
            }
        }
    }

    fun continueLast() {
        val last = lastState.value ?: return
        viewModelScope.launch { openedIds.emit(last.first) }
    }

    private suspend fun refreshLast() {
        val last = listSessions().firstOrNull()
        lastState.value = last?.let { it.id to it.title }
    }

    companion object {
        fun factory(
            createSession: CreateSession,
            listSessions: ListSessions,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(createSession, listSessions) }
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
    listSessions: ListSessions,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(createSession, listSessions))
    val copy = LocalAppCopy.current
    val lastSong by viewModel.lastSong.collectAsState()
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
            text = copy.homeLine,
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        lastSong?.let { (_, title) ->
            ChoiceCard(
                title = copy.continueLast,
                body = title,
                onClick = viewModel::continueLast,
            )
        }
        ChoiceCard(
            title = copy.soloTitle,
            body = copy.soloBody,
            onClick = { viewModel.start(SessionKind.SONG) },
        )
        ChoiceCard(
            title = copy.videoTitle,
            body = copy.videoBody,
            onClick = { viewModel.start(SessionKind.VIDEO) },
        )
        ChoiceCard(
            title = copy.warmupTitle,
            body = copy.warmupBody,
            onClick = { viewModel.start(SessionKind.PRACTICE) },
        )
    }
}
