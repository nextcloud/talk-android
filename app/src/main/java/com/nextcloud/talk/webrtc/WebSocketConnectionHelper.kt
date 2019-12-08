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
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.models.json.signaling.NCMessageWrapper
import com.nextcloud.talk.models.json.websocket.*
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

class WebSocketConnectionHelper: KoinComponent {
    val okHttpClient: OkHttpClient by inject()

    fun getAssembledHelloModel(userEntity: UserNgEntity, ticket: String?): HelloOverallWebSocketMessage {
        val helloOverallWebSocketMessage = HelloOverallWebSocketMessage()
        helloOverallWebSocketMessage.type = "hello"
        val helloWebSocketMessage = HelloWebSocketMessage()
        helloWebSocketMessage.version = "1.0"
        val authWebSocketMessage = AuthWebSocketMessage()
        authWebSocketMessage.url =
                ApiUtils.getUrlForExternalServerAuthBackend(userEntity.baseUrl)
        val authParametersWebSocketMessage = AuthParametersWebSocketMessage()
        authParametersWebSocketMessage.ticket = ticket
        authParametersWebSocketMessage.userid = userEntity.userId
        authWebSocketMessage.authParametersWebSocketMessage = authParametersWebSocketMessage
        helloWebSocketMessage.authWebSocketMessage = authWebSocketMessage
        helloOverallWebSocketMessage.helloWebSocketMessage = helloWebSocketMessage
        return helloOverallWebSocketMessage
    }

    fun getAssembledHelloModelForResume(resumeId: String?): HelloOverallWebSocketMessage {
        val helloOverallWebSocketMessage = HelloOverallWebSocketMessage()
        helloOverallWebSocketMessage.type = "hello"
        val helloWebSocketMessage = HelloWebSocketMessage()
        helloWebSocketMessage.version = "1.0"
        helloWebSocketMessage.resumeid = resumeId
        helloOverallWebSocketMessage.helloWebSocketMessage = helloWebSocketMessage
        return helloOverallWebSocketMessage
    }

    fun getAssembledJoinOrLeaveRoomModel(roomId: String?, sessionId: String?): RoomOverallWebSocketMessage {
        val roomOverallWebSocketMessage = RoomOverallWebSocketMessage()
        roomOverallWebSocketMessage.type = "room"
        val roomWebSocketMessage = RoomWebSocketMessage()
        roomWebSocketMessage.roomId = roomId
        roomWebSocketMessage.sessiondId = sessionId
        roomOverallWebSocketMessage.roomWebSocketMessage = roomWebSocketMessage
        return roomOverallWebSocketMessage
    }

    fun getAssembledRequestOfferModel(sessionId: String?,
                                      roomType: String?): RequestOfferOverallWebSocketMessage {
        val requestOfferOverallWebSocketMessage = RequestOfferOverallWebSocketMessage()
        requestOfferOverallWebSocketMessage.type = "message"
        val requestOfferSignalingMessage = RequestOfferSignalingMessage()
        val actorWebSocketMessage = ActorWebSocketMessage()
        actorWebSocketMessage.type = "session"
        actorWebSocketMessage.sessionId = sessionId
        requestOfferSignalingMessage.actorWebSocketMessage = actorWebSocketMessage
        val signalingDataWebSocketMessageForOffer = SignalingDataWebSocketMessageForOffer()
        signalingDataWebSocketMessageForOffer.roomType = roomType
        signalingDataWebSocketMessageForOffer.type = "requestoffer"
        requestOfferSignalingMessage.signalingDataWebSocketMessageForOffer =
                signalingDataWebSocketMessageForOffer
        requestOfferOverallWebSocketMessage.requestOfferOverallWebSocketMessage =
                requestOfferSignalingMessage
        return requestOfferOverallWebSocketMessage
    }

    fun getAssembledCallMessageModel(ncMessageWrapper: NCMessageWrapper): CallOverallWebSocketMessage {
        val callOverallWebSocketMessage = CallOverallWebSocketMessage()
        callOverallWebSocketMessage.type = "message"
        val callWebSocketMessage = CallWebSocketMessage()
        val actorWebSocketMessage = ActorWebSocketMessage()
        actorWebSocketMessage.type = "session"
        actorWebSocketMessage.sessionId = ncMessageWrapper.signalingMessage.to
        callWebSocketMessage.recipientWebSocketMessage = actorWebSocketMessage
        callWebSocketMessage.ncSignalingMessage = ncMessageWrapper.signalingMessage
        callOverallWebSocketMessage.callWebSocketMessage = callWebSocketMessage
        return callOverallWebSocketMessage
    }

    companion object {
        private val magicWebSocketInstanceMap: MutableMap<Long, MagicWebSocketInstance> = HashMap()
        @Synchronized
        fun getMagicWebSocketInstanceForUserId(
                userId: Long): MagicWebSocketInstance? {
            return if (userId != -1L && magicWebSocketInstanceMap.containsKey(userId)) {
                magicWebSocketInstanceMap[userId]
            } else null
        }

        @Synchronized
        fun getExternalSignalingInstanceForServer(
                url: String, userEntity: UserNgEntity, webSocketTicket: String?, isGuest: Boolean): MagicWebSocketInstance? {
            var generatedURL = url.replace("https://", "wss://").replace("http://", "ws://")
            generatedURL += if (generatedURL.endsWith("/")) {
                "spreed"
            } else {
                "/spreed"
            }
            val userId = if (isGuest) -1 else userEntity.id
            var magicWebSocketInstance: MagicWebSocketInstance
            if (userId != -1L && magicWebSocketInstanceMap.containsKey(userEntity.id)) {
                return magicWebSocketInstanceMap[userEntity.id] as MagicWebSocketInstance
            } else {
                if (userId == -1L) {
                    deleteExternalSignalingInstanceForUserEntity(userId)
                }
                magicWebSocketInstance = MagicWebSocketInstance(userEntity, generatedURL, webSocketTicket!!)
                magicWebSocketInstanceMap[userEntity.id!!] = magicWebSocketInstance
                return magicWebSocketInstance
            }
        }

        @JvmStatic
        @Synchronized
        fun deleteExternalSignalingInstanceForUserEntity(id: Long) {
            var magicWebSocketInstance: MagicWebSocketInstance
            if (magicWebSocketInstanceMap[id].also { magicWebSocketInstance = it!! } != null) {
                if (magicWebSocketInstance.isConnected) {
                    magicWebSocketInstance.sendBye()
                    magicWebSocketInstanceMap.remove(id)
                }
            }
        }
    }
}
