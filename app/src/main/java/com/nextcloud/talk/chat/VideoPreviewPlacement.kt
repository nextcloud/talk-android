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
 * Size of the video recording preview inside the area of the chat, in the unit of the area (pixels). The preview is
 * centered by its parent (gravity), so no offsets are given: they would have to be resolved for right-to-left.
 */
data class VideoPreviewPlacement(val width: Int, val height: Int)

/**
 * Bounds of the preview inside the area.
 *
 * @param margin the gap kept free at every edge of the area (shrinks on a very small area)
 * @param maxWidthFraction the part of the area width the preview may take at most
 * @param maxSide the longest side the preview may have, so that it does not grow without limit on a tablet
 * @param shortAreaHeight an area lower than this keeps [shortMargin] instead of [margin]: on a short landscape area two
 * full margins would take a noticeable part of the height. Zero turns this off.
 */
data class VideoPreviewLimits(
    val margin: Int,
    val maxWidthFraction: Float,
    val maxSide: Int,
    val shortAreaHeight: Int = 0,
    val shortMargin: Int = margin
)

/**
 * Fits a frame of the given aspect ratio into the area inside the [limits]. A portrait frame is limited
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
    val wanted = if (height < limits.shortAreaHeight) limits.shortMargin else limits.margin
    val margin = max(min(wanted, min(width, height) / MARGIN_DIVISOR), 0)
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

    return VideoPreviewPlacement(width = w.roundToInt(), height = h.roundToInt())
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
 * The aspect ratio of the recorded frame in the coordinates of the screen. The sensor image always runs along the
 * same side of the phone; the rotation the video is recorded in only sets the flag in the file. The preview is
 * drawn upright for the display, so when the recording rotation and the display rotation differ by a quarter turn
 * (auto-rotate off with the phone held sideways, or the phone turned during the recording) the frame on the screen
 * is the other way round than the recorded one.
 *
 * @param frameAspect width / height of the frame as the recording shows it
 * @param videoRotation [android.view.Surface] rotation the video is recorded in
 * @param displayRotation [android.view.Surface] rotation of the display
 */
fun screenFrameAspect(frameAspect: Float, videoRotation: Int, displayRotation: Int): Float =
    if ((videoRotation - displayRotation + QUARTER_TURNS) % 2 == 1) 1f / frameAspect else frameAspect

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

private const val QUARTER_TURNS = 4
private const val MARGIN_DIVISOR = 8
private const val HALF_TURN = 180
private const val DEFAULT_FRAME_ASPECT = 9f / 16f
private const val PORTRAIT_ASPECT = 9f / 16f
private const val LANDSCAPE_ASPECT = 16f / 9f
