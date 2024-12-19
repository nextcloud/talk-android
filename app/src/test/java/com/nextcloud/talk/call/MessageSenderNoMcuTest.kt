/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never

class MessageSenderNoMcuTest {

    private var peerConnectionWrappers: MutableList<PeerConnectionWrapper?>? = null
    private var peerConnectionWrapper1: PeerConnectionWrapper? = null
    private var peerConnectionWrapper2: PeerConnectionWrapper? = null
    private var peerConnectionWrapper2Screen: PeerConnectionWrapper? = null
    private var peerConnectionWrapper4Screen: PeerConnectionWrapper? = null

    private var messageSender: MessageSenderNoMcu? = null

    @Before
    fun setUp() {
        val signalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)

        val callParticipants = HashMap<String, CallParticipant>()

        peerConnectionWrappers = ArrayList()

        peerConnectionWrapper1 = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper1!!.sessionId).thenReturn("theSessionId1")
        Mockito.`when`(peerConnectionWrapper1!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(peerConnectionWrapper1)

        peerConnectionWrapper2 = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper2!!.sessionId).thenReturn("theSessionId2")
        Mockito.`when`(peerConnectionWrapper2!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(peerConnectionWrapper2)

        peerConnectionWrapper2Screen = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper2Screen!!.sessionId).thenReturn("theSessionId2")
        Mockito.`when`(peerConnectionWrapper2Screen!!.videoStreamType).thenReturn("screen")
        peerConnectionWrappers!!.add(peerConnectionWrapper2Screen)

        peerConnectionWrapper4Screen = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper4Screen!!.sessionId).thenReturn("theSessionId4")
        Mockito.`when`(peerConnectionWrapper4Screen!!.videoStreamType).thenReturn("screen")
        peerConnectionWrappers!!.add(peerConnectionWrapper4Screen)

        messageSender = MessageSenderNoMcu(signalingMessageSender, callParticipants.keys, peerConnectionWrappers)
    }

    @Test
    fun testSendDataChannelMessage() {
        val message = DataChannelMessage()
        messageSender!!.send(message, "theSessionId2")

        Mockito.verify(peerConnectionWrapper2!!).send(message)
        Mockito.verify(peerConnectionWrapper1!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2Screen!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper4Screen!!, never()).send(message)
    }

    @Test
    fun testSendDataChannelMessageIfScreenPeerConnection() {
        val message = DataChannelMessage()
        messageSender!!.send(message, "theSessionId4")

        Mockito.verify(peerConnectionWrapper1!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2Screen!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper4Screen!!, never()).send(message)
    }

    @Test
    fun testSendDataChannelMessageIfNoPeerConnection() {
        val message = DataChannelMessage()
        messageSender!!.send(message, "theSessionId3")

        Mockito.verify(peerConnectionWrapper1!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2Screen!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper4Screen!!, never()).send(message)
    }

    @Test
    fun testSendDataChannelMessageToAll() {
        val message = DataChannelMessage()
        messageSender!!.sendToAll(message)

        Mockito.verify(peerConnectionWrapper1!!).send(message)
        Mockito.verify(peerConnectionWrapper2!!).send(message)
        Mockito.verify(peerConnectionWrapper2Screen!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper4Screen!!, never()).send(message)
    }
}
