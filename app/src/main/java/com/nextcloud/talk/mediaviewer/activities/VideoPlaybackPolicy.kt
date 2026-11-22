/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

internal object VideoPlaybackPolicy {
    private const val DEFAULT_RATIO = 16f / 9f
    private const val MAX_RATIO = 2.38f
    private const val MIN_RATIO = 0.42f

    fun pictureInPictureRatio(width: Int, height: Int, pixelRatio: Float): Float {
        if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0) return DEFAULT_RATIO
        return (width.toFloat() * pixelRatio / height).coerceIn(MIN_RATIO, MAX_RATIO)
    }
}
