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

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.signaling.NCMessageWrapper;
import com.nextcloud.talk.models.json.websocket.BaseWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.ByeWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.CallOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.ErrorOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.EventOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.HelloResponseOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.JoinedRoomOverallWebSocketMessage;
import com.nextcloud.talk.utils.MagicMap;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
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
    private WebSocket webSocket;
    private MagicMap magicMap;
    private String connectionUrl;

    private String currentRoomToken;
    private boolean isPermanentlyClosed = false;
    private int restartCount = 0;

    private HashMap<String, String> displayNameHashMap;
    private HashMap<String, String> userIdSesssionHashMap;

    MagicWebSocketInstance(UserEntity conversationUser, String connectionUrl, String webSocketTicket) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.connectionUrl = connectionUrl;
        this.conversationUser = conversationUser;
        this.webSocketTicket = webSocketTicket;
        this.webSocketConnectionHelper = new WebSocketConnectionHelper();
        this.displayNameHashMap = new HashMap<>();
        this.userIdSesssionHashMap = new HashMap<>();
        magicMap = new MagicMap();

        restartWebSocket();
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        try {
            if (TextUtils.isEmpty(resumeId)) {
                webSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledHelloModel(conversationUser, webSocketTicket)));
            } else {
                webSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledHelloModelForResume(resumeId)));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to serialize hello model");
        }
    }

    public void restartWebSocket() {
        Request request = new Request.Builder().url(connectionUrl).build();
        this.webSocket = okHttpClient.newWebSocket(request, this);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "Receiving : " + text);

        try {
            BaseWebSocketMessage baseWebSocketMessage = LoganSquare.parse(text, BaseWebSocketMessage.class);
            String messageType = baseWebSocketMessage.getType();
            switch (messageType) {
                case "hello":
                    restartCount = 0;
                    HelloResponseOverallWebSocketMessage helloResponseWebSocketMessage = LoganSquare.parse(text, HelloResponseOverallWebSocketMessage.class);
                    resumeId = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().getResumeId();
                    sessionId = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().getSessionId();
                    hasMCU = helloResponseWebSocketMessage.getHelloResponseWebSocketMessage().serverHasMCUSupport();
                    connected = true;
                    eventBus.post(new WebSocketCommunicationEvent("hello", null));
                    break;
                case "error":
                    ErrorOverallWebSocketMessage errorOverallWebSocketMessage = LoganSquare.parse(text, ErrorOverallWebSocketMessage.class);
                    if (("no_such_session").equals(errorOverallWebSocketMessage.getErrorWebSocketMessage().getCode().equals("no_such_session"))) {
                        resumeId = "";

                    }
                    if (!isPermanentlyClosed) {
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
                    if (callOverallWebSocketMessage.getCallWebSocketMessage().getNcSignalingMessage().getFrom() != null) {
                        HashMap<String, String> messageHashMap = new HashMap<>();
                        messageHashMap.put("jobId", Integer.toString(magicMap.add(callOverallWebSocketMessage.getCallWebSocketMessage().getNcSignalingMessage())));
                        eventBus.post(new WebSocketCommunicationEvent("signalingMessage", messageHashMap));
                    }
                    break;
                case "bye":
                    connected = false;
                    isPermanentlyClosed = true;
                default:
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to WebSocket message");
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        Log.d(TAG, "Receiving bytes : " + bytes.hex());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Closing : " + code + " / " + reason);
        connected = false;
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.d(TAG, "Error : " + t.getMessage());
        connected = false;
        if (restartCount < 4) {
            restartWebSocket();
        } else {
            isPermanentlyClosed = true;
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean hasMCU() {
        return hasMCU;
    }

    public void joinRoomWithRoomTokenAndSession(String roomToken, String normalBackendSession) {
        if (!roomToken.equals(currentRoomToken)) {
            if (isConnected()) {
                try {
                    webSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledJoinOrLeaveRoomModel(roomToken, normalBackendSession)));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to serialize room overall websocket message");
                }
            }
        } else {
            HashMap<String, String> joinRoomHashMap = new HashMap<>();
            joinRoomHashMap.put("roomToken", currentRoomToken);
            eventBus.post(new WebSocketCommunicationEvent("roomJoined", joinRoomHashMap));
        }
    }

    public void sendCallMessage(NCMessageWrapper ncMessageWrapper) {
        if (isConnected()) {
            try {
                webSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledCallMessageModel(ncMessageWrapper)));
            } catch (IOException e) {
                Log.e(TAG, "Failed to serialize signaling message");
            }
        }
    }

    public Object getJobWithId(Integer id) {
        Object copyJob = magicMap.get(id);
        magicMap.remove(id);
        return copyJob;
    }

    public void requestOfferForSessionIdWithType(String sessionIdParam, String roomType) {
        if (isConnected()) {
            try {
                webSocket.send(LoganSquare.serialize(webSocketConnectionHelper.getAssembledRequestOfferModel(sessionIdParam, roomType)));
            } catch (IOException e) {
                Log.e(TAG, "Failed to offer request");
            }
        }
    }

    void sendBye() {
        if (isConnected()) {
            try {
                ByeWebSocketMessage byeWebSocketMessage = new ByeWebSocketMessage();
                byeWebSocketMessage.setType("bye");
                byeWebSocketMessage.setBye(new HashMap<>());
                webSocket.send(LoganSquare.serialize(byeWebSocketMessage));
            } catch (IOException e) {
                Log.e(TAG, "Failed to serialize bye message");
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    boolean isPermanentlyClosed() {
        return isPermanentlyClosed;
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
