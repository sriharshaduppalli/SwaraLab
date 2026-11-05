package com.swaralab.session

import com.swaralab.domain.port.Analytics
import java.io.File

/** Appends events to a local file. Nothing is uploaded. */
class FileAnalytics(private val file: File, private val maxLines: Int = 200) : Analytics {
    private val lock = Any()

    override fun log(name: String, params: Map<String, String>) {
        if (name.isBlank()) return
        val safeName = name.replace(Regex("[^a-z0-9_]"), "_")
        val line = buildString {
            append(System.currentTimeMillis())
            append('\t')
            append(safeName)
            params.forEach { (key, value) ->
                append('\t')
                append(key.replace('\t', ' ').take(40))
                append('=')
                append(value.replace('\t', ' ').replace('\n', ' ').take(80))
            }
        }
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
            trim()
        }
    }

    internal fun readLines(): List<String> = if (file.isFile) file.readLines() else emptyList()

    private fun trim() {
        val lines = file.readLines()
        if (lines.size <= maxLines) return
        file.writeText(lines.takeLast(maxLines).joinToString("\n", postfix = "\n"))
    }
}
