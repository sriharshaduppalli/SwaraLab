package com.swaralab.feature.publish

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Open-source Android share: system ACTION_SEND, no Instagram / YouTube / WhatsApp SDK.
 * If those apps are installed they sit first on the sheet.
 */
object PreferredShare {
    data class Target(
        val packageName: String,
        val label: String,
        val wantsVideo: Boolean,
    )

    val catalog = listOf(
        Target("com.whatsapp", "WhatsApp", wantsVideo = false),
        Target("com.whatsapp.w4b", "WhatsApp", wantsVideo = false),
        Target("com.instagram.android", "Instagram", wantsVideo = true),
        Target("com.google.android.youtube", "YouTube", wantsVideo = true),
    )

    fun installed(context: Context, mime: String): List<Target> {
        val video = mime.startsWith("video/")
        return catalog.filter { target ->
            if (target.wantsVideo && !video) return@filter false
            isInstalled(context, target.packageName)
        }.distinctBy { it.label }
    }

    fun open(
        context: Context,
        uri: Uri,
        mime: String,
        title: String,
        packageName: String? = null,
    ) {
        val send = sendIntent(uri, mime, title)
        if (!packageName.isNullOrBlank()) {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent(send).setPackage(packageName))
            return
        }
        val preferred = installed(context, mime).map { target ->
            context.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Intent(send).setPackage(target.packageName)
        }
        val chooser = Intent.createChooser(send, "Share $title")
        if (preferred.isNotEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, preferred.toTypedArray())
        }
        context.startActivity(chooser)
    }

    private fun sendIntent(uri: Uri, mime: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
