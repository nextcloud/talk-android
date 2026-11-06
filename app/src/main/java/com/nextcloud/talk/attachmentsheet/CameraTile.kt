/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.bindSafely
import com.nextcloud.talk.chat.resolveLens
import com.nextcloud.talk.chat.whenCameraProviderReady

/**
 * Holds the bound preview so it can be released before the system camera starts.
 */
private class PreviewBinding {
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    fun bind(provider: ProcessCameraProvider, preview: Preview?) {
        this.provider = provider
        this.preview = preview
    }

    fun release() {
        preview?.let { provider?.unbind(it) }
        preview = null
    }
}

/**
 * First grid tile. With [livePreview] and the camera permission it shows the back camera; otherwise only the camera
 * icon (no camera is opened, e.g. during a call). The tap goes through the regular photo flow, which asks for the
 * camera permission; the preview is released first so the system camera can open it.
 */
@Composable
internal fun CameraTile(livePreview: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val binding = remember { PreviewBinding() }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.Black)
            .clickable {
                binding.release()
                onClick()
            }
    ) {
        if (livePreview && hasCameraPermission) {
            LiveCameraPreview(binding, Modifier.fillMaxSize())
        }
        Icon(
            painter = painterResource(R.drawable.ic_baseline_photo_camera_24),
            contentDescription = stringResource(R.string.nc_upload_picture_from_cam),
            modifier = Modifier.align(Alignment.Center).padding(8.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun LiveCameraPreview(binding: PreviewBinding, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        var disposed = false
        whenCameraProviderReady(context) { provider ->
            if (!disposed && provider != null) {
                binding.bind(provider, bindBackPreview(provider, lifecycleOwner, previewView))
            }
        }
        onDispose {
            disposed = true
            binding.release()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun bindBackPreview(provider: ProcessCameraProvider, owner: LifecycleOwner, view: PreviewView): Preview? {
    val lens = resolveLens(provider, CameraSelector.LENS_FACING_BACK)
    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
    return preview.takeIf { provider.bindSafely(owner, lens, it) != null }
}
