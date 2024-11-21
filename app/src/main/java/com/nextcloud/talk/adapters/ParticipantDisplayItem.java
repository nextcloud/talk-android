/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.nextcloud.talk.call.CallParticipantModel;
import com.nextcloud.talk.call.RaisedHand;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

public class ParticipantDisplayItem {

    /**
     * Shared handler to receive change notifications from the model on the main thread.
     */
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private final ParticipantDisplayItemNotifier participantDisplayItemNotifier = new ParticipantDisplayItemNotifier();

    private final Context context;

    private final String baseUrl;
    private final String defaultGuestNick;
    private final EglBase rootEglBase;

    private final String session;
    private final String streamType;

    private final String roomToken;

    private final CallParticipantModel callParticipantModel;

    private Participant.ActorType actorType;
    private String actorId;
    private String userId;
    private PeerConnection.IceConnectionState iceConnectionState;
    private String nick;
    private String urlForAvatar;
    private MediaStream mediaStream;
    private boolean streamEnabled;
    private boolean isAudioEnabled;
    private RaisedHand raisedHand;

    public interface Observer {
        void onChange();
    }

    private final CallParticipantModel.Observer callParticipantModelObserver = new CallParticipantModel.Observer() {
        @Override
        public void onChange() {
            updateFromModel();
        }

        @Override
        public void onReaction(String reaction) {
        }
    };

    public ParticipantDisplayItem(Context context, String baseUrl, String defaultGuestNick, EglBase rootEglBase,
                                  String streamType, String roomToken, CallParticipantModel callParticipantModel) {
        this.context = context;

        this.baseUrl = baseUrl;
        this.defaultGuestNick = defaultGuestNick;
        this.rootEglBase = rootEglBase;

        this.session = callParticipantModel.getSessionId();
        this.streamType = streamType;

        this.roomToken = roomToken;

        this.callParticipantModel = callParticipantModel;
        this.callParticipantModel.addObserver(callParticipantModelObserver, handler);

        updateFromModel();
    }

    public void destroy() {
        this.callParticipantModel.removeObserver(callParticipantModelObserver);
    }

    private void updateFromModel() {
        actorType = callParticipantModel.getActorType();
        actorId = callParticipantModel.getActorId();
        userId = callParticipantModel.getUserId();
        nick = callParticipantModel.getNick();

        this.updateUrlForAvatar();

        if ("screen".equals(streamType)) {
            iceConnectionState = callParticipantModel.getScreenIceConnectionState();
            mediaStream = callParticipantModel.getScreenMediaStream();
            isAudioEnabled = true;
            streamEnabled = true;
        } else {
            iceConnectionState = callParticipantModel.getIceConnectionState();
            mediaStream = callParticipantModel.getMediaStream();
            isAudioEnabled = callParticipantModel.isAudioAvailable() != null ?
                callParticipantModel.isAudioAvailable() : false;
            streamEnabled = callParticipantModel.isVideoAvailable() != null ?
                callParticipantModel.isVideoAvailable() : false;
        }

        raisedHand = callParticipantModel.getRaisedHand();

        participantDisplayItemNotifier.notifyChange();
    }

    private void updateUrlForAvatar() {
        if (actorType == Participant.ActorType.FEDERATED) {
            int darkTheme = DisplayUtils.INSTANCE.isDarkModeOn(context) ? 1 : 0;
            urlForAvatar = ApiUtils.getUrlForFederatedAvatar(baseUrl, roomToken, actorId, darkTheme, true);
        } else if (!TextUtils.isEmpty(userId)) {
            urlForAvatar = ApiUtils.getUrlForAvatar(baseUrl, userId, true);
        } else {
            urlForAvatar = ApiUtils.getUrlForGuestAvatar(baseUrl, getNick(), true);
        }
    }

    public boolean isConnected() {
        return iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
            iceConnectionState == PeerConnection.IceConnectionState.COMPLETED ||
            // If there is no connection state that means that no connection is needed, so it is a special case that is
            // also seen as "connected".
            iceConnectionState == null;
    }

    public String getNick() {
        if (TextUtils.isEmpty(userId) && TextUtils.isEmpty(nick)) {
            return defaultGuestNick;
        }

        return nick;
    }

    public String getUrlForAvatar() {
        return urlForAvatar;
    }

    public MediaStream getMediaStream() {
        return mediaStream;
    }

    public boolean isStreamEnabled() {
        return streamEnabled;
    }

    public EglBase getRootEglBase() {
        return rootEglBase;
    }

    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }

    public RaisedHand getRaisedHand() {
        return raisedHand;
    }

    public Participant.ActorType getActorType() {
        return actorType;
    }

    public void addObserver(Observer observer) {
        participantDisplayItemNotifier.addObserver(observer);
    }

    public void removeObserver(Observer observer) {
        participantDisplayItemNotifier.removeObserver(observer);
    }

    @Override
    public String toString() {
        return "ParticipantSession{" +
                "userId='" + userId + '\'' +
                ", actorType='" + actorType + '\'' +
                ", actorId='" + actorId + '\'' +
                ", session='" + session + '\'' +
                ", nick='" + nick + '\'' +
                ", urlForAvatar='" + urlForAvatar + '\'' +
                ", mediaStream=" + mediaStream +
                ", streamType='" + streamType + '\'' +
                ", streamEnabled=" + streamEnabled +
                ", rootEglBase=" + rootEglBase +
                ", raisedHand=" + raisedHand +
                '}';
    }
}
