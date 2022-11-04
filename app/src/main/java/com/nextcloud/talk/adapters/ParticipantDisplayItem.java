package com.nextcloud.talk.adapters;

import android.text.TextUtils;

import com.nextcloud.talk.utils.ApiUtils;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;

public class ParticipantDisplayItem {
    private String baseUrl;
    private String userId;
    private String session;
    private boolean connected;
    private String nick;
    private String urlForAvatar;
    private MediaStream mediaStream;
    private String streamType;
    private boolean streamEnabled;
    private EglBase rootEglBase;
    private boolean isAudioEnabled;

    public ParticipantDisplayItem(String baseUrl, String userId, String session, boolean connected, String nick, MediaStream mediaStream, String streamType, boolean streamEnabled, EglBase rootEglBase) {
        this.baseUrl = baseUrl;
        this.userId = userId;
        this.session = session;
        this.connected = connected;
        this.nick = nick;
        this.mediaStream = mediaStream;
        this.streamType = streamType;
        this.streamEnabled = streamEnabled;
        this.rootEglBase = rootEglBase;

        this.updateUrlForAvatar();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;

        this.updateUrlForAvatar();
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;

        this.updateUrlForAvatar();
    }

    public String getUrlForAvatar() {
        return urlForAvatar;
    }

    private void updateUrlForAvatar() {
        if (!TextUtils.isEmpty(userId)) {
            urlForAvatar = ApiUtils.getUrlForAvatar(baseUrl, userId, true);
        } else {
            urlForAvatar = ApiUtils.getUrlForGuestAvatar(baseUrl, nick, true);
        }
    }

    public MediaStream getMediaStream() {
        return mediaStream;
    }

    public void setMediaStream(MediaStream mediaStream) {
        this.mediaStream = mediaStream;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(String streamType) {
        this.streamType = streamType;
    }

    public boolean isStreamEnabled() {
        return streamEnabled;
    }

    public void setStreamEnabled(boolean streamEnabled) {
        this.streamEnabled = streamEnabled;
    }

    public EglBase getRootEglBase() {
        return rootEglBase;
    }

    public void setRootEglBase(EglBase rootEglBase) {
        this.rootEglBase = rootEglBase;
    }

    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }

    public void setAudioEnabled(boolean audioEnabled) {
        isAudioEnabled = audioEnabled;
    }

    @Override
    public String toString() {
        return "ParticipantSession{" +
                "userId='" + userId + '\'' +
                ", session='" + session + '\'' +
                ", nick='" + nick + '\'' +
                ", urlForAvatar='" + urlForAvatar + '\'' +
                ", mediaStream=" + mediaStream +
                ", streamType='" + streamType + '\'' +
                ", streamEnabled=" + streamEnabled +
                ", rootEglBase=" + rootEglBase +
                '}';
    }
}


