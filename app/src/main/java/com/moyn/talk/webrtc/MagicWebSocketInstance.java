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

package com.moyn.talk.webrtc;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.moyn.talk.R;
import com.moyn.talk.application.NextcloudTalkApplication;
import com.moyn.talk.events.NetworkEvent;
import com.moyn.talk.events.WebSocketCommunicationEvent;
import com.moyn.talk.models.database.UserEntity;
import com.moyn.talk.models.json.participants.Participant;
import com.moyn.talk.models.json.signaling.NCMessageWrapper;
import com.moyn.talk.models.json.signaling.NCSignalingMessage;
import com.moyn.talk.models.json.websocket.BaseWebSocketMessage;
import com.moyn.talk.models.json.websocket.ByeWebSocketMessage;
import com.moyn.talk.models.json.websocket.CallOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.ErrorOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.EventOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.HelloResponseOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.JoinedRoomOverallWebSocketMessage;
import com.moyn.talk.utils.MagicMap;
import com.moyn.talk.utils.bundle.BundleKeys;

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

import static com.moyn.talk.models.json.participants.Participant.ActorType.GUESTS;
import static com.moyn.talk.models.json.participants.Participant.ActorType.USERS;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicWebSocketInstance extends WebSocketListener {
    private static final String TAG = "MagicWebSocketInstance";

    @Inject
    OkHttpClient okHttpClient;

    @Inject
    EventBus eventBus;

    @Inject
    Context context;

    private UserEntity conversationUser;
    private String webSocketTicket;
    private String resumeId;
    private String sessionId;
    private boolean hasMCU;
    private boolean connected;
    private WebSocketConnectionHelper webSocketConnectionHelper;
    private WebSocket internalWebSocket;
    private MagicMap magicMap;
    private String connectionUrl;

    private String currentRoomToken;
    private int restartCount = 0;
    private boolean reconnecting = false;

    private HashMap<String, Participant> usersHashMap;

    private List<String> messagesQueue = new ArrayList<>();

    MagicWebSocketInstance(UserEntity conversationUser, String connectionUrl, String webSocketTicket) {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        this.connectionUrl = connectionUrl;
        this.conversationUser = conversationUser;
        this.webSocketTicket = webSocketTicket;
        this.webSocketConnectionHelper = new WebSocketConnectionHelper();
        this.usersHashMap = new HashMap<>();
        magicMap = new MagicMap();

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

        // TODO: when improving logging, keep in mind this issue: https://github.com/nextcloud/talk-android/issues/1013
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
                            helloHasHap.put("roomToken", currentRoomToken);
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
                                                            refreshChatHashMap.put(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), (String) messageHashMap.get("roomid"));
                                                            refreshChatHashMap.put(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), Long.toString(conversationUser.getId()));
                                                            eventBus.post(new WebSocketCommunicationEvent("refreshChat", refreshChatHashMap));
                                                        }
                                                    }
                                            }
                                        }
                                    } else if (eventOverallWebSocketMessage.getEventMap().get("type").equals("join")) {
                                        List<HashMap<String, Object>> joinEventMap = (List<HashMap<String, Object>>) eventOverallWebSocketMessage.getEventMap().get("join");
                                        HashMap<String, Object> internalHashMap;
                                        Participant participant;
                                        for (int i = 0; i < joinEventMap.size(); i++) {
                                            internalHashMap = joinEventMap.get(i);
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
                                case "participants":
                                    if (eventOverallWebSocketMessage.getEventMap().get("type").equals("update")) {
                                        HashMap<String, String> refreshChatHashMap = new HashMap<>();
                                        HashMap<String, Object> updateEventMap = (HashMap<String, Object>) eventOverallWebSocketMessage.getEventMap().get("update");
                                        refreshChatHashMap.put("roomToken", (String) updateEventMap.get("roomid"));
                                        refreshChatHashMap.put("jobId", Integer.toString(magicMap.add(updateEventMap.get("users"))));
                                        eventBus.post(new WebSocketCommunicationEvent("participantsUpdate", refreshChatHashMap));
                                    }
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

                        if (!TextUtils.isEmpty(ncSignalingMessage.getFrom())) {
                            HashMap<String, String> messageHashMap = new HashMap<>();
                            messageHashMap.put("jobId", Integer.toString(magicMap.add(ncSignalingMessage)));
                            eventBus.post(new WebSocketCommunicationEvent("signalingMessage", messageHashMap));
                        }
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
        joinRoomHashMap.put("roomToken", currentRoomToken);
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

    public void sendCallMessage(NCMessageWrapper ncMessageWrapper) {
        try {
            String message = LoganSquare.serialize(webSocketConnectionHelper.getAssembledCallMessageModel(ncMessageWrapper));
            if (!connected || reconnecting) {
                messagesQueue.add(message);
            } else {
                internalWebSocket.send(message);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to serialize signaling message", e);
        }
    }

    public Object getJobWithId(Integer id) {
        Object copyJob = magicMap.get(id);
        magicMap.remove(id);
        return copyJob;
    }

    public void requestOfferForSessionIdWithType(String sessionIdParam, String roomType) {
        try {
            String message = LoganSquare.serialize(webSocketConnectionHelper.getAssembledRequestOfferModel(sessionIdParam, roomType));
            if (!connected || reconnecting) {
                messagesQueue.add(message);
            } else {
                internalWebSocket.send(message);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to offer request. sessionIdParam: " + sessionIdParam + " roomType:" + roomType, e);
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

        return NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_nick_guest);
    }

    public String getUserIdForSession(String session) {
        Participant participant = usersHashMap.get(session);
        if (participant != null) {
            if (participant.getActorType() == USERS) {
                return participant.getActorId();
            }
        }

        return "";
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(NetworkEvent networkEvent) {
        if (networkEvent.getNetworkConnectionEvent().equals(NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED) && !isConnected()) {
            restartWebSocket();
        }
    }
}
