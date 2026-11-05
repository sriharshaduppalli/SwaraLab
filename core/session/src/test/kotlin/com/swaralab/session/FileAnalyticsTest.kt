package com.swaralab.session

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileAnalyticsTest {
    @Test
    fun writesEventsOnThisPhoneOnly() {
        val file = Files.createTempFile("swaralab-analytics", ".log").toFile()
        file.deleteOnExit()
        val analytics = FileAnalytics(file)
        analytics.log("track_imported", mapOf("duration_ms" to "12000"))
        val line = analytics.readLines().single()
        assertTrue(line.contains("track_imported"))
        assertTrue(line.contains("duration_ms=12000"))
        assertTrue(!line.contains("http"))
    }
}
