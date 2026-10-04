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
import android.view.OrientationEventListener
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File

/**
 * Records a plain mp4 video with CameraX and shows a live preview while doing so.
 *
 * The recorder does not belong to an activity: it lives in the view model of the chat and survives the recreation of
 * the activity (rotation). The camera is bound once per recording to a lifecycle of the recorder itself
 * ([RecordingLifecycleOwner]), not to the one of the activity: the activity stops on every rotation and would stop
 * the camera, and binding the use cases again to the next activity makes the CameraX recorder configure itself in a
 * state where that is not allowed. The activity hands only its preview view and its callback in with [attach] and
 * the recorder lets go of both in [detach], which also happens when the activity lifecycle is destroyed, so no
 * activity or view is leaked. While nothing is attached the recording goes on; a result that arrives then is kept
 * and delivered by the next [attach].
 *
 * All methods must be called on the main thread. The callback is called exactly once per [start] on the main thread:
 * with [Outcome.SEND] and the file to send, with [Outcome.INTERRUPTED] and the file that was recorded up to the
 * interruption, or with another outcome and null (the file is deleted then).
 * The recording survives [switchCamera]; the preview and the camera are released as soon as it ends.
 */
class VideoMessageRecorder(context: Context) {

    /**
     * [INTERRUPTED]: the recording ended without being asked to, but what was recorded is usable. It is not sent
     * on its own but shown as a preview.
     */
    enum class Outcome { SEND, CANCELLED, TOO_SHORT, FAILED, INTERRUPTED }

    private enum class State { IDLE, STARTING, RECORDING }

    private class Finished(val outcome: Outcome, val file: File?)

    private val context: Context = context.applicationContext
    private var activityLifecycle: Lifecycle? = null
    private var cameraOwner: RecordingLifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var onFinished: ((Outcome, File?) -> Unit)? = null
    private var pendingResult: Finished? = null
    private var released = false

    private val ownerObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) detach()
    }

    private var state = State.IDLE
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var outputFile: File? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var session = 0
    private var videoRotation = Surface.ROTATION_0
    private val stopCoordinator = RecordingStopCoordinator(MainThreadScheduler()) { recording?.stop() }

    val isActive: Boolean
        get() = state != State.IDLE

    /**
     * A recording ended while no activity was attached, its result waits for the next [attach].
     */
    val hasPendingResult: Boolean
        get() = pendingResult != null

    /**
     * Shows the preview in [view] and reports the end of the recording to [callback]. A running recording keeps its
     * camera, only the preview picture moves to the new view; a result which is waiting is delivered at once. The
     * previously attached view is dropped. [owner] is only watched: its destruction detaches the recorder.
     */
    fun attach(owner: LifecycleOwner, view: PreviewView, callback: (Outcome, File?) -> Unit) {
        if (released) return
        detach()
        activityLifecycle = owner.lifecycle
        previewView = view
        onFinished = callback
        owner.lifecycle.addObserver(ownerObserver)

        preview?.let { previewUseCase ->
            // only the preview is reconfigured by this, the video use case and the recording are not touched
            previewUseCase.targetRotation = displayRotation(view, videoRotation)
            previewUseCase.surfaceProvider = view.surfaceProvider
        }
        pendingResult?.let {
            pendingResult = null
            callback(it.outcome, it.file)
        }
    }

    /**
     * Lets go of the activity. A running recording continues with its camera and without a picture on the screen
     * until the next [attach].
     */
    fun detach() {
        activityLifecycle?.removeObserver(ownerObserver)
        preview?.surfaceProvider = null
        activityLifecycle = null
        previewView = null
        onFinished = null
    }

    /**
     * Ends the recorder for good, when the chat is left: a running recording is discarded, nothing is delivered.
     */
    fun release() {
        released = true
        detach()
        pendingResult?.file?.delete()
        pendingResult = null
        cancel()
        // Inside the window after a camera switch the stop is held back and the camera is not unbound: that would
        // let CameraX set up the new surface while stopping. The finalization (or the end of the window) closes it.
        if (!stopCoordinator.settling) releaseCamera()
    }

    /**
     * @param targetRotation the [Surface] rotation the video is recorded in, see [videoTargetRotation]
     */
    fun start(file: File, targetRotation: Int) {
        check(state == State.IDLE) { "Video recording already active" }
        state = State.STARTING
        session++
        val startedSession = session
        stopCoordinator.reset()
        outputFile = file
        videoRotation = targetRotation
        lensFacing = CameraSelector.LENS_FACING_FRONT

        whenCameraProviderReady(context) { provider ->
            if (startedSession != session) return@whenCameraProviderReady
            if (provider != null) {
                onCameraProviderReady(provider)
            } else {
                finish(Outcome.FAILED, null)
            }
        }
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
     * until the new camera delivers frames. The use cases are bound again, which makes the CameraX recorder set up a
     * new video surface, and that must not meet a stop: see [RecordingStopCoordinator].
     */
    fun switchCamera() {
        val provider = cameraProvider
        val newLens = oppositeLens(lensFacing)
        if (provider == null || state != State.RECORDING || !provider.hasCamera(cameraSelectorFor(newLens))) return
        if (!stopCoordinator.beginSwitch()) return

        val previousLens = lensFacing
        lensFacing = newLens
        provider.unbind(preview, videoCapture)
        if (!bindUseCases(provider)) {
            lensFacing = previousLens
            bindUseCases(provider)
        }
    }

    private fun stop(action: StopAction) {
        when (state) {
            State.IDLE -> Unit

            State.STARTING -> {
                session++
                finish(if (action == StopAction.SEND) Outcome.TOO_SHORT else Outcome.CANCELLED, null)
            }

            State.RECORDING -> stopCoordinator.requestStop(action)
        }
    }

    private fun onCameraProviderReady(provider: ProcessCameraProvider) {
        if (state != State.STARTING) {
            return
        }
        cameraProvider = provider
        val owner = RecordingLifecycleOwner().also { cameraOwner = it }
        owner.start()

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))
            )
            .setTargetVideoEncodingBitRate(TARGET_VIDEO_BIT_RATE)
            .build()
        videoCapture = VideoCapture.Builder(recorder).setTargetRotation(videoRotation).build()
        preview = Preview.Builder()
            .setTargetRotation(previewView?.let { displayRotation(it, videoRotation) } ?: videoRotation)
            .build()
            .also { it.surfaceProvider = previewView?.surfaceProvider }

        if (!bindUseCases(provider)) {
            finish(Outcome.FAILED, null)
            return
        }
        startRecording()
    }

    private fun bindUseCases(provider: ProcessCameraProvider): Boolean {
        lensFacing = resolveLens(provider, lensFacing)
        val owner = cameraOwner
        val previewUseCase = preview
        val videoUseCase = videoCapture
        if (owner == null || previewUseCase == null || videoUseCase == null) return false
        return provider.bindSafely(owner, lensFacing, previewUseCase, videoUseCase) != null
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
            val duration = event.recordingStats.recordedDurationNanos
            var outcome = resolveOutcome(
                discardRequested = stopCoordinator.action == StopAction.DISCARD,
                hasError = event.hasError(),
                error = event.error,
                recordedDurationNanos = duration
            )
            val cutOff = outcome == Outcome.FAILED &&
                isSalvageable(event.error, duration, outputFile?.length() ?: 0L)
            if (cutOff) {
                outcome = Outcome.INTERRUPTED
            }
            if (event.hasError()) {
                Log.w(TAG, "recording finalized with error ${event.error}, outcome: $outcome")
            }
            finish(outcome, if (outcome == Outcome.SEND || outcome == Outcome.INTERRUPTED) outputFile else null)
        }
    }

    private fun finish(outcome: Outcome, fileToSend: File?) {
        val file = outputFile
        releaseCamera()
        recording = null
        outputFile = null
        state = State.IDLE
        if (fileToSend == null || released) {
            file?.delete()
        }
        if (released) return

        val callback = onFinished
        if (callback != null) {
            callback(outcome, fileToSend)
        } else {
            pendingResult = Finished(outcome, fileToSend)
        }
    }

    /**
     * Ends the lifecycle the camera is bound to, which closes the camera, and lets go of the use cases.
     */
    private fun releaseCamera() {
        stopCoordinator.reset()
        preview?.surfaceProvider = null
        cameraProvider?.unbind(preview, videoCapture)
        cameraOwner?.destroy()
        cameraOwner = null
        preview = null
        videoCapture = null
    }

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

        /**
         * A recording which the camera cut off (source inactive) leaves a valid file with everything recorded
         * before that. It is worth keeping when it is long enough and not empty.
         */
        fun isSalvageable(error: Int, recordedDurationNanos: Long, fileLength: Long): Boolean =
            error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE &&
                recordedDurationNanos >= MIN_DURATION_NANOS &&
                fileLength > 0

        /**
         * The rotation to record the video in: the one of the sensor when it is known (the video then matches how
         * the phone is held, whatever the activity shows), otherwise the one of the display.
         *
         * @param sensorDegrees what [OrientationEventListener] reported, or ORIENTATION_UNKNOWN
         */
        fun videoTargetRotation(sensorDegrees: Int, displayRotation: Int): Int =
            rotationForDeviceOrientation(sensorDegrees) ?: displayRotation
    }
}

/**
 * The rotation of the display the view is shown on. It is not read from the view itself: the activity attaches its
 * preview in onCreate, before the view is in a window, and View.getDisplay() is null then.
 */
private fun displayRotation(view: PreviewView, fallback: Int): Int =
    ContextCompat.getDisplayOrDefault(view.context)?.rotation ?: fallback

/**
 * The lifecycle the camera of a recording is bound to. It is not the one of an activity, so that rotating the screen
 * (stop and destroy of the activity) neither stops the camera nor unbinds its use cases. It is started when the
 * recording starts and destroyed when it ends, which closes the camera.
 */
internal class RecordingLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun start() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.STARTED
    }

    fun destroy() {
        // a lifecycle cannot go from INITIALIZED to DESTROYED directly
        if (registry.currentState == Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * What a new activity does about a recording it finds in the view model of the chat.
 */
enum class RecordingResume {
    /** Nothing was recording. */
    NONE,

    /** Attach to the running recording, the lock is already set. */
    ATTACH,

    /** Attach and lock: the finger which held the record button is gone with the old activity. */
    ATTACH_AND_LOCK,

    /** The recording ended while there was no activity: deliver its result. */
    DELIVER_RESULT
}

fun resolveRecordingResume(
    recorderActive: Boolean,
    hasPendingResult: Boolean,
    recordingInProgress: Boolean,
    recordingLocked: Boolean
): RecordingResume =
    when {
        hasPendingResult -> RecordingResume.DELIVER_RESULT
        recorderActive && recordingLocked -> RecordingResume.ATTACH
        recorderActive -> RecordingResume.ATTACH_AND_LOCK
        recordingInProgress && !recordingLocked -> RecordingResume.ATTACH_AND_LOCK
        else -> RecordingResume.NONE
    }

/**
 * Follows the orientation of the device with the sensor, which keeps working while the activity is not rotated.
 */
class DeviceOrientationTracker(context: Context) {
    var degrees: Int = OrientationEventListener.ORIENTATION_UNKNOWN
        private set

    private val listener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            degrees = orientation
        }
    }

    fun enable() {
        if (listener.canDetectOrientation()) listener.enable()
    }

    fun disable() {
        listener.disable()
        degrees = OrientationEventListener.ORIENTATION_UNKNOWN
    }
}

private const val ORIENTATION_QUARTER = 90
private const val ORIENTATION_EIGHTH = 45
private const val FULL_CIRCLE = 360

// Device turned clockwise by 0, 90, 180, 270 degrees: the display rotates the other way.
private val rotationByQuadrant = intArrayOf(
    Surface.ROTATION_0,
    Surface.ROTATION_270,
    Surface.ROTATION_180,
    Surface.ROTATION_90
)

/**
 * Maps the device orientation reported by OrientationEventListener (degrees, clockwise from upright) to the
 * [Surface] rotation, or null when the orientation is unknown.
 */
internal fun rotationForDeviceOrientation(degrees: Int): Int? {
    if (degrees < 0) return null
    return rotationByQuadrant[((degrees + ORIENTATION_EIGHTH) % FULL_CIRCLE) / ORIENTATION_QUARTER]
}
