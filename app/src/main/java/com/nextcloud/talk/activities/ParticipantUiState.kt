/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.activities

import com.nextcloud.talk.models.json.participants.ParticipantDto
import org.webrtc.MediaStream

data class ParticipantUiState(
    val sessionKey: String?,
    val baseUrl: String,
    val roomToken: String,
    val nick: String?,
    val isConnected: Boolean,
    val isAudioEnabled: Boolean,
    /** Speaking state as last reported by the participant. */
    val isSpeaking: Boolean = false,
    val isStreamEnabled: Boolean,
    val mediaStream: MediaStream? = null,
    val isScreenStreamEnabled: Boolean,
    val screenMediaStream: MediaStream? = null,
    val raisedHand: Boolean,
    val actorType: ParticipantDto.ActorType? = null,
    val actorId: String? = null,
    val isInternal: Boolean
) {
    /** Whether the participant is speaking and can be heard, so the speaking indicator is shown. */
    val isActiveSpeaker: Boolean
        get() = isSpeaking && isAudioEnabled && isConnected
}
