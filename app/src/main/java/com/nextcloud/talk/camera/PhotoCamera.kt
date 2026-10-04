/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nextcloud.talk.chat.bindSafely
import com.nextcloud.talk.chat.cameraSelectorFor
import com.nextcloud.talk.chat.oppositeLens
import com.nextcloud.talk.chat.resolveLens
import com.nextcloud.talk.chat.whenCameraProviderReady
import java.io.File

/**
 * Still-photo camera of the capture screen: a live preview plus [ImageCapture] on the front or the back lens.
 *
 * All methods must be called on the main thread. The observable properties drive the capture screen.
 */
internal class PhotoCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    initialCaptureMode: CaptureModeSetting = CaptureModeSetting.DEFAULT,
    private val onCaptureModeChanged: (CaptureModeSetting) -> Unit = {}
) {
    var lensFacing by mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
        private set
    var flash by mutableStateOf(FlashSetting.OFF)
        private set
    var captureMode by mutableStateOf(initialCaptureMode)
        private set
    var hasFlashUnit by mutableStateOf(false)
        private set
    var canSwitchLens by mutableStateOf(false)
        private set
    var isBound by mutableStateOf(false)
        private set
    var isCapturing by mutableStateOf(false)
        private set

    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var released = false

    fun start() {
        whenCameraProviderReady(context) { readyProvider ->
            if (released || readyProvider == null) return@whenCameraProviderReady
            provider = readyProvider
            // No target rotation: the preview follows the display, the photo follows setTargetRotation().
            preview = Preview.Builder().build()
            imageCapture = buildImageCapture(captureMode)
            canSwitchLens = readyProvider.hasCamera(cameraSelectorFor(CameraSelector.LENS_FACING_BACK)) &&
                readyProvider.hasCamera(cameraSelectorFor(CameraSelector.LENS_FACING_FRONT))
            bind(resolveLens(readyProvider, lensFacing))
        }
    }

    fun switchLens() {
        if (provider == null || !canSwitchLens || isCapturing) return
        val previousLens = lensFacing
        unbind()
        if (!bind(oppositeLens(previousLens))) {
            bind(previousLens)
        }
    }

    /**
     * Switches between the fast and the maximum quality shutter. The mode is fixed when [ImageCapture] is built, so
     * the use case is replaced and bound again. Ignored while a photo is taken: unbinding would cancel it.
     */
    fun toggleCaptureMode() {
        if (provider == null || isCapturing) return
        val previousMode = captureMode
        val lens = lensFacing
        if (!rebindWith(previousMode.toggled(), lens)) {
            rebindWith(previousMode, lens)
            return
        }
        onCaptureModeChanged(captureMode)
    }

    fun cycleFlash() {
        if (!hasFlashUnit) return
        flash = flash.next()
        imageCapture?.flashMode = effectiveFlashMode(flash, hasFlashUnit)
    }

    /**
     * Sets the rotation of the next photo, so the picture is upright however the phone is held.
     */
    fun setTargetRotation(rotation: Int) {
        imageCapture?.targetRotation = rotation
    }

    /**
     * Writes a JPEG with EXIF orientation to [file]. [onResult] gets true when the file is complete.
     */
    fun takePicture(file: File, onResult: (Boolean) -> Unit) {
        val capture = imageCapture
        if (capture == null || !isBound || isCapturing) {
            onResult(false)
            return
        }
        isCapturing = true
        val mode = captureMode
        val startedAt = SystemClock.elapsedRealtime()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "capture mode=${mode.logName} took=${SystemClock.elapsedRealtime() - startedAt} ms")
                    isCapturing = false
                    onResult(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(
                        TAG,
                        "capture mode=${mode.logName} failed after ${SystemClock.elapsedRealtime() - startedAt} ms",
                        exception
                    )
                    isCapturing = false
                    onResult(false)
                }
            }
        )
    }

    fun release() {
        released = true
        unbind()
    }

    private fun buildImageCapture(mode: CaptureModeSetting): ImageCapture =
        ImageCapture.Builder()
            .setCaptureMode(mode.imageCaptureMode)
            .setResolutionSelector(photoResolutionSelector())
            .build()

    private fun rebindWith(mode: CaptureModeSetting, lens: Int): Boolean {
        // A new use case starts with the display rotation, the old one knows the rotation of the hold.
        val rotation = imageCapture?.targetRotation
        unbind()
        imageCapture = buildImageCapture(mode).also { capture -> rotation?.let { capture.targetRotation = it } }
        captureMode = mode
        return bind(lens)
    }

    private fun bind(lens: Int): Boolean {
        val previewUseCase = preview
        val captureUseCase = imageCapture
        val camera = if (previewUseCase != null && captureUseCase != null) {
            previewUseCase.surfaceProvider = previewView.surfaceProvider
            provider?.bindSafely(lifecycleOwner, lens, previewUseCase, captureUseCase)
        } else {
            null
        }
        isBound = camera != null
        if (camera != null) {
            onBound(camera, lens)
        }
        return isBound
    }

    private fun onBound(camera: Camera, lens: Int) {
        lensFacing = lens
        hasFlashUnit = camera.cameraInfo.hasFlashUnit()
        imageCapture?.flashMode = effectiveFlashMode(flash, hasFlashUnit)
        logCaptureResolution()
    }

    private fun logCaptureResolution() {
        val info = imageCapture?.resolutionInfo ?: return
        val size = info.resolution
        Log.d(
            TAG,
            "photo resolution ${size.width}x${size.height}, " +
                "displayed ${describeCaptureResolution(size.width, size.height, info.rotationDegrees)}"
        )
    }

    private fun unbind() {
        preview?.let { provider?.unbind(it) }
        imageCapture?.let { provider?.unbind(it) }
        isBound = false
    }

    private companion object {
        private val TAG = PhotoCamera::class.java.simpleName
    }
}
