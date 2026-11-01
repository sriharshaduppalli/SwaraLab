package com.swaralab.domain

import com.swaralab.domain.model.GearRoute
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.SafeFrames
import com.swaralab.domain.policy.MonitorDecision
import com.swaralab.domain.policy.MonitorPolicy
import com.swaralab.domain.policy.MonitorPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPolicyTest {
    @Test
    fun recordingRoutes() {
        assertEquals(MonitorDecision.ALLOW, MonitorPolicy.evaluate(GearRoute.WIRED, MonitorPurpose.RECORD))
        assertEquals(MonitorDecision.ALLOW, MonitorPolicy.evaluate(GearRoute.USB, MonitorPurpose.RECORD))
        assertEquals(MonitorDecision.WARN, MonitorPolicy.evaluate(GearRoute.BLUETOOTH, MonitorPurpose.RECORD))
        assertEquals(MonitorDecision.BLOCK, MonitorPolicy.evaluate(GearRoute.SPEAKER, MonitorPurpose.RECORD))
        assertEquals(MonitorDecision.BLOCK, MonitorPolicy.evaluate(GearRoute.UNKNOWN, MonitorPurpose.RECORD))
        assertEquals(MonitorDecision.ALLOW, MonitorPolicy.evaluate(GearRoute.SPEAKER, MonitorPurpose.REVIEW))
    }

    @Test
    fun safeFramesFitAVerticalMaster() {
        PublishTarget.entries.forEach { target ->
            assertTrue(target.name, SafeFrames.fits(SafeFrames.forTarget(target)))
        }
    }
}
