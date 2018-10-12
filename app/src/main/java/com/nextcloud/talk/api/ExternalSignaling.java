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

package com.nextcloud.talk.api;

import com.nextcloud.talk.models.json.websocket.CallOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.HelloOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.HelloResponseWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.RequestOfferOverallWebSocketMessage;
import com.nextcloud.talk.models.json.websocket.RoomOverallWebSocketMessage;
import com.tinder.scarlet.WebSocket;
import com.tinder.scarlet.ws.Receive;
import com.tinder.scarlet.ws.Send;

import io.reactivex.Flowable;

public interface ExternalSignaling {
    @Receive
    Flowable<WebSocket.Event.OnMessageReceived> observeOnMessageReceived();

    @Receive
    Flowable<WebSocket.Event.OnConnectionOpened> observeOnConnectionOpenedEvent();

    @Receive
    Flowable<WebSocket.Event.OnConnectionFailed> observeOnConnectionFailedEvent();

    @Receive
    Flowable<WebSocket.Event.OnConnectionClosed> observeOnConnectionClosedEvent();

    @Send
    void sendHello(HelloOverallWebSocketMessage helloOverallWebSocketMessage);

    @Send
    void sendResumeHello(RoomOverallWebSocketMessage roomOverallWebSocketMessage);

    @Send
    void sendOfferRequest(RequestOfferOverallWebSocketMessage requestOfferOverallWebSocketMessage);

    @Send
    void sendCallMessage(CallOverallWebSocketMessage callOverallWebSocketMessage);

    @Receive
    Flowable<HelloResponseWebSocketMessage> observeOnHelloBackEvent();
}
