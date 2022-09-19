package com.nextcloud.talk.adapters;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;

public class ParticipantDisplayItem {
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

    public ParticipantDisplayItem(String userId, String session, boolean connected, String nick, String urlForAvatar, MediaStream mediaStream, String streamType, boolean streamEnabled, EglBase rootEglBase) {
        this.userId = userId;
        this.session = session;
        this.connected = connected;
        this.nick = nick;
        this.urlForAvatar = urlForAvatar;
        this.mediaStream = mediaStream;
        this.streamType = streamType;
        this.streamEnabled = streamEnabled;
        this.rootEglBase = rootEglBase;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
    }

    public String getUrlForAvatar() {
        return urlForAvatar;
    }

    public void setUrlForAvatar(String urlForAvatar) {
        this.urlForAvatar = urlForAvatar;
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


