package com.nextcloud.talk.adapters;

import android.text.TextUtils;

import com.nextcloud.talk.utils.ApiUtils;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

public class ParticipantDisplayItem {

    public interface Observer {
        void onChange();
    }

    private final ParticipantDisplayItemNotifier participantDisplayItemNotifier = new ParticipantDisplayItemNotifier();

    private final String baseUrl;
    private final String defaultGuestNick;
    private final EglBase rootEglBase;

    private final String session;
    private final String streamType;

    private String userId;
    private PeerConnection.IceConnectionState iceConnectionState;
    private String nick;
    private String urlForAvatar;
    private MediaStream mediaStream;
    private boolean streamEnabled;
    private boolean isAudioEnabled;

    public ParticipantDisplayItem(String baseUrl, String userId, String session, PeerConnection.IceConnectionState iceConnectionState, String nick, String defaultGuestNick, MediaStream mediaStream, String streamType, boolean streamEnabled, EglBase rootEglBase) {
        this.baseUrl = baseUrl;
        this.userId = userId;
        this.session = session;
        this.iceConnectionState = iceConnectionState;
        this.nick = nick;
        this.defaultGuestNick = defaultGuestNick;
        this.mediaStream = mediaStream;
        this.streamType = streamType;
        this.streamEnabled = streamEnabled;
        this.rootEglBase = rootEglBase;

        this.updateUrlForAvatar();
    }

    public void setUserId(String userId) {
        this.userId = userId;

        this.updateUrlForAvatar();

        participantDisplayItemNotifier.notifyChange();
    }

    public boolean isConnected() {
        return iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
            iceConnectionState == PeerConnection.IceConnectionState.COMPLETED;
    }

    public void setIceConnectionState(PeerConnection.IceConnectionState iceConnectionState) {
        this.iceConnectionState = iceConnectionState;

        participantDisplayItemNotifier.notifyChange();
    }

    public String getNick() {
        if (TextUtils.isEmpty(userId) && TextUtils.isEmpty(nick)) {
            return defaultGuestNick;
        }

        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;

        this.updateUrlForAvatar();

        participantDisplayItemNotifier.notifyChange();
    }

    public String getUrlForAvatar() {
        return urlForAvatar;
    }

    private void updateUrlForAvatar() {
        if (!TextUtils.isEmpty(userId)) {
            urlForAvatar = ApiUtils.getUrlForAvatar(baseUrl, userId, true);
        } else {
            urlForAvatar = ApiUtils.getUrlForGuestAvatar(baseUrl, getNick(), true);
        }
    }

    public MediaStream getMediaStream() {
        return mediaStream;
    }

    public void setMediaStream(MediaStream mediaStream) {
        this.mediaStream = mediaStream;

        participantDisplayItemNotifier.notifyChange();
    }

    public boolean isStreamEnabled() {
        return streamEnabled;
    }

    public void setStreamEnabled(boolean streamEnabled) {
        this.streamEnabled = streamEnabled;

        participantDisplayItemNotifier.notifyChange();
    }

    public EglBase getRootEglBase() {
        return rootEglBase;
    }

    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }

    public void setAudioEnabled(boolean audioEnabled) {
        isAudioEnabled = audioEnabled;

        participantDisplayItemNotifier.notifyChange();
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


