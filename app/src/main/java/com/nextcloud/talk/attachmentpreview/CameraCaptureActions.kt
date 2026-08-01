/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.nextcloud.talk.utils.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CameraCaptureType { PHOTO, VIDEO }

private const val CAMERA_FILE_DATE_PATTERN = "yyyy-MM-dd HH-mm-ss"

internal data class CameraCaptureActions(val onTakePhoto: () -> Unit, val onTakeVideo: () -> Unit)

@Composable
internal fun rememberCameraCaptureActions(currentFiles: MutableList<String>): CameraCaptureActions {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraPermissionType by remember { mutableStateOf<CameraCaptureType?>(null) }

    val onCaptureResult: (Boolean) -> Unit = { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            currentFiles.add(uri.toString())
        }
        pendingCameraUri = null
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCaptureResult)
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo(), onCaptureResult)

    fun launchCameraCapture(type: CameraCaptureType) {
        val uri = createCameraOutputUri(context, type)
        pendingCameraUri = uri
        when (type) {
            CameraCaptureType.PHOTO -> takePicture.launch(uri)
            CameraCaptureType.VIDEO -> captureVideo.launch(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val type = pendingCameraPermissionType
        pendingCameraPermissionType = null
        if (granted && type != null) {
            launchCameraCapture(type)
        }
    }

    fun requestCameraCapture(type: CameraCaptureType) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraCapture(type)
        } else {
            pendingCameraPermissionType = type
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return CameraCaptureActions(
        onTakePhoto = { requestCameraCapture(CameraCaptureType.PHOTO) },
        onTakeVideo = { requestCameraCapture(CameraCaptureType.VIDEO) }
    )
}

private fun createCameraOutputUri(context: Context, type: CameraCaptureType): Uri {
    val outputDir = FileUtils.getSharedAttachmentsDirectory(context.cacheDir) ?: context.cacheDir
    val timestamp = SimpleDateFormat(CAMERA_FILE_DATE_PATTERN, Locale.ROOT).format(Date())
    val extension = if (type == CameraCaptureType.PHOTO) "jpg" else "mp4"
    val file = File(outputDir, "$timestamp.$extension")
    return FileProvider.getUriForFile(context, context.packageName, file)
}
