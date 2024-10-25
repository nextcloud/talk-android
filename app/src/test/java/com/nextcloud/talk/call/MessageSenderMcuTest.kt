/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never

class MessageSenderMcuTest {

    private var peerConnectionWrappers: MutableList<PeerConnectionWrapper?>? = null
    private var peerConnectionWrapper1: PeerConnectionWrapper? = null
    private var peerConnectionWrapper2: PeerConnectionWrapper? = null
    private var ownPeerConnectionWrapper: PeerConnectionWrapper? = null

    private var messageSender: MessageSenderMcu? = null

    @Before
    fun setUp() {
        peerConnectionWrappers = ArrayList()

        peerConnectionWrapper1 = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper1!!.sessionId).thenReturn("theSessionId1")
        Mockito.`when`(peerConnectionWrapper1!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(peerConnectionWrapper1)

        peerConnectionWrapper2 = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(peerConnectionWrapper2!!.sessionId).thenReturn("theSessionId2")
        Mockito.`when`(peerConnectionWrapper2!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(peerConnectionWrapper2)

        ownPeerConnectionWrapper = Mockito.mock(PeerConnectionWrapper::class.java)
        Mockito.`when`(ownPeerConnectionWrapper!!.sessionId).thenReturn("ownSessionId")
        Mockito.`when`(ownPeerConnectionWrapper!!.videoStreamType).thenReturn("video")
        peerConnectionWrappers!!.add(ownPeerConnectionWrapper)

        messageSender = MessageSenderMcu(peerConnectionWrappers, "ownSessionId")
    }

    @Test
    fun testSendDataChannelMessageToAll() {
        val message = DataChannelMessage()
        messageSender!!.sendToAll(message)

        Mockito.verify(ownPeerConnectionWrapper!!).send(message)
        Mockito.verify(peerConnectionWrapper1!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2!!, never()).send(message)
    }

    @Test
    fun testSendDataChannelMessageToAllWithoutOwnPeerConnection() {
        peerConnectionWrappers!!.remove(ownPeerConnectionWrapper)

        val message = DataChannelMessage()
        messageSender!!.sendToAll(message)

        Mockito.verify(ownPeerConnectionWrapper!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper1!!, never()).send(message)
        Mockito.verify(peerConnectionWrapper2!!, never()).send(message)
    }
}
