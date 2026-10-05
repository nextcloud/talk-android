/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Krainov Gleb <krajnov.g@kontentplus.ru>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Size and position of the video recording preview inside the area of the chat. All values are in the same unit
 * (pixels); [left] and [top] are measured from the top start corner of the area.
 */
data class VideoPreviewPlacement(val width: Int, val height: Int, val left: Int, val top: Int)

/**
 * Bounds of the preview inside the area.
 *
 * @param margin the gap kept free at every edge of the area (shrinks on a very small area)
 * @param maxWidthFraction the part of the area width the preview may take at most
 * @param maxSide the longest side the preview may have, so that it does not grow without limit on a tablet
 */
data class VideoPreviewLimits(val margin: Int, val maxWidthFraction: Float, val maxSide: Int)

/**
 * Fits a frame of the given aspect ratio into the area, centered, inside the [limits]. A portrait frame is limited
 * by the width of the area and by its height, a landscape frame is fitted by the height when the area is short:
 * the frame is never distorted, only scaled down.
 *
 * @param aspect width divided by height of the recorded frame as the viewer sees it (9/16 portrait, 16/9 landscape)
 */
fun videoPreviewPlacement(
    areaWidth: Int,
    areaHeight: Int,
    aspect: Float,
    limits: VideoPreviewLimits
): VideoPreviewPlacement {
    val safeAspect = if (aspect.isFinite() && aspect > 0f) aspect else DEFAULT_FRAME_ASPECT
    val width = max(areaWidth, 0)
    val height = max(areaHeight, 0)
    // a margin may not eat the area: at most an eighth of the shorter side on each edge
    val margin = max(min(limits.margin, min(width, height) / MARGIN_DIVISOR), 0)
    val boxWidth = min((width - 2 * margin).toFloat(), width * limits.maxWidthFraction)
    val boxHeight = (height - 2 * margin).toFloat()

    var w = max(boxWidth, 0f)
    var h = w / safeAspect
    if (h > boxHeight) {
        h = max(boxHeight, 0f)
        w = h * safeAspect
    }
    val longest = max(w, h)
    if (longest > limits.maxSide && longest > 0f) {
        val scale = limits.maxSide / longest
        w *= scale
        h *= scale
    }

    val finalWidth = w.roundToInt()
    val finalHeight = h.roundToInt()
    return VideoPreviewPlacement(
        width = finalWidth,
        height = finalHeight,
        left = (width - finalWidth) / 2,
        top = (height - finalHeight) / 2
    )
}

/**
 * Aspect ratio (width / height) of the frame the viewer sees, from the size of the camera surface and the rotation
 * needed to show it upright. A quarter turn swaps the sides. Null when the size is not known (yet).
 */
fun videoFrameAspect(surfaceWidth: Int, surfaceHeight: Int, rotationDegrees: Int): Float? =
    if (surfaceWidth <= 0 || surfaceHeight <= 0) {
        null
    } else if (rotationDegrees % HALF_TURN == 0) {
        surfaceWidth.toFloat() / surfaceHeight
    } else {
        surfaceHeight.toFloat() / surfaceWidth
    }

/**
 * Aspect ratio of a recording before the camera has told its resolution: the recording is 16:9, upright in the
 * rotation the video is recorded in ([android.view.Surface] ROTATION_0 and ROTATION_180 are portrait).
 */
fun fallbackVideoFrameAspect(surfaceRotation: Int): Float =
    if (surfaceRotation == android.view.Surface.ROTATION_0 || surfaceRotation == android.view.Surface.ROTATION_180) {
        PORTRAIT_ASPECT
    } else {
        LANDSCAPE_ASPECT
    }

private const val MARGIN_DIVISOR = 8
private const val HALF_TURN = 180
private const val DEFAULT_FRAME_ASPECT = 9f / 16f
private const val PORTRAIT_ASPECT = 9f / 16f
private const val LANDSCAPE_ASPECT = 16f / 9f
