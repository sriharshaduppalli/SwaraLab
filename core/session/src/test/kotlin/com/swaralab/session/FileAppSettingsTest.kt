package com.swaralab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.io.path.createTempDirectory

class FileAppSettingsTest {
    @Test
    fun rememberLastOpenedSessionAndPlayhead() {
        val file = createTempDirectory("settings").toFile().resolve("settings.properties")
        val settings = FileAppSettings(file)
        assertNull(settings.sessionId())
        settings.remember("session-9", 1_250L)
        assertEquals("session-9", settings.sessionId())
        assertEquals(1_250L, settings.playheadMs())
        val reloaded = FileAppSettings(file)
        assertEquals("session-9", reloaded.sessionId())
        assertEquals(1_250L, reloaded.playheadMs())
    }
}
