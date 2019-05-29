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

import android.text.TextUtils;
import android.util.Log;
import autodagger.AutoInjector;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.signaling.NCMessageWrapper;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.models.json.websocket.*;
import com.nextcloud.talk.utils.MagicMap;
import okhttp3.*;
import okio.ByteString;
import org.greenrobot.eventbus.EventBus;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicWebSocketInstance extends WebSocketListener {
    private static final String TAG = "MagicWebSocketInstance";

    @Inject
    OkHttpClient okHttpClient;

    @Inject
    EventBus eventBus;

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

    private HashMap<String, String> displayNameHashMap;
    private HashMap<String, String> userIdSesssionHashMap;

    private List<String> messagesQueue = new ArrayList<>();

    MagicWebSocketInstance(UserEntity conversationUser, String connectionUrl, String webSocketTicket) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.connectionUrl = connectionUrl;
        this.conversationUser = conversationUser;
        this.webSocketTicket = webSocketTicket;
        this.webSocketConnectionHelper = new WebSocketConnectionHelper();
        this.displayNameHashMap = new HashMap<>();
        this.userIdSesssionHashMap = new HashMap<>();
        magicMap = new MagicMap();

        connected = false;
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
        messagesQueue = new ArrayList<>();
        currentRoomToken = "";
    }

    private void restartWebSocket() {
        reconnecting = true;

        Request request = new Request.Builder().url(connectionUrl).build();
        okHttpClient.newWebSocket(request, this);
        restartCount++;
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (webSocket == internalWebSocket) {
            Log.d(TAG, "Receiving : " + text);
            try {
                BaseWebSocketMessage baseWebSocketMessage = LoganSquare.parse(text, BaseWebSocketMessage.class);
                String messageType = baseWebSocketMessage.getType();
                switch (messageType) {
                    case "hello":
                        connected = true;
                        reconnecting = false;
                        restartCount = 0;
                        HelloResponseOverallWebSocketMessage helloResponseWebSocketMessage = LoganSquare.parse(text, HelloResponseOverallWebSocketMessage.class);
                        resumeId = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().getResumeId();
                        sessionId = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().getSessionId();
                        hasMCU = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().serverHasMCUSupport();

                        for (int i = 0; i < messagesQueue.size(); i++) {
                            webSocket.send(messagesQueue.get(i));
                        }

                        messagesQueue = new ArrayList<>();
                        eventBus.post(new WebSocketCommunicationEvent("hello", null));
                        break;
                    case "error":
                        ErrorOverallWebSocketMessage errorOverallWebSocketMessage = LoganSquare.parse(text, ErrorOverallWebSocketMessage.class);
                        if (("no_such_session").equals(errorOverallWebSocketMessage.getErrorWebSocketMessage().getCode())) {
                            resumeId = "";
                            restartWebSocket();
                        } else if (("hello_expected").equals(errorOverallWebSocketMessage.getErrorWebSocketMessage().getCode())) {
                            restartWebSocket();
                        }

                        break;
                    case "room":
                        JoinedRoomOverallWebSocketMessage joinedRoomOverallWebSocketMessage = LoganSquare.parse(text, JoinedRoomOverallWebSocketMessage.class);
                        currentRoomToken = joinedRoomOverallWebSocketMessage.getRoomWebSocketMessage().getRoomId();
                        if (joinedRoomOverallWebSocketMessage.getRoomWebSocketMessage().getRoomPropertiesWebSocketMessage() != null && !TextUtils.isEmpty(currentRoomToken)) {
                            HashMap<String, String> joinRoomHashMap = new HashMap<>();
                            joinRoomHashMap.put("roomToken", currentRoomToken);
                            eventBus.post(new WebSocketCommunicationEvent("roomJoined", joinRoomHashMap));
                        } else {
                            userIdSesssionHashMap = new HashMap<>();
                            displayNameHashMap = new HashMap<>();
                        }
                        break;
                    case "event":
                        EventOverallWebSocketMessage eventOverallWebSocketMessage = LoganSquare.parse(text, EventOverallWebSocketMessage.class);
                        if (eventOverallWebSocketMessage.getEventMap() != null) {
                            String target = (String) eventOverallWebSocketMessage.getEventMap().get("target");
                            switch (target) {
                                case "room":
                                    if (eventOverallWebSocketMessage.getEventMap().get("type").equals("message")) {
                                        if (eventOverallWebSocketMessage.getEventMap().containsKey("data")) {
                                            Map<String, Object> dataHashMap = (Map<String, Object>) eventOverallWebSocketMessage.getEventMap().get("data");
                                            if (dataHashMap.containsKey("chat")) {
                                                boolean shouldRefreshChat;
                                                Map<String, Object> chatMap = (Map<String, Object>) dataHashMap.get("chat");
                                                if (chatMap.containsKey("refresh")) {
                                                    shouldRefreshChat = (boolean) chatMap.get("refresh");
                                                    if (shouldRefreshChat) {
                                                        HashMap<String, String> refreshChatHashMap = new HashMap<>();
                                                        refreshChatHashMap.put("roomToken", (String) eventOverallWebSocketMessage.getEventMap().get("roomid"));
                                                        eventBus.post(new WebSocketCommunicationEvent("refreshChat", refreshChatHashMap));
                                                    }
                                                }
                                            }
                                        }
                                    } else if (eventOverallWebSocketMessage.getEventMap().get("type").equals("join")) {
                                        List<HashMap<String, Object>> joinEventMap = (List<HashMap<String, Object>>) eventOverallWebSocketMessage.getEventMap().get("join");
                                        HashMap<String, Object> internalHashMap;
                                        for (int i = 0; i < joinEventMap.size(); i++) {
                                            internalHashMap = joinEventMap.get(i);
                                            HashMap<String, Object> userMap = (HashMap<String, Object>) internalHashMap.get("user");
                                            displayNameHashMap.put((String) internalHashMap.get("sessionid"), (String) userMap.get("displayname"));
                                            userIdSesssionHashMap.put((String) internalHashMap.get("userid"), (String) internalHashMap.get("sessionid"));
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
                Log.e(TAG, "Failed to WebSocket message");
            }
        }
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
        Log.d(TAG, "Error : " + t.getMessage());
        closeWebSocket(webSocket);
        restartWebSocket();
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
                internalWebSocket.send(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
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
            Log.e(TAG, "Failed to serialize signaling message");
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
            Log.e(TAG, "Failed to offer request");
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

    boolean isConnected() {
        return connected;
    }

    public String getDisplayNameForSession(String session) {
        if (displayNameHashMap.containsKey(session)) {
            return displayNameHashMap.get(session);
        }

        return NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_nick_guest);
    }

    public String getSessionForUserId(String userId) {
        return userIdSesssionHashMap.get(userId);
    }
}
