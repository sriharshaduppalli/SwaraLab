package com.swaralab.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.swaralab.ui.R as UiR
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.CompactChoiceCard
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraWordmark
import com.swaralab.ui.studioAtmosphere
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

    fun refreshLastSong() {
        viewModelScope.launch { refreshLast() }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLastSong()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier.fillMaxSize().studioAtmosphere(BoothRoom.HOME)) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = SwaraColors.Card,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(UiR.drawable.swaralab_mark),
                    contentDescription = "SwaraLab",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(72.dp)
                        .alpha(0.28f),
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Box(
                        Modifier
                            .padding(bottom = 10.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(SwaraColors.Teal, RoundedCornerShape(4.dp)),
                    )
                    SwaraWordmark()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = copy.homeLine,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Muted,
                    )
                }
            }
        }
        Text(
            text = "Start a take",
            style = MaterialTheme.typography.titleMedium,
            color = SwaraColors.Teal,
        )
        lastSong?.let { (_, title) ->
            Surface(
                onClick = viewModel::continueLast,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SwaraColors.GoldWash,
                border = BorderStroke(1.dp, SwaraColors.Gold),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = copy.continueLast,
                        style = MaterialTheme.typography.labelLarge,
                        color = SwaraColors.Gold,
                    )
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = SwaraColors.Ink)
                }
            }
        }
        Surface(
            onClick = { viewModel.start(SessionKind.SONG) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = SwaraColors.TealWash,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.5.dp, SwaraColors.Teal),
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
                Text(
                    text = "Start here",
                    style = MaterialTheme.typography.labelLarge,
                    color = SwaraColors.Teal,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = copy.soloTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SwaraColors.Teal,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = copy.soloBody,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SwaraColors.Ink,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactChoiceCard(
                title = copy.videoTitle,
                body = copy.videoBody,
                onClick = { viewModel.start(SessionKind.VIDEO) },
                modifier = Modifier.weight(1f),
                accent = SwaraColors.Rose,
                accentWash = SwaraColors.RoseWash,
            )
            CompactChoiceCard(
                title = copy.warmupTitle,
                body = copy.warmupBody,
                onClick = { viewModel.start(SessionKind.PRACTICE) },
                modifier = Modifier.weight(1f),
                accent = SwaraColors.Gold,
                accentWash = SwaraColors.GoldWash,
            )
        }
    }
    }
}
