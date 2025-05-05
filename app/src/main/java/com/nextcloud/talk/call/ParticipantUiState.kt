/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call

import org.webrtc.SurfaceViewRenderer

data class ParticipantUiState(
    val sessionKey: String,
    val nick: String,
    val isConnected: Boolean,
    val isAudioEnabled: Boolean,
    val isStreamEnabled: Boolean,
    val raisedHand: Boolean,
    val avatarUrl: String?,
    val surfaceViewRenderer: SurfaceViewRenderer? = null
)
