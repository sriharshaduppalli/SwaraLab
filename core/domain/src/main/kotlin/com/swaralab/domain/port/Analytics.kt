package com.swaralab.domain.port

/**
 * On-device product events only. Implementations must not send data off the phone.
 */
interface Analytics {
    fun log(name: String, params: Map<String, String> = emptyMap())
}

object NoOpAnalytics : Analytics {
    override fun log(name: String, params: Map<String, String>) = Unit
}

object AnalyticsEvent {
    const val APP_OPEN = "app_open"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val TRACK_IMPORTED = "track_imported"
    const val KARAOKE_STARTED = "karaoke_started"
    const val KARAOKE_COMPLETED = "karaoke_completed"
    const val RECORDING_SAVED = "recording_saved"
    const val RECORDING_PLAYED = "recording_played"
    const val SEARCH_PERFORMED = "search_performed"
    const val SCREEN_VIEW = "screen_view"
}
