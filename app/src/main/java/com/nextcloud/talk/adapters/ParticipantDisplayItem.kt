/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-FileCopyrightText: 2021-2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.ViewGroup
import com.nextcloud.talk.call.CallParticipantModel
import com.nextcloud.talk.call.RaisedHand
import com.nextcloud.talk.models.json.participants.Participant.ActorType
import com.nextcloud.talk.utils.ApiUtils.getUrlForAvatar
import com.nextcloud.talk.utils.ApiUtils.getUrlForFederatedAvatar
import com.nextcloud.talk.utils.ApiUtils.getUrlForGuestAvatar
import com.nextcloud.talk.utils.DisplayUtils.isDarkModeOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.SurfaceViewRenderer

data class ParticipantUiState(
    val sessionKey: String,
    val nick: String,
    val isConnected: Boolean,
    val isAudioEnabled: Boolean,
    val isStreamEnabled: Boolean,
    val raisedHand: Boolean,
    val avatarUrl: String?,
    val mediaStream: MediaStream?
)

@Suppress("LongParameterList")
class ParticipantDisplayItem(
    private val context: Context,
    private val baseUrl: String,
    private val defaultGuestNick: String,
    val rootEglBase: EglBase,
    private val streamType: String,
    private val roomToken: String,
    private val callParticipantModel: CallParticipantModel
) {
    private val participantDisplayItemNotifier = ParticipantDisplayItemNotifier()

    private val _uiStateFlow = MutableStateFlow(buildUiState())
    val uiStateFlow: StateFlow<ParticipantUiState> = _uiStateFlow.asStateFlow()

    private val session: String = callParticipantModel.sessionId

    var actorType: ActorType? = null
        private set
    private var actorId: String? = null
    private var userId: String? = null
    private var iceConnectionState: IceConnectionState? = null
    var nick: String? = null
        get() = (if (TextUtils.isEmpty(userId) && TextUtils.isEmpty(field)) defaultGuestNick else field)

    var urlForAvatar: String? = null
        private set
    var mediaStream: MediaStream? = null
        private set
    var isStreamEnabled: Boolean = false
        private set
    var isAudioEnabled: Boolean = false
        private set
    var raisedHand: RaisedHand? = null
        private set
    var surfaceViewRenderer: SurfaceViewRenderer? = null

    val sessionKey: String
        get() = "$session-$streamType"

    interface Observer {
        fun onChange()
    }

    private val callParticipantModelObserver: CallParticipantModel.Observer = object : CallParticipantModel.Observer {
        override fun onChange() {
            updateFromModel()
        }

        override fun onReaction(reaction: String) {
            // unused
        }
    }

    init {
        callParticipantModel.addObserver(callParticipantModelObserver, handler)

        updateFromModel()
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun destroy() {
        callParticipantModel.removeObserver(callParticipantModelObserver)

        surfaceViewRenderer?.let { renderer ->
            try {
                mediaStream?.videoTracks?.firstOrNull()?.removeSink(renderer)
                renderer.clearImage()
                renderer.release()
                (renderer.parent as? ViewGroup)?.removeView(renderer)
            } catch (e: Exception) {
                Log.w("ParticipantDisplayItem", "Error releasing renderer", e)
            }
        }
        surfaceViewRenderer = null
    }

    private fun updateFromModel() {
        actorType = callParticipantModel.actorType
        actorId = callParticipantModel.actorId
        userId = callParticipantModel.userId
        nick = callParticipantModel.nick

        updateUrlForAvatar()

        if (streamType == "screen") {
            iceConnectionState = callParticipantModel.screenIceConnectionState
            mediaStream = callParticipantModel.screenMediaStream
            isAudioEnabled = true
            isStreamEnabled = true
        } else {
            iceConnectionState = callParticipantModel.iceConnectionState
            mediaStream = callParticipantModel.mediaStream
            isAudioEnabled = callParticipantModel.isAudioAvailable ?: false
            isStreamEnabled = callParticipantModel.isVideoAvailable ?: false
        }

        raisedHand = callParticipantModel.raisedHand

        if (surfaceViewRenderer == null && mediaStream != null) {
            val renderer = SurfaceViewRenderer(context).apply {
                init(rootEglBase.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false)
            }
            surfaceViewRenderer = renderer
            mediaStream?.videoTracks?.firstOrNull()?.addSink(renderer)
        }

        _uiStateFlow.value = buildUiState()
        participantDisplayItemNotifier.notifyChange()
    }

    private fun buildUiState(): ParticipantUiState =
        ParticipantUiState(
            sessionKey = sessionKey,
            nick = nick ?: "Guest",
            isConnected = isConnected,
            isAudioEnabled = isAudioEnabled,
            isStreamEnabled = isStreamEnabled,
            raisedHand = raisedHand?.state == true,
            avatarUrl = urlForAvatar,
            mediaStream = mediaStream
        )

    private fun updateUrlForAvatar() {
        if (actorType == ActorType.FEDERATED) {
            val darkTheme = if (isDarkModeOn(context)) 1 else 0
            urlForAvatar = getUrlForFederatedAvatar(baseUrl, roomToken, actorId!!, darkTheme, true)
        } else if (!TextUtils.isEmpty(userId)) {
            urlForAvatar = getUrlForAvatar(baseUrl, userId, true)
        } else {
            urlForAvatar = getUrlForGuestAvatar(baseUrl, nick, true)
        }
    }

    val isConnected: Boolean
        get() = iceConnectionState == IceConnectionState.CONNECTED ||
            iceConnectionState == IceConnectionState.COMPLETED ||
            // If there is no connection state that means that no connection is needed,
            // so it is a special case that is also seen as "connected".
            iceConnectionState == null

    fun addObserver(observer: Observer?) {
        participantDisplayItemNotifier.addObserver(observer)
    }

    fun removeObserver(observer: Observer?) {
        participantDisplayItemNotifier.removeObserver(observer)
    }

    override fun toString(): String =
        "ParticipantSession{" +
            "userId='" + userId + '\'' +
            ", actorType='" + actorType + '\'' +
            ", actorId='" + actorId + '\'' +
            ", session='" + session + '\'' +
            ", nick='" + nick + '\'' +
            ", urlForAvatar='" + urlForAvatar + '\'' +
            ", mediaStream=" + mediaStream +
            ", streamType='" + streamType + '\'' +
            ", streamEnabled=" + isStreamEnabled +
            ", rootEglBase=" + rootEglBase +
            ", raisedHand=" + raisedHand +
            '}'

    companion object {
        /**
         * Shared handler to receive change notifications from the model on the main thread.
         */
        private val handler = Handler(Looper.getMainLooper())
    }
}
