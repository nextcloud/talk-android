/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.webrtc;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.events.NetworkEvent;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.models.json.websocket.BaseWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.ByeWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.CallOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.ErrorOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.EventOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.HelloResponseOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.JoinedRoomOverallWebSocketMessage;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;
import com.nextcloud.talk.signaling.SignalingMessageSender;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import autodagger.AutoInjector;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static com.nextcloud.talk.models.json.participants.Participant.ActorType.GUESTS;
import static com.nextcloud.talk.models.json.participants.Participant.ActorType.USERS;
import static com.nextcloud.talk.webrtc.Globals.ROOM_TOKEN;
import static com.nextcloud.talk.webrtc.Globals.TARGET_PARTICIPANTS;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicWebSocketInstance extends WebSocketListener {
    private static final String TAG = "MagicWebSocketInstance";


    @Inject
    OkHttpClient okHttpClient;

    @Inject
    EventBus eventBus;

    @Inject
    Context context;

    private User conversationUser;
    private String webSocketTicket;
    private String resumeId;
    private String sessionId;
    private boolean hasMCU;
    private boolean connected;
    private WebSocketConnectionHelper webSocketConnectionHelper;
    private WebSocket internalWebSocket;
    private String connectionUrl;

    private String currentRoomToken;
    private int restartCount = 0;
    private boolean reconnecting = false;

    private HashMap<String, Participant> usersHashMap;

    private List<String> messagesQueue = new ArrayList<>();

    private final ExternalSignalingMessageReceiver signalingMessageReceiver = new ExternalSignalingMessageReceiver();

    private final ExternalSignalingMessageSender signalingMessageSender = new ExternalSignalingMessageSender();

    MagicWebSocketInstance(User conversationUser, String connectionUrl, String webSocketTicket) {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        this.connectionUrl = connectionUrl;
        this.conversationUser = conversationUser;
        this.webSocketTicket = webSocketTicket;
        this.webSocketConnectionHelper = new WebSocketConnectionHelper();
        this.usersHashMap = new HashMap<>();

        connected = false;
        eventBus.register(this);

        restartWebSocket();
    }

    private void sendHello() {
        try {
            if (TextUtils.isEmpty(resumeId)) {
                internalWebSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledHelloModel(conversationUser, webSocketTicket)));
            } else {
                internalWebSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledHelloModelForResume(resumeId)));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to serialize hello model");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        internalWebSocket = webSocket;
        sendHello();
    }

    private void closeWebSocket(WebSocket webSocket) {
        webSocket.close(1000, null);
        webSocket.cancel();
        if (webSocket == internalWebSocket) {
            connected = false;
            messagesQueue = new ArrayList<>();
        }

        restartWebSocket();
    }


    public void clearResumeId() {
        resumeId = "";
    }

    public void restartWebSocket() {
        reconnecting = true;

        // TODO when improving logging, keep in mind this issue: https://github.com/nextcloud/talk-android/issues/1013
        Log.d(TAG, "restartWebSocket: " + connectionUrl);
        Request request = new Request.Builder().url(connectionUrl).build();
        okHttpClient.newWebSocket(request, this);
        restartCount++;
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (webSocket == internalWebSocket) {
            Log.d(TAG, "Receiving : " + webSocket.toString() + " " + text);

            try {
                BaseWebSocketMessage baseWebSocketMessage = LoganSquare.parse(text, BaseWebSocketMessage.class);
                String messageType = baseWebSocketMessage.getType();
                switch (messageType) {
                    case "hello":
                        connected = true;
                        reconnecting = false;
                        restartCount = 0;
                        String oldResumeId = resumeId;
                        HelloResponseOverallWebSocketMessage helloResponseWebSocketMessage = LoganSquare.parse(text, HelloResponseOverallWebSocketMessage.class);
                        resumeId = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().getResumeId();
                        sessionId = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().getSessionId();
                        hasMCU = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().serverHasMCUSupport();

                        for (int i = 0; i < messagesQueue.size(); i++) {
                            webSocket.send(messagesQueue.get(i));
                        }

                        messagesQueue = new ArrayList<>();
                        HashMap<String, String> helloHasHap = new HashMap<>();
                        if (!TextUtils.isEmpty(oldResumeId)) {
                            helloHasHap.put("oldResumeId", oldResumeId);
                        } else {
                            currentRoomToken = "";
                        }

                        if (!TextUtils.isEmpty(currentRoomToken)) {
                            helloHasHap.put(ROOM_TOKEN, currentRoomToken);
                        }
                        eventBus.post(new WebSocketCommunicationEvent("hello", helloHasHap));
                        break;
                    case "error":
                        Log.e(TAG, "Received error: " + text);
                        ErrorOverallWebSocketMessage errorOverallWebSocketMessage = LoganSquare.parse(text, ErrorOverallWebSocketMessage.class);
                        if (("no_such_session").equals(errorOverallWebSocketMessage.getErrorWebSocketMessage().getCode())) {
                            Log.d(TAG, "WebSocket " + webSocket.hashCode() + " resumeID " + resumeId + " expired");
                            resumeId = "";
                            currentRoomToken = "";
                            restartWebSocket();
                        } else if (("hello_expected").equals(errorOverallWebSocketMessage.getErrorWebSocketMessage().getCode())) {
                            restartWebSocket();
                        }

                        break;
                    case "room":
                        JoinedRoomOverallWebSocketMessage joinedRoomOverallWebSocketMessage = LoganSquare.parse(text, JoinedRoomOverallWebSocketMessage.class);
                        currentRoomToken = joinedRoomOverallWebSocketMessage.getRoomWebSocketMessage().getRoomId();
                        if (joinedRoomOverallWebSocketMessage.getRoomWebSocketMessage().getRoomPropertiesWebSocketMessage() != null && !TextUtils.isEmpty(currentRoomToken)) {
                            sendRoomJoinedEvent();
                        }
                        break;
                    case "event":
                        EventOverallWebSocketMessage eventOverallWebSocketMessage = LoganSquare.parse(text, EventOverallWebSocketMessage.class);
                        if (eventOverallWebSocketMessage.getEventMap() != null) {
                            String target = (String) eventOverallWebSocketMessage.getEventMap().get("target");
                            switch (target) {
                                case "room":
                                    if (eventOverallWebSocketMessage.getEventMap().get("type").equals("message")) {
                                        Map<String, Object> messageHashMap =
                                            (Map<String, Object>) eventOverallWebSocketMessage.getEventMap().get("message");
                                        if (messageHashMap.containsKey("data")) {
                                            Map<String, Object> dataHashMap = (Map<String, Object>) messageHashMap.get(
                                                "data");
                                            if (dataHashMap.containsKey("chat")) {
                                                boolean shouldRefreshChat;
                                                Map<String, Object> chatMap = (Map<String, Object>) dataHashMap.get("chat");
                                                if (chatMap.containsKey("refresh")) {
                                                    shouldRefreshChat = (boolean) chatMap.get("refresh");
                                                    if (shouldRefreshChat) {
                                                        HashMap<String, String> refreshChatHashMap = new HashMap<>();
                                                        refreshChatHashMap.put(BundleKeys.KEY_ROOM_TOKEN, (String) messageHashMap.get("roomid"));
                                                        refreshChatHashMap.put(BundleKeys.KEY_INTERNAL_USER_ID, Long.toString(conversationUser.getId()));
                                                        eventBus.post(new WebSocketCommunicationEvent("refreshChat", refreshChatHashMap));
                                                    }
                                                }
                                            }
                                        }
                                    } else if (eventOverallWebSocketMessage.getEventMap().get("type").equals("join")) {
                                        List<HashMap<String, Object>> joinEventList = (List<HashMap<String, Object>>) eventOverallWebSocketMessage.getEventMap().get("join");
                                        HashMap<String, Object> internalHashMap;
                                        Participant participant;
                                        for (int i = 0; i < joinEventList.size(); i++) {
                                            internalHashMap = joinEventList.get(i);
                                            HashMap<String, Object> userMap = (HashMap<String, Object>) internalHashMap.get("user");
                                            participant = new Participant();
                                            String userId = (String) internalHashMap.get("userid");
                                            if (userId != null) {
                                                participant.setActorType(USERS);
                                                participant.setActorId(userId);
                                            } else {
                                                participant.setActorType(GUESTS);
                                                // FIXME seems to be not given by the HPB: participant.setActorId();
                                            }
                                            if (userMap != null) {
                                                // There is no "user" attribute for guest participants.
                                                participant.setDisplayName((String) userMap.get("displayname"));
                                            }
                                            usersHashMap.put((String) internalHashMap.get("sessionid"), participant);
                                        }
                                    }
                                    break;
                                case TARGET_PARTICIPANTS:
                                    signalingMessageReceiver.process(eventOverallWebSocketMessage.getEventMap());
                                    break;
                            }
                        }
                        break;
                    case "message":
                        CallOverallWebSocketMessage callOverallWebSocketMessage = LoganSquare.parse(text, CallOverallWebSocketMessage.class);
                        NCSignalingMessage ncSignalingMessage = callOverallWebSocketMessage.getCallWebSocketMessage().getNcSignalingMessage();
                        if (TextUtils.isEmpty(ncSignalingMessage.getFrom()) && callOverallWebSocketMessage.getCallWebSocketMessage().getSenderWebSocketMessage() != null) {
                            ncSignalingMessage.setFrom(callOverallWebSocketMessage.getCallWebSocketMessage().getSenderWebSocketMessage().getSessionId());
                        }

                        signalingMessageReceiver.process(ncSignalingMessage);
                        break;
                    case "bye":
                        connected = false;
                        resumeId = "";
                    default:
                        break;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to recognize WebSocket message", e);
            }
        }
    }

    private void sendRoomJoinedEvent() {
        HashMap<String, String> joinRoomHashMap = new HashMap<>();
        joinRoomHashMap.put(ROOM_TOKEN, currentRoomToken);
        eventBus.post(new WebSocketCommunicationEvent("roomJoined", joinRoomHashMap));
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        Log.d(TAG, "Receiving bytes : " + bytes.hex());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Closing : " + code + " / " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.d(TAG, "Error : WebSocket " + webSocket.hashCode() + " onFailure: " + t.getMessage());
        closeWebSocket(webSocket);
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean hasMCU() {
        return hasMCU;
    }

    public void joinRoomWithRoomTokenAndSession(String roomToken, String normalBackendSession) {
        Log.d(TAG, "joinRoomWithRoomTokenAndSession");
        Log.d(TAG, "   roomToken: " + roomToken);
        Log.d(TAG, "   session: " + normalBackendSession);
        try {
            String message = LoganSquare.serialize(webSocketConnectionHelper.getAssembledJoinOrLeaveRoomModel(roomToken, normalBackendSession));
            if (!connected || reconnecting) {
                messagesQueue.add(message);
            } else {
                if (roomToken.equals(currentRoomToken)) {
                    sendRoomJoinedEvent();
                } else {
                    internalWebSocket.send(message);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void sendCallMessage(NCSignalingMessage ncSignalingMessage) {
        try {
            String message = LoganSquare.serialize(webSocketConnectionHelper.getAssembledCallMessageModel(ncSignalingMessage));
            if (!connected || reconnecting) {
                messagesQueue.add(message);
            } else {
                internalWebSocket.send(message);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to serialize signaling message", e);
        }
    }

    void sendBye() {
        if (connected) {
            try {
                ByeWebSocketMessage byeWebSocketMessage = new ByeWebSocketMessage();
                byeWebSocketMessage.setType("bye");
                byeWebSocketMessage.setBye(new HashMap<>());
                internalWebSocket.send(LoganSquare.serialize(byeWebSocketMessage));
            } catch (IOException e) {
                Log.e(TAG, "Failed to serialize bye message");
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDisplayNameForSession(String session) {
        Participant participant = usersHashMap.get(session);
        if (participant != null) {
            if (participant.getDisplayName() != null) {
                return participant.getDisplayName();
            }
        }

        return "";
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(NetworkEvent networkEvent) {
        if (networkEvent.getNetworkConnectionEvent() == NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED && !isConnected()) {
            restartWebSocket();
        }
    }

    public SignalingMessageReceiver getSignalingMessageReceiver() {
        return signalingMessageReceiver;
    }

    public SignalingMessageSender getSignalingMessageSender() {
        return signalingMessageSender;
    }

    /**
     * Temporary implementation of SignalingMessageReceiver until signaling related code is extracted to a Signaling
     * class.
     *
     * All listeners are called in the WebSocket reader thread. This thread should be the same as long as the
     * WebSocket stays connected, but it may change whenever it is connected again.
     */
    private static class ExternalSignalingMessageReceiver extends SignalingMessageReceiver {
        public void process(Map<String, Object> eventMap) {
            processEvent(eventMap);
        }

        public void process(NCSignalingMessage message) {
            processSignalingMessage(message);
        }
    }

    private class ExternalSignalingMessageSender implements SignalingMessageSender {
        @Override
        public void send(NCSignalingMessage ncSignalingMessage) {
            sendCallMessage(ncSignalingMessage);
        }
    }
}
