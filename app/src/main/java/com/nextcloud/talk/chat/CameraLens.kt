/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutionException

private const val TAG = "CameraLens"

internal fun cameraSelectorFor(lens: Int): CameraSelector = CameraSelector.Builder().requireLensFacing(lens).build()

internal fun oppositeLens(lens: Int): Int =
    if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT

/**
 * The lens to bind: [preferred] when the device has it, otherwise the other one.
 */
internal fun resolveLens(provider: ProcessCameraProvider, preferred: Int): Int =
    if (provider.hasCamera(cameraSelectorFor(preferred))) preferred else oppositeLens(preferred)

/**
 * Calls [onResult] on the main thread with the camera provider, or with null when it is not available.
 */
internal fun whenCameraProviderReady(context: Context, onResult: (ProcessCameraProvider?) -> Unit) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = try {
            providerFuture.get()
        } catch (e: ExecutionException) {
            Log.w(TAG, "camera provider is not available", e)
            null
        }
        onResult(provider)
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Binds [useCases] to the camera with [lens]. Returns null when the camera cannot be bound.
 */
internal fun ProcessCameraProvider.bindSafely(owner: LifecycleOwner, lens: Int, vararg useCases: UseCase): Camera? =
    try {
        bindToLifecycle(owner, cameraSelectorFor(lens), *useCases)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "cannot bind camera use cases", e)
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "cannot bind camera use cases", e)
        null
    }
