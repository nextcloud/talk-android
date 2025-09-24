/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

/**
 * Read-only data model for (remote) call participants.
 *
 *
 * If the hand was never raised null is returned by "getRaisedHand()". Otherwise a RaisedHand object is returned with
 * the current state (raised or not) and the timestamp when the raised hand state last changed.
 *
 *
 * The received audio and video are available only if the participant is sending them and also has them enabled.
 * Before a connection is established it is not known whether audio and video are available or not, so null is returned
 * in that case (therefore it should not be autoboxed to a plain boolean without checking that).
 *
 *
 * Audio and video in screen shares, on the other hand, are always seen as available.
 *
 *
 * Actor type and actor id will be set only in Talk >= 20.
 *
 *
 * Clients of the model can observe it with CallParticipantModel.Observer to be notified when any value changes.
 * Getters called after receiving a notification are guaranteed to provide at least the value that triggered the
 * notification, but it may return even a more up to date one (so getting the value again on the following
 * notification may return the same value as before).
 *
 *
 * Besides onChange(), which notifies about changes in the model values, CallParticipantModel.Observer provides
 * additional methods to be notified about one-time events that are not reflected in the model values, like reactions.
 */
import com.nextcloud.talk.models.json.participants.Participant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState

data class ParticipantUiState(
    val sessionKey: String?,
    val nick: String?,
    val isConnected: Boolean,
    val isAudioEnabled: Boolean,
    val isStreamEnabled: Boolean,
    val raisedHand: Boolean,
    val avatarUrl: String? = null,
    val mediaStream: MediaStream? = null,
    val actorType: Participant.ActorType? = null,
    val actorId: String? = null,
    val userId: String? = null,
    val isInternal: Boolean
)

// this.sessionId = sessionId;
//
// this.actorType = new Data<>();
// this.actorId = new Data<>();
// this.userId = new Data<>();
// this.nick = new Data<>();
//
// this.internal = new Data<>();
//
// this.raisedHand = new Data<>();
//
// this.iceConnectionState = new Data<>();
// this.mediaStream = new Data<>();
// this.audioAvailable = new Data<>();
// this.videoAvailable = new Data<>();
//
// this.screenIceConnectionState = new Data<>();
// this.screenMediaStream = new Data<>();

class CallParticipantModel(val sessionId: String?, private val defaultGuestNick: String? = "Guest") {

    private val _uiState = MutableStateFlow(
        ParticipantUiState(
            sessionKey = sessionId,
            nick = defaultGuestNick,
            isConnected = false,
            isAudioEnabled = false,
            isStreamEnabled = false,
            raisedHand = false,
            isInternal = false
        )
    )
    val uiState: StateFlow<ParticipantUiState> = _uiState.asStateFlow()

    /** Updates participant's nickname */
    fun updateNick(nick: String?) {
        _uiState.value = _uiState.value.copy(
            nick = nick ?: defaultGuestNick
        )
    }

    /** Updates audio/video availability and media stream */
    fun updateMedia(
        mediaStream: MediaStream?,
        isAudioEnabled: Boolean,
        isStreamEnabled: Boolean,
        iceState: IceConnectionState? = null
    ) {
        _uiState.value = _uiState.value.copy(
            mediaStream = mediaStream,
            isAudioEnabled = isAudioEnabled,
            isStreamEnabled = isStreamEnabled,
            isConnected = iceState == IceConnectionState.CONNECTED ||
                iceState == IceConnectionState.COMPLETED ||
                iceState == null
        )
    }

    /** Updates raised hand state */
    fun updateRaisedHand(raisedHand: RaisedHand?) {
        _uiState.value = _uiState.value.copy(
            raisedHand = raisedHand?.state == true
        )
    }

    /** Updates avatar URL */
    fun updateAvatarUrl(url: String?) {
        _uiState.value = _uiState.value.copy(
            avatarUrl = url
        )
    }

    /** Convenience method to update multiple fields at once */
    fun update(
        nick: String? = null,
        mediaStream: MediaStream? = null,
        isAudioEnabled: Boolean? = null,
        isStreamEnabled: Boolean? = null,
        iceState: IceConnectionState? = null,
        raisedHand: RaisedHand? = null,
        avatarUrl: String? = null,
        actorType: Participant.ActorType? = null,
        actorId: String? = null,
        userId: String? = null,
        isInternal: Boolean? = null
    ) {
        val old = _uiState.value
        _uiState.value = old.copy(
            nick = nick ?: old.nick,
            mediaStream = mediaStream ?: old.mediaStream,
            isAudioEnabled = isAudioEnabled ?: old.isAudioEnabled,
            isStreamEnabled = isStreamEnabled ?: old.isStreamEnabled,
            isConnected = iceState?.let {
                it == IceConnectionState.CONNECTED || it == IceConnectionState.COMPLETED
            } ?: old.isConnected,
            raisedHand = raisedHand?.state ?: old.raisedHand,
            avatarUrl = avatarUrl ?: old.avatarUrl,
            actorType = actorType ?: old.actorType,
            actorId = actorId ?: old.actorId,
            userId = userId ?: old.userId,
            isInternal = isInternal ?: old.isInternal
        )
    }
}
