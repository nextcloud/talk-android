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

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R.string
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.events.NetworkEvent
import com.nextcloud.talk.events.NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.signaling.NCMessageWrapper
import com.nextcloud.talk.models.json.websocket.*
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.LoggingUtils.writeLogEntryToFile
import com.nextcloud.talk.utils.MagicMap
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.BACKGROUND
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.IOException
import java.util.*

class MagicWebSocketInstance internal constructor(
        conversationUser: UserNgEntity,
        connectionUrl: String,
        webSocketTicket: String
) : WebSocketListener(), KoinComponent {
    val okHttpClient: OkHttpClient by inject()
    val eventBus: EventBus by inject()
    val context: Context by inject()

    private val conversationUser: UserNgEntity
    private val webSocketTicket: String
    private var resumeId: String? = null
    var sessionId: String? = null
        private set
    private var hasMCU = false
    var isConnected: Boolean
        private set
    private val webSocketConnectionHelper: WebSocketConnectionHelper
    private var internalWebSocket: WebSocket? = null
    private val magicMap: MagicMap
    private val connectionUrl: String
    private var currentRoomToken: String? = null
    private var restartCount = 0
    private var reconnecting = false
    private val usersHashMap: HashMap<String?, Participant>
    private var messagesQueue: MutableList<String> =
            ArrayList()

    private fun sendHello() {
        try {
            if (TextUtils.isEmpty(resumeId)) {
                internalWebSocket!!.send(
                        LoganSquare.serialize(
                                webSocketConnectionHelper.getAssembledHelloModel(conversationUser, webSocketTicket)
                        )
                )
            } else {
                internalWebSocket!!.send(
                        LoganSquare.serialize(
                                webSocketConnectionHelper.getAssembledHelloModelForResume(resumeId)
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to serialize hello model")
        }
    }

    override fun onOpen(
            webSocket: WebSocket,
            response: Response
    ) {
        internalWebSocket = webSocket
        sendHello()
    }

    private fun closeWebSocket(webSocket: WebSocket) {
        webSocket.close(1000, null)
        webSocket.cancel()
        if (webSocket === internalWebSocket) {
            isConnected = false
            messagesQueue = ArrayList()
        }
        restartWebSocket()
    }

    fun clearResumeId() {
        resumeId = ""
    }

    fun restartWebSocket() {
        reconnecting = true
        val request = Builder()
                .url(connectionUrl)
                .build()
        okHttpClient.newWebSocket(request, this)
        restartCount++
    }

    override fun onMessage(
            webSocket: WebSocket,
            text: String
    ) {
        if (webSocket === internalWebSocket) {
            Log.d(
                    TAG, "Receiving : $webSocket $text"
            )
            writeLogEntryToFile(
                    context,
                    "WebSocket " + webSocket.hashCode() + " receiving: " + text
            )
            try {
                val baseWebSocketMessage =
                        LoganSquare.parse(text, BaseWebSocketMessage::class.java)
                val messageType = baseWebSocketMessage.type
                when (messageType) {
                    "hello" -> {
                        isConnected = true
                        reconnecting = false
                        restartCount = 0
                        val oldResumeId = resumeId
                        val helloResponseWebSocketMessage =
                                LoganSquare.parse(
                                        text, HelloResponseOverallWebSocketMessage::class.java
                                )
                        resumeId = helloResponseWebSocketMessage.helloResponseWebSocketMessage
                                .resumeId
                        sessionId = helloResponseWebSocketMessage.helloResponseWebSocketMessage
                                .sessionId
                        hasMCU = helloResponseWebSocketMessage.helloResponseWebSocketMessage
                                .serverHasMCUSupport()
                        var i = 0
                        while (i < messagesQueue.size) {
                            webSocket.send(messagesQueue[i])
                            i++
                        }
                        messagesQueue = ArrayList()
                        val helloHasHap =
                                HashMap<String, String?>()
                        if (!TextUtils.isEmpty(oldResumeId)) {
                            helloHasHap["oldResumeId"] = oldResumeId
                        } else {
                            currentRoomToken = ""
                        }
                        if (!TextUtils.isEmpty(currentRoomToken)) {
                            helloHasHap["roomToken"] = currentRoomToken
                        }
                        eventBus.post(WebSocketCommunicationEvent("hello", helloHasHap))
                    }
                    "error" -> {
                        val errorOverallWebSocketMessage =
                                LoganSquare.parse(
                                        text, ErrorOverallWebSocketMessage::class.java
                                )
                        if ("no_such_session" ==
                                errorOverallWebSocketMessage.errorWebSocketMessage.code
                        ) {
                            writeLogEntryToFile(
                                    context,
                                    "WebSocket " + webSocket.hashCode() + " resumeID " + resumeId + " expired"
                            )
                            resumeId = ""
                            currentRoomToken = ""
                            restartWebSocket()
                        } else if ("hello_expected" ==
                                errorOverallWebSocketMessage.errorWebSocketMessage.code
                        ) {
                            restartWebSocket()
                        }
                    }
                    "room" -> {
                        val joinedRoomOverallWebSocketMessage =
                                LoganSquare.parse(
                                        text, JoinedRoomOverallWebSocketMessage::class.java
                                )
                        currentRoomToken = joinedRoomOverallWebSocketMessage.roomWebSocketMessage
                                .roomId
                        if (joinedRoomOverallWebSocketMessage.roomWebSocketMessage
                                        .roomPropertiesWebSocketMessage != null && !TextUtils.isEmpty(
                                        currentRoomToken
                                )
                        ) {
                            sendRoomJoinedEvent()
                        }
                    }
                    "event" -> {
                        val eventOverallWebSocketMessage =
                                LoganSquare.parse(
                                        text, EventOverallWebSocketMessage::class.java
                                )
                        if (eventOverallWebSocketMessage.eventMap != null) {
                            val target =
                                    eventOverallWebSocketMessage.eventMap["target"] as String?
                            when (target) {
                                "room" -> if (eventOverallWebSocketMessage.eventMap["type"] == "message"
                                ) {
                                    val messageHashMap =
                                            eventOverallWebSocketMessage.eventMap["message"] as Map<String, Any>?
                                    if (messageHashMap!!.containsKey("data")) {
                                        val dataHashMap =
                                                messageHashMap["data"] as Map<String, Any>?
                                        if (dataHashMap!!.containsKey("chat")) {
                                            val shouldRefreshChat: Boolean
                                            val chatMap =
                                                    dataHashMap["chat"] as Map<String, Any>?
                                            if (chatMap!!.containsKey("refresh")) {
                                                shouldRefreshChat = chatMap["refresh"] as Boolean
                                                if (shouldRefreshChat) {
                                                    val refreshChatHashMap =
                                                            HashMap<String, String?>()
                                                    refreshChatHashMap[KEY_ROOM_TOKEN] = messageHashMap["roomid"] as String?
                                                    refreshChatHashMap[KEY_INTERNAL_USER_ID] =
                                                            java.lang.Long.toString(conversationUser.id!!)
                                                    eventBus.post(
                                                            WebSocketCommunicationEvent("refreshChat", refreshChatHashMap)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (eventOverallWebSocketMessage.eventMap["type"]
                                        == "join"
                                ) {
                                    val joinEventMap =
                                            eventOverallWebSocketMessage.eventMap["join"] as List<HashMap<String, Any>>?
                                    var internalHashMap: HashMap<String, Any>
                                    var participant: Participant
                                    var i = 0
                                    while (i < joinEventMap!!.size) {
                                        internalHashMap = joinEventMap[i]
                                        val userMap =
                                                internalHashMap["user"] as HashMap<String, Any>?
                                        participant = Participant()
                                        participant.userId = internalHashMap["userid"] as String
                                        participant.displayName = userMap!!["displayname"] as String
                                        usersHashMap[internalHashMap["sessionid"] as String?] = participant
                                        i++
                                    }
                                }
                                "participants" -> if (eventOverallWebSocketMessage.eventMap["type"] == "update"
                                ) {
                                    val refreshChatHashMap =
                                            HashMap<String, String?>()
                                    val updateEventMap =
                                            eventOverallWebSocketMessage.eventMap["update"] as HashMap<String, Any>?
                                    refreshChatHashMap["roomToken"] = updateEventMap!!["roomid"] as String?
                                    refreshChatHashMap["jobId"] = Integer.toString(
                                            magicMap.add(
                                                    updateEventMap["users"]!!
                                            )
                                    )
                                    eventBus.post(
                                            WebSocketCommunicationEvent("participantsUpdate", refreshChatHashMap)
                                    )
                                }
                            }
                        }
                    }
                    "message" -> {
                        val callOverallWebSocketMessage =
                                LoganSquare.parse(
                                        text, CallOverallWebSocketMessage::class.java
                                )
                        val ncSignalingMessage =
                                callOverallWebSocketMessage.callWebSocketMessage
                                        .ncSignalingMessage
                        if (TextUtils.isEmpty(ncSignalingMessage.from)
                                && callOverallWebSocketMessage.callWebSocketMessage.senderWebSocketMessage
                                != null
                        ) {
                            ncSignalingMessage.from =
                                    callOverallWebSocketMessage.callWebSocketMessage
                                            .senderWebSocketMessage
                                            .sessionId

                        }
                        if (!TextUtils.isEmpty(ncSignalingMessage.from)) {
                            val messageHashMap =
                                    HashMap<String, String>()
                            messageHashMap["jobId"] = Integer.toString(magicMap.add(ncSignalingMessage))
                            eventBus.post(WebSocketCommunicationEvent("signalingMessage", messageHashMap))
                        }
                    }
                    "bye" -> {
                        isConnected = false
                        resumeId = ""
                    }
                    else -> {
                    }
                }
            } catch (e: IOException) {
                writeLogEntryToFile(
                        context,
                        "WebSocket " + webSocket.hashCode() + " IOException: " + e.message
                )
                Log.e(
                        TAG, "Failed to recognize WebSocket message"
                )
            }
        }
    }

    private fun sendRoomJoinedEvent() {
        val joinRoomHashMap =
                HashMap<String, String?>()
        joinRoomHashMap["roomToken"] = currentRoomToken
        eventBus.post(WebSocketCommunicationEvent("roomJoined", joinRoomHashMap))
    }

    override fun onMessage(
            webSocket: WebSocket,
            bytes: ByteString
    ) {
        Log.d(TAG, "Receiving bytes : " + bytes.hex())
    }

    override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String
    ) {
        Log.d(TAG, "Closing : $code / $reason")
        writeLogEntryToFile(
                context,
                "WebSocket " + webSocket.hashCode() + " Closing: " + reason
        )
    }

    override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
    ) {
        Log.d(TAG, "Error : " + t.message)
        writeLogEntryToFile(
                context,
                "WebSocket " + webSocket.hashCode() + " onFailure: " + t.message
        )
        closeWebSocket(webSocket)
    }

    fun hasMCU(): Boolean {
        return hasMCU
    }

    fun joinRoomWithRoomTokenAndSession(
            roomToken: String,
            normalBackendSession: String?
    ) {
        try {
            val message = LoganSquare.serialize(
                    webSocketConnectionHelper.getAssembledJoinOrLeaveRoomModel(
                            roomToken,
                            normalBackendSession
                    )
            )
            if (!isConnected || reconnecting) {
                messagesQueue.add(message)
            } else {
                if (roomToken == currentRoomToken) {
                    sendRoomJoinedEvent()
                } else {
                    internalWebSocket!!.send(message)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendCallMessage(ncMessageWrapper: NCMessageWrapper) {
        try {
            val message = LoganSquare.serialize(
                    webSocketConnectionHelper.getAssembledCallMessageModel(ncMessageWrapper)
            )
            if (!isConnected || reconnecting) {
                messagesQueue.add(message)
            } else {
                internalWebSocket!!.send(message)
            }
        } catch (e: IOException) {
            writeLogEntryToFile(
                    context,
                    "WebSocket sendCalLMessage: " + e.message + "\n" + ncMessageWrapper.toString()
            )
            Log.e(
                    TAG, "Failed to serialize signaling message"
            )
        }
    }

    fun getJobWithId(id: Int): Any? {
        val copyJob = magicMap[id]
        magicMap.remove(id)
        return copyJob
    }

    fun requestOfferForSessionIdWithType(
            sessionIdParam: String,
            roomType: String
    ) {
        try {
            val message = LoganSquare.serialize(
                    webSocketConnectionHelper.getAssembledRequestOfferModel(sessionIdParam, roomType)
            )
            if (!isConnected || reconnecting) {
                messagesQueue.add(message)
            } else {
                internalWebSocket!!.send(message)
            }
        } catch (e: IOException) {
            writeLogEntryToFile(
                    context,
                    "WebSocket requestOfferForSessionIdWithType: "
                            + e.message
                            + "\n"
                            + sessionIdParam
                            + " "
                            + roomType
            )
            Log.e(TAG, "Failed to offer request")
        }
    }

    fun sendBye() {
        if (isConnected) {
            try {
                val byeWebSocketMessage = ByeWebSocketMessage()
                byeWebSocketMessage.type = "bye"
                byeWebSocketMessage.bye = HashMap()
                internalWebSocket!!.send(LoganSquare.serialize(byeWebSocketMessage))
            } catch (e: IOException) {
                Log.e(TAG, "Failed to serialize bye message")
            }
        }
    }

    fun getDisplayNameForSession(session: String?): String {
        return if (usersHashMap.containsKey(session)) {
            usersHashMap[session]!!.displayName
        } else sharedApplication!!.getString(string.nc_nick_guest)
    }

    fun getSessionForUserId(userId: String): String? {
        for (session in usersHashMap.keys) {
            if (userId == usersHashMap[session]!!.userId) {
                return session
            }
        }
        return ""
    }

    fun getUserIdForSession(session: String?): String {
        return if (usersHashMap.containsKey(session)) {
            usersHashMap[session]!!.userId
        } else ""
    }

    @Subscribe(threadMode = BACKGROUND)
    fun onMessageEvent(networkEvent: NetworkEvent) {
        if ((networkEvent.networkConnectionEvent
                        == NETWORK_CONNECTED) && !isConnected
        ) {
            restartWebSocket()
        }
    }

    companion object {
        private const val TAG = "MagicWebSocketInstance"
    }

    init {
        this.connectionUrl = connectionUrl
        this.conversationUser = conversationUser
        this.webSocketTicket = webSocketTicket
        webSocketConnectionHelper = WebSocketConnectionHelper()
        usersHashMap =
                HashMap()
        magicMap = MagicMap()
        isConnected = false
        eventBus.register(this)
        restartWebSocket()
    }
}