/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import android.hardware.display.DisplayManager
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.File

private val ControlSize = 48.dp
private val ShutterSize = 72.dp
private val ShutterRingWidth = 4.dp
private val ScreenPadding = 16.dp
private val LensSwitchGap = 84.dp
private val ModeButtonSize = 56.dp
private val ModeButtonCorner = 16.dp
private val ModeLabelSize = 11.sp
private const val DISABLED_ALPHA = 0.4f

/**
 * Full screen photo capture: preview, close, shutter mode and flash at the top edge of the device body, shutter and
 * lens switch at its bottom edge.
 * The shutter mode is read from and kept in [appPreferences].
 * [newPhotoFile] makes the target of a photo, [onCaptured] gets the finished file, [onFailed] the target of a
 * photo that could not be written.
 */
@Composable
internal fun PhotoCaptureScreen(
    appPreferences: AppPreferences,
    newPhotoFile: () -> File?,
    onCaptured: (File) -> Unit,
    onFailed: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val camera = remember {
        PhotoCamera(
            context,
            lifecycleOwner,
            previewView,
            CaptureModeSetting.fromStorage(appPreferences.cameraCaptureMode)
        ) { appPreferences.cameraCaptureMode = it.storageValue }
    }

    var deviceOrientation by remember { mutableIntStateOf(0) }

    DisposableEffect(camera) {
        camera.start()
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = rotationForDeviceOrientation(orientation) ?: return
                camera.setTargetRotation(rotation)
                if (rotationForDeviceOrientation(deviceOrientation) != rotation) {
                    deviceOrientation = orientation
                }
            }
        }
        orientationListener.enable()
        onDispose {
            orientationListener.disable()
            camera.release()
        }
    }

    val displayRotation = rememberDisplayRotation()
    val iconAngle = rememberIconAngle(iconRotationDegrees(deviceOrientation, displayRotation) ?: 0)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CaptureControls(camera, displayRotation, iconAngle, onClose) {
            if (camera.isCapturing || !camera.isBound) return@CaptureControls
            val file = newPhotoFile()
            if (file == null) {
                onFailed()
            } else {
                camera.takePicture(file) { success ->
                    if (success) {
                        onCaptured(file)
                    } else {
                        file.delete()
                        onFailed()
                    }
                }
            }
        }
    }
}

/**
 * The window rotation of the display ([android.view.Surface] constant). The activity handles configuration changes
 * itself and a half turn is no configuration change, so the display is observed.
 */
@Composable
private fun rememberDisplayRotation(): Int {
    val view = LocalView.current
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                view.display?.let { rotation = it.rotation }
            }
        }
        manager.registerDisplayListener(listener, null)
        rotation = view.display?.rotation ?: rotation
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return rotation
}

/**
 * The animated icon angle for [targetDegrees]: every change takes the shortest way round.
 */
@Composable
private fun rememberIconAngle(targetDegrees: Int): Float {
    var unbounded by remember { mutableFloatStateOf(targetDegrees.toFloat()) }
    LaunchedEffect(targetDegrees) {
        unbounded = shortestRotationTarget(unbounded, targetDegrees)
    }
    return animateFloatAsState(unbounded, label = "captureIconAngle").value
}

/**
 * Controls tied to the device body, not to the window: the shutter stays at the natural bottom edge, close and
 * flash at the natural top edge with the shutter mode button between them, the lens switch next to the shutter,
 * whatever rotation the window has.
 */
@Composable
private fun CaptureControls(
    camera: PhotoCamera,
    displayRotation: Int,
    iconAngle: Float,
    onClose: () -> Unit,
    onShutter: () -> Unit
) {
    Box(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding().padding(ScreenPadding)) {
        fun BoxScope.at(x: Int, y: Int): Modifier =
            Modifier.align(BodyPoint(x, y).inWindow(displayRotation).toAlignment())

        CaptureIconButton(
            icon = R.drawable.ic_baseline_close_24,
            description = stringResource(R.string.close),
            angle = iconAngle,
            modifier = at(-1, -1),
            onClick = onClose
        )
        val modeEnabled = camera.isBound && !camera.isCapturing
        CaptureModeButton(
            mode = camera.captureMode,
            angle = iconAngle,
            enabled = modeEnabled,
            modifier = at(0, -1),
            onClick = camera::toggleCaptureMode
        )
        if (camera.hasFlashUnit) {
            val (icon, description) = flashPresentation(camera.flash)
            CaptureIconButton(
                icon = icon,
                description = stringResource(description),
                angle = iconAngle,
                modifier = at(1, -1),
                onClick = camera::cycleFlash
            )
        }
        val enabled = camera.isBound && !camera.isCapturing
        val shutterDescription = stringResource(R.string.take_photo)
        Box(
            modifier = at(0, 1)
                .size(ShutterSize)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .border(ShutterRingWidth, Color.White, CircleShape)
                .padding(ShutterRingWidth * 2)
                .clip(CircleShape)
                .background(Color.White)
                .semantics {
                    contentDescription = shutterDescription
                    role = Role.Button
                }
                .clickable(enabled = enabled, onClick = onShutter)
        )
        if (camera.canSwitchLens) {
            LensSwitchButton(camera, displayRotation, iconAngle, at(0, 1))
        }
    }
}

/**
 * Beside the shutter along the edge, and centered on it across the edge. [shutterModifier] places the shutter.
 */
@Composable
private fun LensSwitchButton(camera: PhotoCamera, displayRotation: Int, iconAngle: Float, shutterModifier: Modifier) {
    val beside = BodyPoint(1, 0).inWindow(displayRotation)
    val inward = BodyPoint(0, -1).inWindow(displayRotation)
    val centering = (ShutterSize - ControlSize) / 2
    CaptureIconButton(
        icon = R.drawable.ic_baseline_flip_camera_android_24,
        description = stringResource(R.string.nc_video_recording_switch_camera),
        angle = iconAngle,
        modifier = shutterModifier.offset(
            LensSwitchGap * beside.x + centering * inward.x,
            LensSwitchGap * beside.y + centering * inward.y
        ),
        onClick = camera::switchLens
    )
}

private fun BodyPoint.toAlignment(): Alignment = BiasAlignment(x.toFloat(), y.toFloat())

@Composable
private fun CaptureIconButton(icon: Int, description: String, angle: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(ControlSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.rotate(angle)
        )
    }
}

/**
 * The shutter mode button: icon over a short label, both turn with [angle]. The tile is nearly square, so a quarter
 * turn keeps it inside the controls area.
 */
@Composable
private fun CaptureModeButton(
    mode: CaptureModeSetting,
    angle: Float,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val (icon, label, description) = modePresentation(mode)
    val descriptionText = stringResource(description)
    Column(
        modifier = modifier
            .size(ModeButtonSize)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(ModeButtonCorner))
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = descriptionText }
            .rotate(angle),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = Color.White)
        Text(text = stringResource(label), color = Color.White, fontSize = ModeLabelSize, maxLines = 1)
    }
}

private fun modePresentation(mode: CaptureModeSetting): Triple<Int, Int, Int> =
    when (mode) {
        CaptureModeSetting.FAST -> Triple(
            R.drawable.ic_baseline_speed_24,
            R.string.nc_photo_capture_mode_fast,
            R.string.nc_photo_capture_mode_fast_description
        )
        CaptureModeSetting.QUALITY -> Triple(
            R.drawable.ic_baseline_hd_24,
            R.string.nc_photo_capture_mode_quality,
            R.string.nc_photo_capture_mode_quality_description
        )
    }

private fun flashPresentation(setting: FlashSetting): Pair<Int, Int> =
    when (setting) {
        FlashSetting.OFF -> R.drawable.ic_baseline_flash_off_24 to R.string.nc_photo_capture_flash_off
        FlashSetting.AUTO -> R.drawable.ic_baseline_flash_auto_24 to R.string.nc_photo_capture_flash_auto
        FlashSetting.ON -> R.drawable.ic_baseline_flash_on_24 to R.string.nc_photo_capture_flash_on
    }
