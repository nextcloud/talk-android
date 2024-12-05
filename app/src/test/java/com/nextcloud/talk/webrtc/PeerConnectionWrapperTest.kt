/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.DataChannelMessageListener
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.doNothing
import org.webrtc.DataChannel
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import java.nio.ByteBuffer
import java.util.HashMap

class PeerConnectionWrapperTest {

    private var peerConnectionWrapper: PeerConnectionWrapper? = null
    private var mockedPeerConnection: PeerConnection? = null
    private var mockedPeerConnectionFactory: PeerConnectionFactory? = null
    private var mockedSignalingMessageReceiver: SignalingMessageReceiver? = null
    private var mockedSignalingMessageSender: SignalingMessageSender? = null

    private fun dataChannelMessageToBuffer(dataChannelMessage: DataChannelMessage): DataChannel.Buffer {
        return DataChannel.Buffer(
            ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).toByteArray()),
            false
        )
    }

    @Before
    fun setUp() {
        mockedPeerConnection = Mockito.mock(PeerConnection::class.java)
        mockedPeerConnectionFactory = Mockito.mock(PeerConnectionFactory::class.java)
        mockedSignalingMessageReceiver = Mockito.mock(SignalingMessageReceiver::class.java)
        mockedSignalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)
    }

    @Test
    fun testReceiveDataChannelMessage() {
        Mockito.`when`(
            mockedPeerConnectionFactory!!.createPeerConnection(
                any(PeerConnection.RTCConfiguration::class.java),
                any(PeerConnection.Observer::class.java)
            )
        ).thenReturn(mockedPeerConnection)

        val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
        Mockito.`when`(mockedStatusDataChannel.state()).thenReturn(DataChannel.State.OPEN)
        Mockito.`when`(mockedPeerConnection!!.createDataChannel(eq("status"), any()))
            .thenReturn(mockedStatusDataChannel)

        val statusDataChannelObserverArgumentCaptor: ArgumentCaptor<DataChannel.Observer> =
            ArgumentCaptor.forClass(DataChannel.Observer::class.java)

        doNothing().`when`(mockedStatusDataChannel).registerObserver(statusDataChannelObserverArgumentCaptor.capture())

        peerConnectionWrapper = PeerConnectionWrapper(
            mockedPeerConnectionFactory,
            ArrayList<PeerConnection.IceServer>(),
            MediaConstraints(),
            "the-session-id",
            "the-local-session-id",
            null,
            true,
            true,
            "video",
            mockedSignalingMessageReceiver,
            mockedSignalingMessageSender
        )

        val mockedDataChannelMessageListener = Mockito.mock(DataChannelMessageListener::class.java)
        peerConnectionWrapper!!.addListener(mockedDataChannelMessageListener)

        // The payload must be a map to be able to serialize it and, therefore, generate the data that would have been
        // received from another participant, so it is not possible to test receiving the nick as a String payload.
        val payloadMap = HashMap<String, String>()
        payloadMap["name"] = "the-nick-in-map"

        statusDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("nickChanged", null, payloadMap))
        )

        Mockito.verify(mockedDataChannelMessageListener).onNickChanged("the-nick-in-map")
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)

        statusDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("audioOn"))
        )

        Mockito.verify(mockedDataChannelMessageListener).onAudioOn()
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)

        statusDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("audioOff"))
        )

        Mockito.verify(mockedDataChannelMessageListener).onAudioOff()
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)

        statusDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("videoOn"))
        )

        Mockito.verify(mockedDataChannelMessageListener).onVideoOn()
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)

        statusDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("videoOff"))
        )

        Mockito.verify(mockedDataChannelMessageListener).onVideoOff()
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)
    }

    @Test
    fun testReceiveDataChannelMessageWithOpenRemoteDataChannel() {
        val peerConnectionObserverArgumentCaptor: ArgumentCaptor<PeerConnection.Observer> =
            ArgumentCaptor.forClass(PeerConnection.Observer::class.java)

        Mockito.`when`(
            mockedPeerConnectionFactory!!.createPeerConnection(
                any(PeerConnection.RTCConfiguration::class.java),
                peerConnectionObserverArgumentCaptor.capture()
            )
        ).thenReturn(mockedPeerConnection)

        val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
        Mockito.`when`(mockedStatusDataChannel.state()).thenReturn(DataChannel.State.OPEN)
        Mockito.`when`(mockedPeerConnection!!.createDataChannel(eq("status"), any()))
            .thenReturn(mockedStatusDataChannel)

        val statusDataChannelObserverArgumentCaptor: ArgumentCaptor<DataChannel.Observer> =
            ArgumentCaptor.forClass(DataChannel.Observer::class.java)

        doNothing().`when`(mockedStatusDataChannel).registerObserver(statusDataChannelObserverArgumentCaptor.capture())

        peerConnectionWrapper = PeerConnectionWrapper(
            mockedPeerConnectionFactory,
            ArrayList<PeerConnection.IceServer>(),
            MediaConstraints(),
            "the-session-id",
            "the-local-session-id",
            null,
            true,
            true,
            "video",
            mockedSignalingMessageReceiver,
            mockedSignalingMessageSender
        )

        val randomIdDataChannelObserverArgumentCaptor: ArgumentCaptor<DataChannel.Observer> =
            ArgumentCaptor.forClass(DataChannel.Observer::class.java)

        val mockedRandomIdDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedRandomIdDataChannel.label()).thenReturn("random-id")
        Mockito.`when`(mockedRandomIdDataChannel.state()).thenReturn(DataChannel.State.OPEN)
        doNothing().`when`(mockedRandomIdDataChannel).registerObserver(
            randomIdDataChannelObserverArgumentCaptor.capture()
        )
        peerConnectionObserverArgumentCaptor.value.onDataChannel(mockedRandomIdDataChannel)

        val mockedDataChannelMessageListener = Mockito.mock(DataChannelMessageListener::class.java)
        peerConnectionWrapper!!.addListener(mockedDataChannelMessageListener)

        statusDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("audioOn"))
        )

        Mockito.verify(mockedDataChannelMessageListener).onAudioOn()
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)

        randomIdDataChannelObserverArgumentCaptor.value.onMessage(
            dataChannelMessageToBuffer(DataChannelMessage("audioOff"))
        )

        Mockito.verify(mockedDataChannelMessageListener).onAudioOff()
        Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)
    }
}
