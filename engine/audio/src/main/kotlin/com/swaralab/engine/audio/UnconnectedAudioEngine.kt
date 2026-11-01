package com.swaralab.engine.audio

import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.policy.MonitorPolicy
import com.swaralab.domain.policy.MonitorPurpose
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.port.AudioEngineState
import com.swaralab.domain.port.EngineStatus
import com.swaralab.domain.port.FinishedTake
import com.swaralab.domain.port.TakeControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Policy-only transport. [OboeAudioEngine] replaces this class behind [AudioEngine].
 * The callback thread will stay lock-free and write PCM to a WAV on a second thread.
 * UI and use cases keep talking to [AudioEngine] only.
 */
class UnconnectedAudioEngine : AudioEngine {
    private val states = MutableStateFlow(AudioEngineState())
    override val state: StateFlow<AudioEngineState> = states.asStateFlow()

    override suspend fun arm(request: ArmRequest): MonitorDecision {
        val decision = MonitorPolicy.evaluate(request.output, MonitorPurpose.RECORD)
        states.value = AudioEngineState(
            status = if (decision == MonitorDecision.BLOCK) EngineStatus.BLOCKED else EngineStatus.ARMED,
            sessionId = request.sessionId,
            output = request.output,
            decision = decision,
            transportConnected = false,
        )
        return decision
    }

    override suspend fun startTake(): TakeControl {
        val decision = states.value.decision ?: MonitorDecision.BLOCK
        if (decision == MonitorDecision.BLOCK) return TakeControl.Refused(decision)
        return TakeControl.TransportUnavailable
    }

    override suspend fun stopTake(): FinishedTake? = null

    override fun disarm() {
        states.value = AudioEngineState()
    }
}
