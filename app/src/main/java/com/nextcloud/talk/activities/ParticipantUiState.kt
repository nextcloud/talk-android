/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.activities

import com.nextcloud.talk.models.json.participants.Participant
import org.webrtc.MediaStream

data class ParticipantUiState(
    val sessionKey: String?,
    val nick: String?,
    val isConnected: Boolean,
    val isAudioEnabled: Boolean,
    val isStreamEnabled: Boolean,
    val mediaStream: MediaStream? = null,
    val isScreenStreamEnabled: Boolean,
    val screenMediaStream: MediaStream? = null,
    val raisedHand: Boolean,
    val avatarUrl: String? = null,
    val actorType: Participant.ActorType? = null,
    val actorId: String? = null,
    val userId: String? = null,
    val isInternal: Boolean
)
