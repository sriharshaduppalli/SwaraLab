package com.swaralab.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** System ACTION_SEND for a take on this phone. No live social APIs. */
object LocalTakeShare {
    fun open(context: Context, file: File?, title: String) {
        val playFile = file?.takeIf { it.isFile } ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", playFile)
        }.getOrNull() ?: return
        val mime = when (playFile.extension.lowercase()) {
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            else -> "audio/*"
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_TEXT, "A take from this phone.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title.ifBlank { "Share take" }))
    }

    fun latestTakeFile(sessionRoot: File): File? {
        val takes = File(sessionRoot, "media/takes")
        if (!takes.isDirectory) return null
        val files = takes.listFiles().orEmpty().filter { file ->
            file.isFile &&
                file.extension.lowercase() in setOf("wav", "m4a", "aac") &&
                !file.name.equals("draft.wav", ignoreCase = true)
        }
        return files.filter { it.extension.lowercase() in setOf("m4a", "aac") }.maxByOrNull { it.lastModified() }
            ?: files.maxByOrNull { it.lastModified() }
    }
}
