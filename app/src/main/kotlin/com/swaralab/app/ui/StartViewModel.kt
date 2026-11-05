package com.swaralab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.SingMode
import com.swaralab.domain.usecase.StartSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class StartedSession(
    val id: String,
    val sing: String = "",
)

class StartViewModel(
    private val startSession: StartSession,
) : ViewModel() {
    private val openedIds = MutableSharedFlow<StartedSession>(extraBufferCapacity = 1)
    val opened: SharedFlow<StartedSession> = openedIds.asSharedFlow()
    private var busy = false

    fun start(kind: SessionKind, mode: SingMode? = null) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                openedIds.emit(StartedSession(startSession(kind), mode?.name.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    companion object {
        fun factory(startSession: StartSession): ViewModelProvider.Factory = viewModelFactory {
            initializer { StartViewModel(startSession) }
        }
    }
}
