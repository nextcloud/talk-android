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

import com.moyn.talk.application.NextcloudTalkApplication;
import com.moyn.talk.models.database.UserEntity;
import com.moyn.talk.models.json.signaling.NCMessageWrapper;
import com.moyn.talk.models.json.websocket.ActorWebSocketMessage;
import com.moyn.talk.models.json.websocket.AuthParametersWebSocketMessage;
import com.moyn.talk.models.json.websocket.AuthWebSocketMessage;
import com.moyn.talk.models.json.websocket.CallOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.CallWebSocketMessage;
import com.moyn.talk.models.json.websocket.HelloOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.HelloWebSocketMessage;
import com.moyn.talk.models.json.websocket.RequestOfferOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.RequestOfferSignalingMessage;
import com.moyn.talk.models.json.websocket.RoomOverallWebSocketMessage;
import com.moyn.talk.models.json.websocket.RoomWebSocketMessage;
import com.moyn.talk.models.json.websocket.SignalingDataWebSocketMessageForOffer;
import com.moyn.talk.utils.ApiUtils;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import autodagger.AutoInjector;
import okhttp3.OkHttpClient;

@AutoInjector(NextcloudTalkApplication.class)
public class WebSocketConnectionHelper {
    private static Map<Long, MagicWebSocketInstance> magicWebSocketInstanceMap = new HashMap<>();

    @Inject
    OkHttpClient okHttpClient;


    public WebSocketConnectionHelper() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    public static synchronized MagicWebSocketInstance getMagicWebSocketInstanceForUserId(long userId) {
        if (userId != -1 && magicWebSocketInstanceMap.containsKey(userId)) {
            return magicWebSocketInstanceMap.get(userId);
        }

        return null;
    }

    public static synchronized MagicWebSocketInstance getExternalSignalingInstanceForServer(String url, UserEntity userEntity, String webSocketTicket, boolean isGuest) {
        String generatedURL = url.replace("https://", "wss://").replace("http://", "ws://");

        if (generatedURL.endsWith("/")) {
            generatedURL += "spreed";
        } else {
            generatedURL += "/spreed";
        }

        long userId = isGuest ? -1 : userEntity.getId();


        MagicWebSocketInstance magicWebSocketInstance;
        if (userId != -1 && magicWebSocketInstanceMap.containsKey(userEntity.getId()) && (magicWebSocketInstance = magicWebSocketInstanceMap.get(userEntity.getId())) != null) {
            return magicWebSocketInstance;
        } else {
            if (userId == -1) {
                deleteExternalSignalingInstanceForUserEntity(userId);
            }
            magicWebSocketInstance = new MagicWebSocketInstance(userEntity, generatedURL, webSocketTicket);
            magicWebSocketInstanceMap.put(userEntity.getId(), magicWebSocketInstance);
            return magicWebSocketInstance;
        }
    }

    public static synchronized void deleteExternalSignalingInstanceForUserEntity(long id) {
        MagicWebSocketInstance magicWebSocketInstance;
        if ((magicWebSocketInstance = magicWebSocketInstanceMap.get(id)) != null) {
            if (magicWebSocketInstance.isConnected()) {
                magicWebSocketInstance.sendBye();
                magicWebSocketInstanceMap.remove(id);
            }
        }
    }

    HelloOverallWebSocketMessage getAssembledHelloModel(UserEntity userEntity, String ticket) {
        int apiVersion = ApiUtils.getSignalingApiVersion(userEntity, new int[] {ApiUtils.APIv3, 2, 1});

        HelloOverallWebSocketMessage helloOverallWebSocketMessage = new HelloOverallWebSocketMessage();
        helloOverallWebSocketMessage.setType("hello");
        HelloWebSocketMessage helloWebSocketMessage = new HelloWebSocketMessage();
        helloWebSocketMessage.setVersion("1.0");
        AuthWebSocketMessage authWebSocketMessage = new AuthWebSocketMessage();
        authWebSocketMessage.setUrl(ApiUtils.getUrlForSignalingBackend(apiVersion, userEntity.getBaseUrl()));
        AuthParametersWebSocketMessage authParametersWebSocketMessage = new AuthParametersWebSocketMessage();
        authParametersWebSocketMessage.setTicket(ticket);
        if (!userEntity.getUserId().equals("?")) {
            authParametersWebSocketMessage.setUserid(userEntity.getUserId());
        }
        authWebSocketMessage.setAuthParametersWebSocketMessage(authParametersWebSocketMessage);
        helloWebSocketMessage.setAuthWebSocketMessage(authWebSocketMessage);
        helloOverallWebSocketMessage.setHelloWebSocketMessage(helloWebSocketMessage);
        return helloOverallWebSocketMessage;
    }

    HelloOverallWebSocketMessage getAssembledHelloModelForResume(String resumeId) {
        HelloOverallWebSocketMessage helloOverallWebSocketMessage = new HelloOverallWebSocketMessage();
        helloOverallWebSocketMessage.setType("hello");
        HelloWebSocketMessage helloWebSocketMessage = new HelloWebSocketMessage();
        helloWebSocketMessage.setVersion("1.0");
        helloWebSocketMessage.setResumeid(resumeId);
        helloOverallWebSocketMessage.setHelloWebSocketMessage(helloWebSocketMessage);
        return helloOverallWebSocketMessage;
    }

    RoomOverallWebSocketMessage getAssembledJoinOrLeaveRoomModel(String roomId, String sessionId) {
        RoomOverallWebSocketMessage roomOverallWebSocketMessage = new RoomOverallWebSocketMessage();
        roomOverallWebSocketMessage.setType("room");
        RoomWebSocketMessage roomWebSocketMessage = new RoomWebSocketMessage();
        roomWebSocketMessage.setRoomId(roomId);
        roomWebSocketMessage.setSessiondId(sessionId);
        roomOverallWebSocketMessage.setRoomWebSocketMessage(roomWebSocketMessage);
        return roomOverallWebSocketMessage;
    }

    RequestOfferOverallWebSocketMessage getAssembledRequestOfferModel(String sessionId, String roomType) {
        RequestOfferOverallWebSocketMessage requestOfferOverallWebSocketMessage = new RequestOfferOverallWebSocketMessage();
        requestOfferOverallWebSocketMessage.setType("message");

        RequestOfferSignalingMessage requestOfferSignalingMessage = new RequestOfferSignalingMessage();

        ActorWebSocketMessage actorWebSocketMessage = new ActorWebSocketMessage();
        actorWebSocketMessage.setType("session");
        actorWebSocketMessage.setSessionId(sessionId);
        requestOfferSignalingMessage.setActorWebSocketMessage(actorWebSocketMessage);

        SignalingDataWebSocketMessageForOffer signalingDataWebSocketMessageForOffer = new SignalingDataWebSocketMessageForOffer();
        signalingDataWebSocketMessageForOffer.setRoomType(roomType);
        signalingDataWebSocketMessageForOffer.setType("requestoffer");
        requestOfferSignalingMessage.setSignalingDataWebSocketMessageForOffer(signalingDataWebSocketMessageForOffer);

        requestOfferOverallWebSocketMessage.setRequestOfferOverallWebSocketMessage(requestOfferSignalingMessage);
        return requestOfferOverallWebSocketMessage;
    }

    CallOverallWebSocketMessage getAssembledCallMessageModel(NCMessageWrapper ncMessageWrapper) {
        CallOverallWebSocketMessage callOverallWebSocketMessage = new CallOverallWebSocketMessage();
        callOverallWebSocketMessage.setType("message");

        CallWebSocketMessage callWebSocketMessage = new CallWebSocketMessage();

        ActorWebSocketMessage actorWebSocketMessage = new ActorWebSocketMessage();
        actorWebSocketMessage.setType("session");
        actorWebSocketMessage.setSessionId(ncMessageWrapper.getSignalingMessage().getTo());
        callWebSocketMessage.setRecipientWebSocketMessage(actorWebSocketMessage);
        callWebSocketMessage.setNcSignalingMessage(ncMessageWrapper.getSignalingMessage());

        callOverallWebSocketMessage.setCallWebSocketMessage(callWebSocketMessage);
        return callOverallWebSocketMessage;
    }
}
