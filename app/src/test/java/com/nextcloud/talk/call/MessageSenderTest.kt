/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.activities.CallViewModel
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.times
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSenderTest {

    private class MessageSender(
        signalingMessageSender: SignalingMessageSender?,
        callParticipantSessionIds: Set<String>?,
        peerConnectionWrappers: List<PeerConnectionWrapper>?
    ) : com.nextcloud.talk.call.MessageSender(
        signalingMessageSender,
        callParticipantSessionIds,
        peerConnectionWrappers
    ) {

        override fun sendToAll(dataChannelMessage: DataChannelMessage?) {
            // Not used in base class tests
        }
    }

    private var signalingMessageSender: SignalingMessageSender? = null

    private lateinit var viewModel: CallViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var messageSender: MessageSender? = null

    val mockReceiver = mock<SignalingMessageReceiver>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CallViewModel()

        signalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)

        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = "theSessionId1",
            signalingMessageReceiver = mockReceiver
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = "theSessionId2",
            signalingMessageReceiver = mockReceiver
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = "theSessionId3",
            signalingMessageReceiver = mockReceiver
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = "theSessionId4",
            signalingMessageReceiver = mockReceiver
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val peerConnectionWrappers = ArrayList<PeerConnectionWrapper>()

        val sessionKeys = viewModel.participants.value
            .mapNotNull { it.sessionKey }
            .toSet()

        messageSender = MessageSender(signalingMessageSender, sessionKeys, peerConnectionWrappers)
    }

    @Test
    fun testSendSignalingMessage() {
        val message: NCSignalingMessage = Mockito.mock(NCSignalingMessage::class.java)
        messageSender!!.send(message, "theSessionId2")

        Mockito.verify(message).to = "theSessionId2"
        Mockito.verify(signalingMessageSender!!).send(message)
    }

    @Test
    fun testSendSignalingMessageIfUnknownSessionId() {
        val message: NCSignalingMessage = Mockito.mock(NCSignalingMessage::class.java)
        messageSender!!.send(message, "unknownSessionId")

        Mockito.verify(message).to = "unknownSessionId"
        Mockito.verify(signalingMessageSender!!).send(message)
    }

    @Test
    fun testSendSignalingMessageToAll() = testScope.runTest {
        val sentTo: MutableList<String?> = ArrayList()
        doAnswer { invocation: InvocationOnMock ->
            val arguments = invocation.arguments
            val message = (arguments[0] as NCSignalingMessage)

            sentTo.add(message.to)
            null
        }.`when`(signalingMessageSender!!).send(any())

        val message = NCSignalingMessage()
        messageSender!!.sendToAll(message)

        assertTrue(sentTo.contains("theSessionId1"))
        assertTrue(sentTo.contains("theSessionId2"))
        assertTrue(sentTo.contains("theSessionId3"))
        assertTrue(sentTo.contains("theSessionId4"))
        Mockito.verify(signalingMessageSender!!, times(4)).send(message)
        Mockito.verifyNoMoreInteractions(signalingMessageSender)
    }

    @Test
    fun testSendSignalingMessageToAllWhenParticipantsWereUpdated() = testScope.runTest {
        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = "theSessionId5",
            signalingMessageReceiver = mockReceiver
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.removeParticipant("theSessionId2")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.removeParticipant("theSessionId3")
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedSessionKeys = viewModel.participants.value
            .mapNotNull { it.sessionKey }
            .toSet()

        messageSender = MessageSender(signalingMessageSender, updatedSessionKeys, emptyList())


        val sentTo: MutableList<String?> = ArrayList()
        doAnswer { invocation: InvocationOnMock ->
            val arguments = invocation.arguments
            val message = (arguments[0] as NCSignalingMessage)

            sentTo.add(message.to)
            null
        }.`when`(signalingMessageSender!!).send(any())

        val message = NCSignalingMessage()
        messageSender!!.sendToAll(message)

        assertTrue(sentTo.contains("theSessionId1"))
        assertTrue(sentTo.contains("theSessionId4"))
        assertTrue(sentTo.contains("theSessionId5"))
        Mockito.verify(signalingMessageSender!!, times(3)).send(message)
        Mockito.verifyNoMoreInteractions(signalingMessageSender)
    }
}
