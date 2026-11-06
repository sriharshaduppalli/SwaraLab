package com.swaralab.session

import com.swaralab.domain.port.BoothProfile
import com.swaralab.domain.port.OpenSessionPointer
import java.io.File
import java.util.Properties

class FileAppSettings(private val file: File) : BoothProfile, OpenSessionPointer {
    fun uiLanguage(): String = property("uiLanguage", "en")

    fun themeId(): String = property("themeId", "raga")

    fun setUiLanguage(tag: String) = setProperty("uiLanguage", tag)

    fun setThemeId(tag: String) = setProperty("themeId", tag)

    fun firstOpenSeen(): Boolean = property("firstOpenSeen", "false") == "true"

    fun markFirstOpenSeen() = setProperty("firstOpenSeen", "true")

    fun replayOnboarding() = setProperty("firstOpenSeen", "false")

    override fun displayName(): String = property("singerName", "")

    override fun bio(): String = property("singerBio", "")

    override fun setProfile(name: String, bio: String) {
        setProperty("singerName", name.trim())
        setProperty("singerBio", bio.trim())
    }

    override fun activitySeenAt(): Long = property("boothSeenAt", "0").toLongOrNull() ?: 0L

    override fun markActivitySeen() = setProperty("boothSeenAt", System.currentTimeMillis().toString())

    override fun remember(sessionId: String, playheadMs: Long) = setProperties(
        "lastOpenedSessionId" to sessionId,
        "lastPlayheadMs" to playheadMs.coerceAtLeast(0L).toString(),
        "lastOpenedAt" to System.currentTimeMillis().toString(),
    )

    override fun sessionId(): String? = property("lastOpenedSessionId", "").ifBlank { null }

    override fun playheadMs(): Long = property("lastPlayheadMs", "0").toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    private fun property(key: String, fallback: String): String {
        if (!file.isFile) return fallback
        return runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty(key, fallback)
        }.getOrDefault(fallback)
    }

    private fun setProperty(key: String, value: String) = setProperties(key to value)

    private fun setProperties(vararg pairs: Pair<String, String>) {
        file.parentFile?.mkdirs()
        val props = Properties()
        if (file.isFile) {
            file.inputStream().use { props.load(it) }
        }
        pairs.forEach { (key, value) -> props.setProperty(key, value) }
        file.outputStream().use { props.store(it, "SwaraLab app settings") }
    }
}
