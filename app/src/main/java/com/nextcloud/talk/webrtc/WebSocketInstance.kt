/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.content.Context
import android.text.TextUtils
import android.util.Log
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.NetworkEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.models.json.signaling.settings.FederationSettings
import com.nextcloud.talk.models.json.websocket.BaseWebSocketMessage
import com.nextcloud.talk.models.json.websocket.ByeWebSocketMessage
import com.nextcloud.talk.models.json.websocket.CallOverallWebSocketMessage
import com.nextcloud.talk.models.json.websocket.CallWebSocketMessage
import com.nextcloud.talk.models.json.websocket.ErrorOverallWebSocketMessage
import com.nextcloud.talk.models.json.websocket.EventOverallWebSocketMessage
import com.nextcloud.talk.models.json.websocket.HelloResponseOverallWebSocketMessage
import com.nextcloud.talk.models.json.websocket.JoinedRoomOverallWebSocketMessage
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.utils.bundle.BundleKeys
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.IOException
import java.lang.Thread.sleep
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("TooManyFunctions")
class WebSocketInstance internal constructor(conversationUser: User, connectionUrl: String, webSocketTicket: String) :
    WebSocketListener() {
    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null

    @JvmField
    @Inject
    var eventBus: EventBus? = null

    @JvmField
    @Inject
    var context: Context? = null
    private val conversationUser: User
    private val webSocketTicket: String
    private var resumeId: String? = null
    var sessionId: String? = null
        private set
    private var hasMCU = false
    var isConnected: Boolean
        private set
    private val webSocketConnectionHelper: WebSocketConnectionHelper
    private var internalWebSocket: WebSocket? = null
    private val connectionUrl: String
    private var currentRoomToken: String? = null
    private var currentNormalBackendSession: String? = null
    private var currentFederation: FederationSettings? = null
    private var reconnecting = false
    private val usersHashMap: HashMap<String?, Participant>
    private var messagesQueue: MutableList<String> = ArrayList()
    private val signalingMessageReceiver = ExternalSignalingMessageReceiver()
    val signalingMessageSender = ExternalSignalingMessageSender()

    init {
        sharedApplication!!.componentApplication.inject(this)
        this.connectionUrl = connectionUrl
        this.conversationUser = conversationUser
        this.webSocketTicket = webSocketTicket
        webSocketConnectionHelper = WebSocketConnectionHelper()
        usersHashMap = HashMap()
        isConnected = false
        eventBus!!.register(this)
        restartWebSocket()
    }

    private fun sendHello() {
        try {
            if (TextUtils.isEmpty(resumeId)) {
                internalWebSocket!!.send(
                    LoganSquare.serialize(
                        webSocketConnectionHelper
                            .getAssembledHelloModel(conversationUser, webSocketTicket)
                    )
                )
            } else {
                internalWebSocket!!.send(
                    LoganSquare.serialize(
                        webSocketConnectionHelper
                            .getAssembledHelloModelForResume(resumeId)
                    )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to serialize hello model")
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Open webSocket")
        internalWebSocket = webSocket
        sendHello()
    }

    private fun closeWebSocket(webSocket: WebSocket) {
        webSocket.close(NORMAL_CLOSURE, null)
        webSocket.cancel()
        if (webSocket === internalWebSocket) {
            isConnected = false
            messagesQueue = ArrayList()
        }
        sleep(ONE_SECOND)
        restartWebSocket()
    }

    fun clearResumeId() {
        resumeId = ""
    }

    fun restartWebSocket() {
        reconnecting = true
        Log.d(TAG, "restartWebSocket: $connectionUrl")
        val request = Request.Builder().url(connectionUrl).build()
        okHttpClient!!.newWebSocket(request, this)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (webSocket === internalWebSocket) {
            Log.d(TAG, "Receiving : $webSocket $text")
            try {
                val (messageType) = LoganSquare.parse(text, BaseWebSocketMessage::class.java)
                if (messageType != null) {
                    when (messageType) {
                        "hello" -> processHelloMessage(webSocket, text)
                        "error" -> processErrorMessage(webSocket, text)
                        "room" -> processJoinedRoomMessage(text)
                        "event" -> processEventMessage(text)
                        "message" -> processMessage(text)
                        "bye" -> {
                            isConnected = false
                            resumeId = ""
                        }

                        else -> {}
                    }
                } else {
                    Log.e(TAG, "Received message with type: null")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to recognize WebSocket message", e)
            }
        }
    }

    @Throws(IOException::class)
    private fun processMessage(text: String) {
        val (_, callWebSocketMessage) = LoganSquare.parse(text, CallOverallWebSocketMessage::class.java)
        if (callWebSocketMessage != null) {
            val ncSignalingMessage = callWebSocketMessage.ncSignalingMessage

            if (ncSignalingMessage != null &&
                TextUtils.isEmpty(ncSignalingMessage.from) &&
                callWebSocketMessage.senderWebSocketMessage != null
            ) {
                ncSignalingMessage.from = callWebSocketMessage.senderWebSocketMessage!!.sessionId
            }

            signalingMessageReceiver.process(callWebSocketMessage)
        }
    }

    @Throws(IOException::class)
    private fun processEventMessage(text: String) {
        val eventOverallWebSocketMessage = LoganSquare.parse(text, EventOverallWebSocketMessage::class.java)
        if (eventOverallWebSocketMessage.eventMap != null) {
            val target = eventOverallWebSocketMessage.eventMap!!["target"] as String?
            if (target != null) {
                when (target) {
                    Globals.TARGET_ROOM -> {
                        if ("message" == eventOverallWebSocketMessage.eventMap!!["type"]) {
                            processRoomMessageMessage(eventOverallWebSocketMessage)
                        } else if ("join" == eventOverallWebSocketMessage.eventMap!!["type"]) {
                            processRoomJoinMessage(eventOverallWebSocketMessage)
                        } else if ("leave" == eventOverallWebSocketMessage.eventMap!!["type"]) {
                            processRoomLeaveMessage(eventOverallWebSocketMessage)
                        }
                        signalingMessageReceiver.process(eventOverallWebSocketMessage.eventMap)
                    }

                    Globals.TARGET_PARTICIPANTS ->
                        signalingMessageReceiver.process(eventOverallWebSocketMessage.eventMap)

                    else ->
                        Log.i(TAG, "Received unknown/ignored event target: $target")
                }
            } else {
                Log.w(TAG, "Received message with event target: null")
            }
        }
    }

    private fun processRoomMessageMessage(eventOverallWebSocketMessage: EventOverallWebSocketMessage) {
        val messageHashMap = eventOverallWebSocketMessage.eventMap?.get("message") as Map<*, *>?

        if (messageHashMap != null && messageHashMap.containsKey("data")) {
            val dataHashMap = messageHashMap["data"] as Map<*, *>?

            if (dataHashMap != null && dataHashMap.containsKey("chat")) {
                val chatMap = dataHashMap["chat"] as Map<*, *>?
                if (chatMap != null && chatMap.containsKey("refresh") && chatMap["refresh"] as Boolean) {
                    val refreshChatHashMap = HashMap<String, String?>()
                    refreshChatHashMap[BundleKeys.KEY_ROOM_TOKEN] = messageHashMap["roomid"] as String?
                    refreshChatHashMap[BundleKeys.KEY_INTERNAL_USER_ID] = (conversationUser.id!!).toString()
                    eventBus!!.post(WebSocketCommunicationEvent("refreshChat", refreshChatHashMap))
                }
            } else if (dataHashMap != null && dataHashMap.containsKey("recording")) {
                val recordingMap = dataHashMap["recording"] as Map<*, *>?
                if (recordingMap != null && recordingMap.containsKey("status")) {
                    val status = (recordingMap["status"] as Long?)!!.toInt()
                    Log.d(TAG, "status is $status")
                    val recordingHashMap = HashMap<String, String>()
                    recordingHashMap[BundleKeys.KEY_RECORDING_STATE] = status.toString()
                    eventBus!!.post(WebSocketCommunicationEvent("recordingStatus", recordingHashMap))
                }
            }
        }
    }

    private fun processRoomJoinMessage(eventOverallWebSocketMessage: EventOverallWebSocketMessage) {
        val joinEventList = eventOverallWebSocketMessage.eventMap?.get("join") as List<HashMap<String, Any>>?
        var internalHashMap: HashMap<String, Any>
        var participant: Participant
        for (i in joinEventList!!.indices) {
            internalHashMap = joinEventList[i]
            val userMap = internalHashMap["user"] as HashMap<String, Any>?
            participant = Participant()
            val userId = internalHashMap["userid"] as String?
            if (userId != null) {
                participant.actorType = ActorType.USERS
                participant.actorId = userId
            } else {
                participant.actorType = ActorType.GUESTS
                // FIXME seems to be not given by the HPB: participant.setActorId();
            }
            if (userMap != null) {
                // There is no "user" attribute for guest participants.
                participant.displayName = userMap["displayname"] as String?
            }
            usersHashMap[internalHashMap["sessionid"] as String?] = participant
        }
    }

    private fun processRoomLeaveMessage(eventOverallWebSocketMessage: EventOverallWebSocketMessage) {
        val leaveEventList = eventOverallWebSocketMessage.eventMap?.get("leave") as List<String>?
        for (i in leaveEventList!!.indices) {
            usersHashMap.remove(leaveEventList[i])
        }
    }

    fun getUserMap(): HashMap<String?, Participant> = usersHashMap

    @Throws(IOException::class)
    private fun processJoinedRoomMessage(text: String) {
        val (_, roomWebSocketMessage) = LoganSquare.parse(text, JoinedRoomOverallWebSocketMessage::class.java)
        if (roomWebSocketMessage != null) {
            currentRoomToken = roomWebSocketMessage.roomId
            if (roomWebSocketMessage.roomPropertiesWebSocketMessage != null && !TextUtils.isEmpty(currentRoomToken)) {
                sendRoomJoinedEvent()
            }
        }
    }

    @Throws(IOException::class)
    private fun processErrorMessage(webSocket: WebSocket, text: String) {
        Log.e(TAG, "Received error: $text")
        val (_, message) = LoganSquare.parse(text, ErrorOverallWebSocketMessage::class.java)
        if (message != null) {
            if ("no_such_session" == message.code) {
                Log.d(TAG, "WebSocket " + webSocket.hashCode() + " resumeID " + resumeId + " expired")
                resumeId = ""
                currentRoomToken = ""
                currentNormalBackendSession = ""
                restartWebSocket()
            } else if ("hello_expected" == message.code) {
                restartWebSocket()
            }
        }
    }

    @Throws(IOException::class)
    private fun processHelloMessage(webSocket: WebSocket, text: String) {
        isConnected = true
        reconnecting = false
        val oldResumeId = resumeId
        val (_, helloResponseWebSocketMessage1) = LoganSquare.parse(
            text,
            HelloResponseOverallWebSocketMessage::class.java
        )
        if (helloResponseWebSocketMessage1 != null) {
            resumeId = helloResponseWebSocketMessage1.resumeId
            sessionId = helloResponseWebSocketMessage1.sessionId
            hasMCU = helloResponseWebSocketMessage1.serverHasMCUSupport()
        }
        for (i in messagesQueue.indices) {
            webSocket.send(messagesQueue[i])
        }
        messagesQueue = ArrayList()
        val helloHashMap = HashMap<String, String?>()
        if (!TextUtils.isEmpty(oldResumeId)) {
            helloHashMap["oldResumeId"] = oldResumeId
        } else {
            currentRoomToken = ""
            currentNormalBackendSession = ""
        }
        if (!TextUtils.isEmpty(currentRoomToken)) {
            helloHashMap[Globals.ROOM_TOKEN] = currentRoomToken
        }
        eventBus!!.post(WebSocketCommunicationEvent("hello", helloHashMap))
    }

    private fun sendRoomJoinedEvent() {
        val joinRoomHashMap = HashMap<String, String?>()
        joinRoomHashMap[Globals.ROOM_TOKEN] = currentRoomToken
        eventBus!!.post(WebSocketCommunicationEvent("roomJoined", joinRoomHashMap))
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "Receiving bytes : " + bytes.hex())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "onClosing : $code / $reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "onClosed : $code / $reason")
        isConnected = false
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Error : WebSocket " + webSocket.hashCode(), t)
        closeWebSocket(webSocket)
    }

    fun hasMCU(): Boolean = hasMCU

    @Suppress("Detekt.ComplexMethod")
    fun joinRoomWithRoomTokenAndSession(
        roomToken: String,
        normalBackendSession: String?,
        federation: FederationSettings? = null
    ) {
        Log.d(TAG, "joinRoomWithRoomTokenAndSession")
        Log.d(TAG, "   roomToken: $roomToken")
        Log.d(TAG, "   session: $normalBackendSession")
        try {
            val message = LoganSquare.serialize(
                webSocketConnectionHelper.getAssembledJoinOrLeaveRoomModel(roomToken, normalBackendSession, federation)
            )
            if (roomToken == "") {
                Log.d(TAG, "sending 'leave room' via websocket")
                currentNormalBackendSession = ""
                currentFederation = null
                sendMessage(message)
            } else if (
                roomToken == currentRoomToken &&
                normalBackendSession == currentNormalBackendSession &&
                federation?.roomId == currentFederation?.roomId &&
                federation?.nextcloudServer == currentFederation?.nextcloudServer
            ) {
                Log.d(TAG, "roomToken & session are unchanged. Joining locally without to send websocket message")
                sendRoomJoinedEvent()
            } else {
                Log.d(TAG, "Sending join room message via websocket")
                currentNormalBackendSession = normalBackendSession
                currentFederation = federation
                sendMessage(message)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to serialize signaling message", e)
        }
    }

    private fun sendCallMessage(ncSignalingMessage: NCSignalingMessage) {
        try {
            val message = LoganSquare.serialize(
                webSocketConnectionHelper.getAssembledCallMessageModel(ncSignalingMessage)
            )
            sendMessage(message)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to serialize signaling message", e)
        }
    }

    private fun sendMessage(message: String) {
        if (!isConnected || reconnecting) {
            messagesQueue.add(message)

            if (!reconnecting) {
                restartWebSocket()
            }
        } else {
            if (!internalWebSocket!!.send(message)) {
                messagesQueue.add(message)
                restartWebSocket()
            }
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

    fun getDisplayNameForSession(session: String?): String? {
        val participant = usersHashMap[session]
        if (participant != null) {
            if (participant.displayName != null) {
                return participant.displayName
            }
        }
        return ""
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(networkEvent: NetworkEvent) {
        if (networkEvent.networkConnectionEvent == NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED &&
            !isConnected
        ) {
            restartWebSocket()
        }
    }

    fun getSignalingMessageReceiver(): SignalingMessageReceiver = signalingMessageReceiver

    /**
     * Temporary implementation of SignalingMessageReceiver until signaling related code is extracted to a Signaling
     * class.
     *
     *
     * All listeners are called in the WebSocket reader thread. This thread should be the same as long as the WebSocket
     * stays connected, but it may change whenever it is connected again.
     */
    private class ExternalSignalingMessageReceiver : SignalingMessageReceiver() {
        fun process(eventMap: Map<String, Any?>?) {
            processEvent(eventMap)
        }

        fun process(message: CallWebSocketMessage?) {
            if (message?.ncSignalingMessage?.type == "startedTyping" ||
                message?.ncSignalingMessage?.type == "stoppedTyping"
            ) {
                processCallWebSocketMessage(message)
            } else {
                processSignalingMessage(message?.ncSignalingMessage)
            }
        }
    }

    inner class ExternalSignalingMessageSender : SignalingMessageSender {
        override fun send(ncSignalingMessage: NCSignalingMessage) {
            sendCallMessage(ncSignalingMessage)
        }
    }

    companion object {
        private const val TAG = "WebSocketInstance"
        private const val NORMAL_CLOSURE = 1000
        private const val ONE_SECOND: Long = 1000
    }
}
