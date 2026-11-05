package com.swaralab.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

object MediaPermissions {
    fun audioReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasRecordAudio(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAudioRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioReadPermission()) ==
            PackageManager.PERMISSION_GRANTED
}

@Composable
fun PermissionRationale(
    title: String,
    message: String,
    grantLabel: String,
    denyLabel: String,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorState(
        title = title,
        message = message,
        retryLabel = grantLabel,
        onRetry = onGrant,
        secondaryLabel = denyLabel,
        onSecondary = onDeny,
        fillMax = true,
        modifier = modifier,
    )
}
