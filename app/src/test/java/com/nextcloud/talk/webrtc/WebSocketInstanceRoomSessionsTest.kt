/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.app.Application
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplicationComponent
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

private fun sessionOf(room: String) = "session-in-$room"

private fun joinFrame(session: String, user: String) =
    "{\"type\":\"event\",\"event\":{\"target\":\"room\",\"type\":\"join\",\"join\":" +
        "[{\"sessionid\":\"$session\",\"userid\":\"$user\",\"user\":{\"displayname\":\"$user\"}}]}}"

private fun errorFrame(code: String) = "{\"type\":\"error\",\"error\":{\"code\":\"$code\",\"message\":\"t\"}}"

/**
 * The sessions of the room the signaling connection is in, fed by the frames the signaling server sends. A frame
 * after the one under test serves as a barrier: the client handles frames in order, so when the barrier session is
 * known, everything before it has been handled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
@Suppress("TooManyFunctions")
class WebSocketInstanceRoomSessionsTest {

    private val server = MockWebServer()
    private val serverLog = CopyOnWriteArrayList<String>()
    private val serverSockets = CopyOnWriteArrayList<ServerListener>()
    private val eventBus = EventBus.builder().build()
    private val instances = CopyOnWriteArrayList<WebSocketInstance>()
    private val path = "/t-${UUID.randomUUID()}/"
    private val okHttpClient = OkHttpClient()
    private var previousApplication: NextcloudTalkApplication? = null

    // the setter of the singleton is protected: only the real application sets it
    private val sharedApplicationField = NextcloudTalkApplication::class.java
        .getDeclaredField("sharedApplication")
        .apply { isAccessible = true }

    @Volatile
    private var holdAnswerToLeave = false

    @Volatile
    private var holdAnswerToJoin = false

    @Before
    fun setUp() {
        previousApplication = sharedApplicationField.get(null) as NextcloudTalkApplication?
        sharedApplicationField.set(null, applicationInjecting(okHttpClient, eventBus))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath == path) {
                    MockResponse.Builder().webSocketUpgrade(ServerListener("c${serverSockets.size + 1}")).build()
                } else {
                    MockResponse(code = HTTP_NOT_FOUND)
                }
        }
        server.start()
    }

    @After
    fun tearDown() {
        instances.forEach { eventBus.unregister(it) }
        // close() of the server waits for open sockets
        serverSockets.forEach { it.close() }
        server.close()
        sharedApplicationField.set(null, previousApplication)
    }

    @Test
    fun switchingToAnotherRoomDropsSessionsOfThePreviousRoom() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        instance.joinRoomWithRoomTokenAndSession(ROOM_B, "room-session-b")

        waitUntil("sessions of room B") { instance.isSessionInRoom(sessionOf(ROOM_B), ROOM_B) }
        assertFalse(instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_B))
        assertEquals(listOf(sessionOf(ROOM_B)), instance.getRoomSessionIds())
    }

    @Test
    fun sessionOfThePreviousRoomIsNotTrustedForTheNextRoomBeforeItsJoinIsAnswered() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }
        // Switching rooms without leaving first (e.g. during a call): the sessions of room A stay until the answer.
        holdAnswerToJoin = true
        instance.joinRoomWithRoomTokenAndSession(ROOM_B, "room-session-b")
        waitUntil("join received") { serverLog.contains("c1:joined-unanswered") }
        sendBarrierJoin(instance)

        assertFalse(
            "typing message of room A is ignored by the conversation of room B",
            instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_B)
        )
        assertTrue(instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A))
    }

    @Test
    fun lateJoinOfTheLeftRoomIsNeitherTrustedNorAddressedWhenTheNextRoomIsEntered() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }
        // The server answers the leave only after it sent a join event of the room which is being left.
        holdAnswerToLeave = true
        instance.joinRoomWithRoomTokenAndSession("", "room-session-a")
        waitUntil("leave received") { serverLog.contains("c1:left-unanswered") }
        serverSockets.first().push(joinFrame(LATE_SESSION, "user-late"))
        awaitFramesHandled(instance)

        assertFalse("typing message of the late session is ignored", instance.isSessionInRoom(LATE_SESSION, ROOM_A))
        assertTrue("no typing message to the late session", instance.getRoomSessionIds().isEmpty())

        serverSockets.first().push("{\"type\":\"room\",\"room\":{\"roomid\":\"\"}}")
        instance.joinRoomWithRoomTokenAndSession(ROOM_B, "room-session-b")
        waitUntil("sessions of room B") { instance.isSessionInRoom(sessionOf(ROOM_B), ROOM_B) }

        assertFalse(instance.isSessionInRoom(LATE_SESSION, ROOM_B))
        assertFalse(instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_B))
        assertEquals(listOf(sessionOf(ROOM_B)), instance.getRoomSessionIds())
    }

    @Test
    fun leavingTheRoomDropsTheSessions() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        instance.joinRoomWithRoomTokenAndSession("", "room-session-a")

        waitUntil("room left") { serverLog.contains("c1:left") }
        awaitFramesHandled(instance)
        assertTrue(instance.getRoomSessionIds().isEmpty())
    }

    @Test
    fun resumedSessionKeepsTheSessions() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        instance.restartWebSocket()
        waitUntil("resumed hello answered") { serverLog.contains("c2:hello-answered") }
        waitUntil("connected again") { instance.isConnected }
        sendBarrierJoin(instance)

        assertTrue(instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A))
    }

    @Test
    fun newSessionWithoutResumeStartsWithoutSessions() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        instance.clearResumeId()
        instance.restartWebSocket()
        waitUntil("new session said hello") { serverLog.contains("c2:hello-answered") }
        waitUntil("connected again") { instance.isConnected }
        awaitFramesHandled(instance)

        assertFalse(serverLog.first { it.startsWith("c2:hello ") }.contains("resumeid"))
        assertTrue(instance.getRoomSessionIds().isEmpty())
    }

    @Test
    fun alreadyJoinedAnswerKeepsTheSessions() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-call")
        waitUntil("second join answered") { serverLog.count { it == "c1:already_joined" } == 1 }
        sendBarrierJoin(instance)

        assertTrue(instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A))
    }

    @Test
    fun expiredSessionDropsTheSessions() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        serverSockets.first().push(errorFrame("no_such_session"))
        waitUntil("new session said hello") { serverLog.contains("c2:hello-answered") }
        waitUntil("connected again") { instance.isConnected }
        awaitFramesHandled(instance)

        assertTrue(instance.getRoomSessionIds().isEmpty())
    }

    @Test
    fun rejectedRoomSessionDropsTheSessions() {
        val instance = createConnectedInstance()
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        serverSockets.first().push(errorFrame("no_such_room"))
        awaitFramesHandled(instance)

        assertTrue(instance.getRoomSessionIds().isEmpty())
    }

    @Test
    fun leaveEventRemovesTheSessionAndNotifiesTheListeners() {
        val instance = createConnectedInstance()
        val listener = mock<SignalingMessageReceiver.ConversationMessageListener>()
        instance.getSignalingMessageReceiver().addListener(listener)
        instance.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        waitUntil("sessions of room A") { instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }

        serverSockets.first().push(
            "{\"type\":\"event\",\"event\":{\"target\":\"room\",\"type\":\"leave\",\"leave\":" +
                "[\"${sessionOf(ROOM_A)}\"]}}"
        )
        sendBarrierJoin(instance)

        assertFalse(instance.isSessionInRoom(sessionOf(ROOM_A), ROOM_A))
        verify(listener).onSessionsLeft(listOf(sessionOf(ROOM_A)))
    }

    @Test
    fun accountsDoNotShareSessions() {
        val first = createConnectedInstance()
        val second = createConnectedInstance()
        first.joinRoomWithRoomTokenAndSession(ROOM_A, "room-session-a")
        second.joinRoomWithRoomTokenAndSession(ROOM_B, "room-session-b")
        waitUntil("sessions of room A") { first.isSessionInRoom(sessionOf(ROOM_A), ROOM_A) }
        waitUntil("sessions of room B") { second.isSessionInRoom(sessionOf(ROOM_B), ROOM_B) }

        assertEquals(listOf(sessionOf(ROOM_A)), first.getRoomSessionIds())
        assertEquals(listOf(sessionOf(ROOM_B)), second.getRoomSessionIds())
    }

    /**
     * A barrier for a connection which is in no room, where a join event is ignored: the leave event of an unknown
     * session reaches the listeners, so once it is seen the frames sent before it have been handled.
     */
    private fun awaitFramesHandled(instance: WebSocketInstance) {
        val listener = mock<SignalingMessageReceiver.ConversationMessageListener>()
        instance.getSignalingMessageReceiver().addListener(listener)
        serverSockets.last().push(
            "{\"type\":\"event\",\"event\":{\"target\":\"room\",\"type\":\"leave\",\"leave\":[\"$BARRIER\"]}}"
        )
        verify(listener, timeout(TIMEOUT_MILLIS)).onSessionsLeft(listOf(BARRIER))
    }

    private fun sendBarrierJoin(instance: WebSocketInstance) {
        serverSockets.last().push(joinFrame(BARRIER, "barrier-user"))
        waitUntil("barrier handled") { instance.getRoomSessionIds().contains(BARRIER) }
    }

    private fun createConnectedInstance(): WebSocketInstance {
        val user = User(
            userId = "alice",
            baseUrl = "https://cloud.example.com",
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto(listOf("signaling-v3"), null, "")
            }
        )
        val created = WebSocketInstance(user, server.url(path).toString(), "ticket")
        instances.add(created)
        waitUntil("session connected") { created.isConnected }
        return created
    }

    /**
     * The application of the production code injects its components via Dagger; the test hands them the client and
     * the event bus it controls instead.
     */
    private fun applicationInjecting(client: OkHttpClient, bus: EventBus): NextcloudTalkApplication {
        val component = mock<NextcloudTalkApplicationComponent>(
            defaultAnswer = { invocation ->
                when (val target = invocation.arguments.firstOrNull()) {
                    is WebSocketInstance -> {
                        target.okHttpClient = client
                        target.eventBus = bus
                    }

                    is WebSocketConnectionHelper -> target.okHttpClient = client
                }
                null
            }
        )
        val application = mock<NextcloudTalkApplication>()
        whenever(application.componentApplication).thenReturn(component)
        return application
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what, server log: $serverLog" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Behaves like the signaling server: a join of a room is answered with the "room" message, followed by the
     * join event of a session of that room; joining the same room again is an "already_joined" error.
     */
    private inner class ServerListener(private val name: String) : WebSocketListener() {
        private var webSocket: WebSocket? = null
        private var joinedRoom: String? = null

        init {
            serverSockets.add(this)
        }

        fun push(frame: String) {
            webSocket!!.send(frame)
        }

        fun close() {
            webSocket?.close(NORMAL_CLOSURE, null)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            this.webSocket = webSocket
            when {
                text.contains("\"type\":\"hello\"") -> {
                    serverLog.add("$name:hello $text")
                    webSocket.send(
                        "{\"type\":\"hello\",\"hello\":{\"resumeid\":\"resume-$name\",\"sessionid\":\"own-$name\"}}"
                    )
                    serverLog.add("$name:hello-answered")
                }

                text.contains("\"type\":\"room\"") -> {
                    val room = Regex("\"roomid\":\"([^\"]*)\"").find(text)!!.groupValues[1]
                    when {
                        room.isEmpty() && holdAnswerToLeave -> {
                            joinedRoom = null
                            serverLog.add("$name:left-unanswered")
                        }

                        room.isEmpty() -> {
                            joinedRoom = null
                            webSocket.send("{\"type\":\"room\",\"room\":{\"roomid\":\"\"}}")
                            serverLog.add("$name:left")
                        }

                        holdAnswerToJoin -> serverLog.add("$name:joined-unanswered")

                        room == joinedRoom -> {
                            webSocket.send(errorFrame("already_joined"))
                            serverLog.add("$name:already_joined")
                        }

                        else -> {
                            joinedRoom = room
                            webSocket.send(
                                "{\"type\":\"room\",\"room\":{\"roomid\":\"$room\",\"properties\":{\"name\":\"n\"}}}"
                            )
                            webSocket.send(joinFrame(sessionOf(room), "user-in-$room"))
                        }
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }
    }

    companion object {
        private const val ROOM_A = "roomA"
        private const val ROOM_B = "roomB"
        private const val BARRIER = "barrier-session"
        private const val LATE_SESSION = "late-session-of-room-a"
        private const val NORMAL_CLOSURE = 1000
        private const val HTTP_NOT_FOUND = 404
        private const val TIMEOUT_MILLIS = 10_000L
        private const val POLL_MILLIS = 20L
    }
}
