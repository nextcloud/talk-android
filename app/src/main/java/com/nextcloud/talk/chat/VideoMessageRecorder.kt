/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Records a plain mp4 video with CameraX and shows a live preview while doing so.
 *
 * All methods must be called on the main thread. [onFinished] is called exactly once per [start] on the main thread:
 * with [Outcome.SEND] and the file to send, or with another outcome and null (the file is deleted then).
 * The recording survives [switchCamera]; the preview and the camera are released as soon as it ends.
 */
class VideoMessageRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFinished: (Outcome, File?) -> Unit
) {

    enum class Outcome { SEND, CANCELLED, TOO_SHORT, FAILED }

    private enum class State { IDLE, STARTING, RECORDING }
    private enum class StopAction { SEND, DISCARD }

    private var state = State.IDLE
    private var stopAction: StopAction? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var outputFile: File? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var session = 0

    val isActive: Boolean
        get() = state != State.IDLE

    fun start(file: File) {
        check(state == State.IDLE) { "Video recording already active" }
        state = State.STARTING
        session++
        val startedSession = session
        stopAction = null
        outputFile = file
        lensFacing = CameraSelector.LENS_FACING_FRONT

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (startedSession != session) return@addListener
            try {
                onCameraProviderReady(providerFuture.get())
            } catch (e: java.util.concurrent.ExecutionException) {
                Log.e(TAG, "camera provider is not available", e)
                finish(Outcome.FAILED, null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopAndSend() {
        stop(StopAction.SEND)
    }

    /**
     * Discards the recording unless it was already stopped to be sent: that one is finished and sent regardless.
     */
    fun cancel() {
        stop(StopAction.DISCARD)
    }

    /**
     * Switches between the front and the back camera. A running recording continues, the picture is frozen
     * until the new camera delivers frames.
     */
    fun switchCamera() {
        val provider = cameraProvider
        if (state == State.IDLE || provider == null) return

        val newLens = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        if (!provider.hasCamera(selectorFor(newLens))) return

        val previousLens = lensFacing
        lensFacing = newLens
        provider.unbind(preview, videoCapture)
        if (!bindUseCases(provider)) {
            lensFacing = previousLens
            bindUseCases(provider)
        }
    }

    private fun stop(action: StopAction) {
        if (stopAction == StopAction.SEND) return
        when (state) {
            State.IDLE -> Unit

            State.STARTING -> {
                session++
                finish(if (action == StopAction.SEND) Outcome.TOO_SHORT else Outcome.CANCELLED, null)
            }

            State.RECORDING -> {
                stopAction = action
                recording?.stop()
                if (action == StopAction.DISCARD) {
                    releaseCamera()
                }
            }
        }
    }

    private fun onCameraProviderReady(provider: ProcessCameraProvider) {
        if (state != State.STARTING) {
            return
        }
        cameraProvider = provider

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))
            )
            .setTargetVideoEncodingBitRate(TARGET_VIDEO_BIT_RATE)
            .build()
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        videoCapture = VideoCapture.Builder(recorder).setTargetRotation(rotation).build()
        preview = Preview.Builder().setTargetRotation(rotation).build()

        if (!bindUseCases(provider)) {
            finish(Outcome.FAILED, null)
            return
        }
        startRecording()
    }

    private fun bindUseCases(provider: ProcessCameraProvider): Boolean {
        var selector = selectorFor(lensFacing)
        if (!provider.hasCamera(selector)) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            selector = selectorFor(lensFacing)
        }
        val previewUseCase = preview
        val videoUseCase = videoCapture
        return try {
            requireNotNull(previewUseCase).surfaceProvider = previewView.surfaceProvider
            provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, requireNotNull(videoUseCase))
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "cannot bind camera use cases", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "cannot bind camera use cases", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    @androidx.annotation.OptIn(ExperimentalPersistentRecording::class)
    private fun startRecording() {
        val file = outputFile ?: return
        val videoUseCase = videoCapture ?: return
        val options = FileOutputOptions.Builder(file)
            .setDurationLimitMillis(MAX_DURATION_MS)
            .build()
        recording = videoUseCase.output
            .prepareRecording(context, options)
            .withAudioEnabled()
            .asPersistentRecording()
            .start(ContextCompat.getMainExecutor(context), ::onRecordEvent)
        state = State.RECORDING
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        if (event is VideoRecordEvent.Finalize) {
            val outcome = resolveOutcome(
                discardRequested = stopAction == StopAction.DISCARD,
                hasError = event.hasError(),
                error = event.error,
                recordedDurationNanos = event.recordingStats.recordedDurationNanos
            )
            if (event.hasError()) {
                Log.w(TAG, "recording finalized with error ${event.error}, outcome: $outcome")
            }
            finish(outcome, if (outcome == Outcome.SEND) outputFile else null)
        }
    }

    private fun finish(outcome: Outcome, fileToSend: File?) {
        val file = outputFile
        releaseCamera()
        recording = null
        outputFile = null
        stopAction = null
        state = State.IDLE
        if (fileToSend == null) {
            file?.delete()
        }
        onFinished(outcome, fileToSend)
    }

    private fun releaseCamera() {
        cameraProvider?.unbind(preview, videoCapture)
    }

    private fun selectorFor(lens: Int): CameraSelector = CameraSelector.Builder().requireLensFacing(lens).build()

    companion object {
        private val TAG = VideoMessageRecorder::class.java.simpleName
        const val MAX_DURATION_MS = 120_000L
        const val TARGET_VIDEO_BIT_RATE = 2_500_000

        const val MIN_DURATION_NANOS = 1_000_000_000L

        /**
         * A recording that ended because of the duration or size limit is complete and valid; any other error leaves
         * a file that must not be sent.
         */
        fun isUsableFinalize(hasError: Boolean, error: Int): Boolean =
            !hasError ||
                error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED

        /**
         * Decides what happens with a finalized recording. "No valid data" and a recording shorter than
         * [MIN_DURATION_NANOS] mean the button was released too early, every other error is a failure.
         */
        fun resolveOutcome(
            discardRequested: Boolean,
            hasError: Boolean,
            error: Int,
            recordedDurationNanos: Long
        ): Outcome =
            when {
                discardRequested -> Outcome.CANCELLED
                hasError && error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> Outcome.TOO_SHORT
                !isUsableFinalize(hasError, error) -> Outcome.FAILED
                recordedDurationNanos < MIN_DURATION_NANOS -> Outcome.TOO_SHORT
                else -> Outcome.SEND
            }
    }
}
