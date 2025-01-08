/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.times
import org.mockito.invocation.InvocationOnMock

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

    private var callParticipants: MutableMap<String, CallParticipant>? = null

    private var messageSender: MessageSender? = null

    @Before
    fun setUp() {
        signalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)

        callParticipants = HashMap()

        val callParticipant1: CallParticipant = Mockito.mock(CallParticipant::class.java)
        callParticipants!!["theSessionId1"] = callParticipant1

        val callParticipant2: CallParticipant = Mockito.mock(CallParticipant::class.java)
        callParticipants!!["theSessionId2"] = callParticipant2

        val callParticipant3: CallParticipant = Mockito.mock(CallParticipant::class.java)
        callParticipants!!["theSessionId3"] = callParticipant3

        val callParticipant4: CallParticipant = Mockito.mock(CallParticipant::class.java)
        callParticipants!!["theSessionId4"] = callParticipant4

        val peerConnectionWrappers = ArrayList<PeerConnectionWrapper>()

        messageSender = MessageSender(signalingMessageSender, callParticipants!!.keys, peerConnectionWrappers)
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
    fun testSendSignalingMessageToAll() {
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
    fun testSendSignalingMessageToAllWhenParticipantsWereUpdated() {
        val callParticipant5: CallParticipant = Mockito.mock(CallParticipant::class.java)
        callParticipants!!["theSessionId5"] = callParticipant5

        callParticipants!!.remove("theSessionId2")
        callParticipants!!.remove("theSessionId3")

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
