/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRTCVideoView(surfaceViewRenderer: SurfaceViewRenderer) {
    AndroidView(
        factory = { surfaceViewRenderer },
        update = { /* No-op, renderer is already initialized and reused */ },
        modifier = Modifier.fillMaxSize()
    )
}
