package com.swaralab.domain.policy

import com.swaralab.domain.model.GearRoute

enum class MonitorPurpose {
    RECORD,
    REVIEW,
}

enum class MonitorDecision {
    ALLOW,
    WARN,
    BLOCK,
}

object MonitorPolicy {
    fun evaluate(output: GearRoute, purpose: MonitorPurpose): MonitorDecision {
        if (purpose == MonitorPurpose.REVIEW) return MonitorDecision.ALLOW
        return when (output) {
            GearRoute.WIRED, GearRoute.USB -> MonitorDecision.ALLOW
            GearRoute.BLUETOOTH -> MonitorDecision.WARN
            GearRoute.SPEAKER, GearRoute.BUILTIN_MIC, GearRoute.UNKNOWN -> MonitorDecision.BLOCK
        }
    }
}
