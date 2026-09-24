/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import android.annotation.SuppressLint;
import android.util.Log;

import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessageDto;
import com.nextcloud.talk.models.json.signaling.settings.FederationSettingsDto;
import com.nextcloud.talk.models.json.websocket.ActorWebSocketMessageDto;
import com.nextcloud.talk.models.json.websocket.AuthParametersWebSocketMessageDto;
import com.nextcloud.talk.models.json.websocket.AuthWebSocketMessageDto;
import com.nextcloud.talk.models.json.websocket.CallOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.CallWebSocketMessageDto;
import com.nextcloud.talk.models.json.websocket.HelloOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.HelloWebSocketMessageDto;
import com.nextcloud.talk.models.json.websocket.RoomFederationWebSocketMessageDto;
import com.nextcloud.talk.models.json.websocket.RoomOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.RoomWebSocketMessageDto;
import com.nextcloud.talk.utils.ApiUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import autodagger.AutoInjector;
import okhttp3.OkHttpClient;

@AutoInjector(NextcloudTalkApplication.class)
public class WebSocketConnectionHelper {
    public static final String TAG = "WebSocketConnectionHelper";
    private static Map<Long, WebSocketInstance> webSocketInstanceMap = new HashMap<>();

    @Inject
    OkHttpClient okHttpClient;


    public WebSocketConnectionHelper() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @SuppressLint("LongLogTag")
    public static synchronized WebSocketInstance getWebSocketInstanceForUser(User user) {
        WebSocketInstance webSocketInstance = webSocketInstanceMap.get(user.getId());

        if (webSocketInstance == null) {
            Log.d(TAG, "No webSocketInstance found for user " + user.getDisplayName() +
                " (userId:" + user.getId() + ")");
        } else {
            Log.d(TAG, "Existing webSocketInstance found for user " + user.getDisplayName() +
                " (userId:" + user.getId() + ")");
        }

        return webSocketInstance;
    }

    public static synchronized WebSocketInstance getExternalSignalingInstanceForServer(String url,
                                                                                       User user,
                                                                                       String webSocketTicket,
                                                                                       boolean isGuest) {
        String generatedURL = url.replace("https://", "wss://").replace("http://", "ws://");

        if (generatedURL.endsWith("/")) {
            generatedURL += "spreed";
        } else {
            generatedURL += "/spreed";
        }

        long userId = isGuest ? -1 : user.getId();

        if (userId == -1) {
            deleteExternalSignalingInstanceForUserEntity(userId);
        } else {
            WebSocketInstance webSocketInstance = getWebSocketInstanceForUser(user);
            if (webSocketInstance != null) {
                Log.d(TAG, "Reusing webSocketInstance " + webSocketInstance.hashCode() + " for userId " + userId);
                return webSocketInstance;
            }
        }

        Log.d(TAG, "Creating new webSocketInstance for userId " + userId);

        WebSocketInstance webSocketInstance = new WebSocketInstance(user, generatedURL, webSocketTicket);
        webSocketInstanceMap.put(user.getId(), webSocketInstance);
        return webSocketInstance;
    }

    public static synchronized void deleteExternalSignalingInstanceForUserEntity(long id) {
        WebSocketInstance webSocketInstance;
        if ((webSocketInstance = webSocketInstanceMap.get(id)) != null) {
            if (webSocketInstance.isConnected()) {
                webSocketInstance.sendBye();
                webSocketInstanceMap.remove(id);
            }
        }
    }

    HelloOverallWebSocketMessage getAssembledHelloModel(User user, String ticket) {
        int apiVersion = ApiUtils.getSignalingApiVersion(user, new int[]{ApiUtils.API_V3, 2, 1});

        HelloOverallWebSocketMessage helloOverallWebSocketMessage = new HelloOverallWebSocketMessage();
        helloOverallWebSocketMessage.setType("hello");
        HelloWebSocketMessageDto helloWebSocketMessage = new HelloWebSocketMessageDto();
        helloWebSocketMessage.setVersion("1.0");
        AuthWebSocketMessageDto authWebSocketMessage = new AuthWebSocketMessageDto();
        authWebSocketMessage.setUrl(ApiUtils.getUrlForSignalingBackend(apiVersion, user.getBaseUrl()));
        AuthParametersWebSocketMessageDto authParametersWebSocketMessage = new AuthParametersWebSocketMessageDto();
        authParametersWebSocketMessage.setTicket(ticket);
        if (!("?").equals(user.getUserId())) {
            authParametersWebSocketMessage.setUserid(user.getUserId());
        }
        authWebSocketMessage.setAuthParametersWebSocketMessage(authParametersWebSocketMessage);
        helloWebSocketMessage.setAuthWebSocketMessage(authWebSocketMessage);

        List<String> features = Collections.singletonList("chat-relay");
        helloWebSocketMessage.setFeatures(features);

        helloOverallWebSocketMessage.setHelloWebSocketMessage(helloWebSocketMessage);
        return helloOverallWebSocketMessage;
    }

    HelloOverallWebSocketMessage getAssembledHelloModelForResume(String resumeId) {
        HelloOverallWebSocketMessage helloOverallWebSocketMessage = new HelloOverallWebSocketMessage();
        helloOverallWebSocketMessage.setType("hello");
        HelloWebSocketMessageDto helloWebSocketMessage = new HelloWebSocketMessageDto();
        helloWebSocketMessage.setVersion("1.0");
        helloWebSocketMessage.setResumeid(resumeId);
        List<String> features = Collections.singletonList("chat-relay");
        helloWebSocketMessage.setFeatures(features);
        helloOverallWebSocketMessage.setHelloWebSocketMessage(helloWebSocketMessage);
        return helloOverallWebSocketMessage;
    }

    RoomOverallWebSocketMessage getAssembledJoinOrLeaveRoomModel(String roomId, String sessionId,
                                                                 FederationSettingsDto federation) {
        RoomOverallWebSocketMessage roomOverallWebSocketMessage = new RoomOverallWebSocketMessage();
        roomOverallWebSocketMessage.setType("room");
        RoomWebSocketMessageDto roomWebSocketMessage = new RoomWebSocketMessageDto();
        roomWebSocketMessage.setRoomId(roomId);
        roomWebSocketMessage.setSessionId(sessionId);
        if (federation != null) {
            String federationAuthToken = null;
            if (federation.getHelloAuthParams() != null) {
                federationAuthToken = federation.getHelloAuthParams().getToken();
            }
            RoomFederationWebSocketMessageDto roomFederationWebSocketMessage = new RoomFederationWebSocketMessageDto();
            roomFederationWebSocketMessage.setSignaling(federation.getServer());
            roomFederationWebSocketMessage.setUrl(federation.getNextcloudServer() + "/ocs/v2.php/apps/spreed/api/v3/signaling/backend");
            roomFederationWebSocketMessage.setRoomid(federation.getRoomId());
            roomFederationWebSocketMessage.setToken(federationAuthToken);
            roomWebSocketMessage.setRoomFederationWebSocketMessage(roomFederationWebSocketMessage);
        }
        roomOverallWebSocketMessage.setRoomWebSocketMessage(roomWebSocketMessage);
        return roomOverallWebSocketMessage;
    }

    CallOverallWebSocketMessage getAssembledCallMessageModel(NCSignalingMessageDto ncSignalingMessage) {
        CallOverallWebSocketMessage callOverallWebSocketMessage = new CallOverallWebSocketMessage();
        callOverallWebSocketMessage.setType("message");

        CallWebSocketMessageDto callWebSocketMessage = new CallWebSocketMessageDto();

        ActorWebSocketMessageDto actorWebSocketMessage = new ActorWebSocketMessageDto();
        actorWebSocketMessage.setType("session");
        actorWebSocketMessage.setSessionId(ncSignalingMessage.getTo());
        callWebSocketMessage.setRecipientWebSocketMessage(actorWebSocketMessage);
        callWebSocketMessage.setNcSignalingMessage(ncSignalingMessage);

        callOverallWebSocketMessage.setCallWebSocketMessage(callWebSocketMessage);
        return callOverallWebSocketMessage;
    }
}
