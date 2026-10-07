/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import android.view.Surface

private const val ROTATIONS = 4
private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val FULL_TURN = 360

/**
 * A spot on the device body in the natural orientation: [x] is -1 at the left edge, 0 in the middle and 1 at the
 * right edge, [y] is -1 at the top edge, 0 in the middle and 1 at the bottom edge.
 */
internal data class BodyPoint(val x: Int, val y: Int)

/**
 * Where a [BodyPoint] lies in the window when the display has the given [Surface] rotation. The capture controls are
 * tied to the device body, so the shutter stays under the same finger in every window rotation, whether the system
 * kept the window in the natural orientation or turned it.
 *
 * ROTATION_90 means the device was turned counter-clockwise: the natural bottom edge is on the right of the window.
 */
internal fun BodyPoint.inWindow(displayRotation: Int): BodyPoint =
    when (displayRotation) {
        Surface.ROTATION_90 -> BodyPoint(y, -x)
        Surface.ROTATION_180 -> BodyPoint(-x, -y)
        Surface.ROTATION_270 -> BodyPoint(-y, x)
        else -> this
    }

/**
 * Clockwise angle (0, 90, 180 or 270 degrees) that keeps an icon upright for the user. [deviceOrientation] is the
 * angle of OrientationEventListener, [displayRotation] the [Surface] rotation of the window. Returns null while the
 * device orientation is unknown.
 *
 * The photo rotation is the display rotation the window would need to be upright for the current hold, so the icon
 * turns by the difference between it and the rotation the window really has.
 */
internal fun iconRotationDegrees(deviceOrientation: Int, displayRotation: Int): Int? {
    val upright = rotationForDeviceOrientation(deviceOrientation) ?: return null
    return Math.floorMod(upright - displayRotation, ROTATIONS) * QUARTER_TURN
}

/**
 * The value to animate an icon to, so that it reaches [targetDegrees] by the shortest way. [currentDegrees] is
 * the unbounded angle shown now; a turn from 0 to 270 gives -90, not 270.
 */
internal fun shortestRotationTarget(currentDegrees: Float, targetDegrees: Int): Float {
    val delta = Math.floorMod((targetDegrees - currentDegrees).toInt() + HALF_TURN, FULL_TURN) - HALF_TURN
    return currentDegrees + delta
}
