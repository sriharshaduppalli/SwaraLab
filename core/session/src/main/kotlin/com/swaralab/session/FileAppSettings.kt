package com.swaralab.session

import java.io.File
import java.util.Properties

class FileAppSettings(private val file: File) {
    fun uiLanguage(): String = property("uiLanguage", "en")

    fun themeId(): String = property("themeId", "raga")

    fun setUiLanguage(tag: String) = setProperty("uiLanguage", tag)

    fun setThemeId(tag: String) = setProperty("themeId", tag)

    private fun property(key: String, fallback: String): String {
        if (!file.isFile) return fallback
        return runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty(key, fallback)
        }.getOrDefault(fallback)
    }

    private fun setProperty(key: String, value: String) {
        file.parentFile?.mkdirs()
        val props = Properties()
        if (file.isFile) {
            file.inputStream().use { props.load(it) }
        }
        props.setProperty(key, value)
        file.outputStream().use { props.store(it, "SwaraLab app settings") }
    }
}
