package com.swaralab.engine.audio

import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.port.ArmRequest
import com.swaralab.domain.port.TakeControl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UnconnectedAudioEngineTest {
    @Test
    fun wiredRouteArmsAndWaitsForTheMicrophonePath() = runBlocking {
        val engine = UnconnectedAudioEngine()
        val decision = engine.arm(ArmRequest(sessionId = "session", output = GearRoute.WIRED))
        assertEquals(MonitorDecision.ALLOW, decision)
        assertFalse(engine.state.value.transportConnected)
        assertEquals(TakeControl.TransportUnavailable, engine.startTake())
    }

    @Test
    fun speakerRouteRefusesATake() = runBlocking {
        val engine = UnconnectedAudioEngine()
        engine.arm(ArmRequest(sessionId = "session", output = GearRoute.SPEAKER))
        assertEquals(TakeControl.Refused(MonitorDecision.BLOCK), engine.startTake())
    }
}
