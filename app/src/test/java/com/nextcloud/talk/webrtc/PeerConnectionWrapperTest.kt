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
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.atMostOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.webrtc.DataChannel
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import java.nio.ByteBuffer
import java.util.HashMap
import kotlin.concurrent.thread

@Suppress("LongMethod", "TooGenericExceptionCaught")
class PeerConnectionWrapperTest {

    private var peerConnectionWrapper: PeerConnectionWrapper? = null
    private var mockedPeerConnection: PeerConnection? = null
    private var mockedPeerConnectionFactory: PeerConnectionFactory? = null
    private var mockedSignalingMessageReceiver: SignalingMessageReceiver? = null
    private var mockedSignalingMessageSender: SignalingMessageSender? = null

    /**
     * Helper answer for DataChannel methods.
     */
    private class ReturnValueOrThrowIfDisposed<T>(val value: T) : Answer<T> {
        override fun answer(currentInvocation: InvocationOnMock): T {
            if (Mockito.mockingDetails(currentInvocation.mock).invocations.find {
                    it!!.method.name === "dispose"
                } !== null
            ) {
                throw IllegalStateException("DataChannel has been disposed")
            }

            return value
        }
    }

    /**
     * Helper matcher for DataChannelMessages.
     */
    private inner class MatchesDataChannelMessage(private val expectedDataChannelMessage: DataChannelMessage) :
        ArgumentMatcher<DataChannel.Buffer> {
        override fun matches(buffer: DataChannel.Buffer): Boolean {
            // DataChannel.Buffer does not implement "equals", so the comparison needs to be done on the ByteBuffer
            // instead.
            return dataChannelMessageToBuffer(expectedDataChannelMessage).data.equals(buffer.data)
        }
    }

    private fun dataChannelMessageToBuffer(dataChannelMessage: DataChannelMessage) =
        DataChannel.Buffer(
            ByteBuffer.wrap(LoganSquare.serialize(dataChannelMessage).toByteArray()),
            false
        )

    @Before
    fun setUp() {
        mockedPeerConnection = Mockito.mock(PeerConnection::class.java)
        mockedPeerConnectionFactory = Mockito.mock(PeerConnectionFactory::class.java)
        mockedSignalingMessageReceiver = Mockito.mock(SignalingMessageReceiver::class.java)
        mockedSignalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)
    }

    @Test
    fun testSendDataChannelMessage() {
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

        peerConnectionWrapper!!.send(DataChannelMessage("the-message-type"))

        Mockito.verify(mockedStatusDataChannel).send(
            argThat(MatchesDataChannelMessage(DataChannelMessage("the-message-type")))
        )
    }

    @Test
    fun testSendDataChannelMessageWithOpenRemoteDataChannel() {
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

        val mockedRandomIdDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedRandomIdDataChannel.label()).thenReturn("random-id")
        Mockito.`when`(mockedRandomIdDataChannel.state()).thenReturn(DataChannel.State.OPEN)
        peerConnectionObserverArgumentCaptor.value.onDataChannel(mockedRandomIdDataChannel)

        peerConnectionWrapper!!.send(DataChannelMessage("the-message-type"))

        Mockito.verify(mockedStatusDataChannel).send(
            argThat(MatchesDataChannelMessage(DataChannelMessage("the-message-type")))
        )
        Mockito.verify(mockedRandomIdDataChannel, never()).send(any())
    }

    @Test
    fun testSendDataChannelMessageBeforeOpeningDataChannel() {
        Mockito.`when`(
            mockedPeerConnectionFactory!!.createPeerConnection(
                any(PeerConnection.RTCConfiguration::class.java),
                any(PeerConnection.Observer::class.java)
            )
        ).thenReturn(mockedPeerConnection)

        val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
        Mockito.`when`(mockedStatusDataChannel.state()).thenReturn(DataChannel.State.CONNECTING)
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

        peerConnectionWrapper!!.send(DataChannelMessage("the-message-type"))
        peerConnectionWrapper!!.send(DataChannelMessage("another-message-type"))

        Mockito.verify(mockedStatusDataChannel, never()).send(any())

        Mockito.`when`(mockedStatusDataChannel.state()).thenReturn(DataChannel.State.OPEN)
        statusDataChannelObserverArgumentCaptor.value.onStateChange()

        Mockito.verify(mockedStatusDataChannel).send(
            argThat(MatchesDataChannelMessage(DataChannelMessage("the-message-type")))
        )
        Mockito.verify(mockedStatusDataChannel).send(
            argThat(MatchesDataChannelMessage(DataChannelMessage("another-message-type")))
        )
    }

    @Test
    fun testSendDataChannelMessageBeforeOpeningDataChannelWithDifferentThreads() {
        // A brute force approach is used to test race conditions between different threads just repeating the test
        // several times. Due to this the test passing could be a false positive, as it could have been a matter of
        // luck, but even if the test may wrongly pass sometimes it is better than nothing (although, in general, with
        // that number of reruns, it fails when it should).
        for (i in 1..1000) {
            Mockito.`when`(
                mockedPeerConnectionFactory!!.createPeerConnection(
                    any(PeerConnection.RTCConfiguration::class.java),
                    any(PeerConnection.Observer::class.java)
                )
            ).thenReturn(mockedPeerConnection)

            val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
            Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
            Mockito.`when`(mockedStatusDataChannel.state()).thenReturn(DataChannel.State.CONNECTING)
            Mockito.`when`(mockedPeerConnection!!.createDataChannel(eq("status"), any()))
                .thenReturn(mockedStatusDataChannel)

            val statusDataChannelObserverArgumentCaptor: ArgumentCaptor<DataChannel.Observer> =
                ArgumentCaptor.forClass(DataChannel.Observer::class.java)

            doNothing().`when`(mockedStatusDataChannel)
                .registerObserver(statusDataChannelObserverArgumentCaptor.capture())

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

            val dataChannelMessageCount = 5

            val sendThread = thread {
                for (j in 1..dataChannelMessageCount) {
                    peerConnectionWrapper!!.send(DataChannelMessage("the-message-type-$j"))
                }
            }

            // Exceptions thrown in threads are not propagated to the main thread, so it needs to be explicitly done
            // (for example, for ConcurrentModificationExceptions when iterating over the data channel messages).
            var exceptionOnStateChange: Exception? = null

            val openDataChannelThread = thread {
                Mockito.`when`(mockedStatusDataChannel.state()).thenReturn(DataChannel.State.OPEN)

                try {
                    statusDataChannelObserverArgumentCaptor.value.onStateChange()
                } catch (e: Exception) {
                    exceptionOnStateChange = e
                }
            }

            sendThread.join()
            openDataChannelThread.join()

            if (exceptionOnStateChange !== null) {
                throw exceptionOnStateChange!!
            }

            val inOrder = inOrder(mockedStatusDataChannel)

            for (j in 1..dataChannelMessageCount) {
                inOrder.verify(mockedStatusDataChannel).send(
                    argThat(MatchesDataChannelMessage(DataChannelMessage("the-message-type-$j")))
                )
            }
        }
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

    @Test
    fun testRemovePeerConnectionWithOpenRemoteDataChannel() {
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

        val mockedRandomIdDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedRandomIdDataChannel.label()).thenReturn("random-id")
        Mockito.`when`(mockedRandomIdDataChannel.state()).thenReturn(DataChannel.State.OPEN)
        peerConnectionObserverArgumentCaptor.value.onDataChannel(mockedRandomIdDataChannel)

        peerConnectionWrapper!!.removePeerConnection()

        Mockito.verify(mockedStatusDataChannel).dispose()
        Mockito.verify(mockedRandomIdDataChannel).dispose()
    }

    @Test
    fun testRemovePeerConnectionWhileAddingRemoteDataChannelsWithDifferentThreads() {
        // A brute force approach is used to test race conditions between different threads just repeating the test
        // several times. Due to this the test passing could be a false positive, as it could have been a matter of
        // luck, but even if the test may wrongly pass sometimes it is better than nothing (although, in general, with
        // that number of reruns, it fails when it should).
        for (i in 1..1000) {
            val peerConnectionObserverArgumentCaptor: ArgumentCaptor<PeerConnection.Observer> =
                ArgumentCaptor.forClass(PeerConnection.Observer::class.java)

            Mockito.`when`(
                mockedPeerConnectionFactory!!.createPeerConnection(
                    any(PeerConnection.RTCConfiguration::class.java),
                    peerConnectionObserverArgumentCaptor.capture()
                )
            ).thenReturn(mockedPeerConnection)

            val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
            Mockito.`when`(mockedStatusDataChannel.label()).thenAnswer(ReturnValueOrThrowIfDisposed("status"))
            Mockito.`when`(mockedStatusDataChannel.state()).thenAnswer(
                ReturnValueOrThrowIfDisposed(DataChannel.State.OPEN)
            )
            Mockito.`when`(mockedPeerConnection!!.createDataChannel(eq("status"), any()))
                .thenReturn(mockedStatusDataChannel)

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

            val dataChannelCount = 5

            val mockedRandomIdDataChannels: MutableList<DataChannel> = ArrayList()
            val dataChannelObservers: MutableList<DataChannel.Observer?> = ArrayList()
            for (j in 0..<dataChannelCount) {
                mockedRandomIdDataChannels.add(Mockito.mock(DataChannel::class.java))
                // Add data channels with duplicated labels (from the second data channel and onwards) to test that
                // they are correctly disposed also in that case (which should not happen anyway, but just in case).
                Mockito.`when`(mockedRandomIdDataChannels[j].label())
                    .thenAnswer(ReturnValueOrThrowIfDisposed("random-id-" + ((j + 1) / 2)))
                Mockito.`when`(mockedRandomIdDataChannels[j].state())
                    .thenAnswer(ReturnValueOrThrowIfDisposed(DataChannel.State.OPEN))

                // Store a reference to the registered observer, if any, to be called after the registration. The call
                // is done outside the mock to better simulate the normal behaviour, as it would not be called during
                // the registration itself.
                dataChannelObservers.add(null)
                doAnswer { invocation ->
                    if (Mockito.mockingDetails(invocation.mock).invocations.find {
                            it!!.method.name === "dispose"
                        } !== null
                    ) {
                        throw IllegalStateException("DataChannel has been disposed")
                    }

                    dataChannelObservers[j] = invocation.getArgument(0, DataChannel.Observer::class.java)

                    null
                }.`when`(mockedRandomIdDataChannels[j]).registerObserver(any())
            }

            val onDataChannelThread = thread {
                // Add again "status" data channel to test that it is correctly disposed also in that case (which
                // should not happen anyway even if it was added by the remote peer, but just in case)
                peerConnectionObserverArgumentCaptor.value.onDataChannel(mockedStatusDataChannel)

                for (j in 0..<dataChannelCount) {
                    peerConnectionObserverArgumentCaptor.value.onDataChannel(mockedRandomIdDataChannels[j])

                    // Call "onStateChange" on the registered observer to simulate that the data channel was opened.
                    dataChannelObservers[j]?.onStateChange()
                }
            }

            // Exceptions thrown in threads are not propagated to the main thread, so it needs to be explicitly done
            // (for example, for ConcurrentModificationExceptions when iterating over the data channels).
            var exceptionRemovePeerConnection: Exception? = null

            val removePeerConnectionThread = thread {
                try {
                    peerConnectionWrapper!!.removePeerConnection()
                } catch (e: Exception) {
                    exceptionRemovePeerConnection = e
                }
            }

            onDataChannelThread.join()
            removePeerConnectionThread.join()

            if (exceptionRemovePeerConnection !== null) {
                throw exceptionRemovePeerConnection!!
            }

            Mockito.verify(mockedStatusDataChannel).dispose()
            for (j in 0..<dataChannelCount) {
                Mockito.verify(mockedRandomIdDataChannels[j]).dispose()
            }
        }
    }

    @Test
    fun testRemovePeerConnectionWhileSendingWithDifferentThreads() {
        // A brute force approach is used to test race conditions between different threads just repeating the test
        // several times. Due to this the test passing could be a false positive, as it could have been a matter of
        // luck, but even if the test may wrongly pass sometimes it is better than nothing (although, in general, with
        // that number of reruns, it fails when it should).
        for (i in 1..1000) {
            val peerConnectionObserverArgumentCaptor: ArgumentCaptor<PeerConnection.Observer> =
                ArgumentCaptor.forClass(PeerConnection.Observer::class.java)

            Mockito.`when`(
                mockedPeerConnectionFactory!!.createPeerConnection(
                    any(PeerConnection.RTCConfiguration::class.java),
                    peerConnectionObserverArgumentCaptor.capture()
                )
            ).thenReturn(mockedPeerConnection)

            val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)

            Mockito.`when`(mockedStatusDataChannel.label()).thenAnswer(ReturnValueOrThrowIfDisposed("status"))
            Mockito.`when`(mockedStatusDataChannel.state())
                .thenAnswer(ReturnValueOrThrowIfDisposed(DataChannel.State.OPEN))
            Mockito.`when`(mockedStatusDataChannel.send(any())).thenAnswer(ReturnValueOrThrowIfDisposed(true))
            Mockito.`when`(mockedPeerConnection!!.createDataChannel(eq("status"), any()))
                .thenReturn(mockedStatusDataChannel)

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

            val dataChannelMessageCount = 5

            // Exceptions thrown in threads are not propagated to the main thread, so it needs to be explicitly done
            // (for example, for IllegalStateExceptions when using a disposed data channel).
            var exceptionSend: Exception? = null

            val sendThread = thread {
                try {
                    for (j in 0..<dataChannelMessageCount) {
                        peerConnectionWrapper!!.send(DataChannelMessage("the-message-type-$j"))
                    }
                } catch (e: Exception) {
                    exceptionSend = e
                }
            }

            val removePeerConnectionThread = thread {
                peerConnectionWrapper!!.removePeerConnection()
            }

            sendThread.join()
            removePeerConnectionThread.join()

            if (exceptionSend !== null) {
                throw exceptionSend!!
            }

            Mockito.verify(mockedStatusDataChannel).registerObserver(any())
            Mockito.verify(mockedStatusDataChannel).dispose()
            Mockito.verify(mockedStatusDataChannel, atLeast(0)).label()
            Mockito.verify(mockedStatusDataChannel, atLeast(0)).state()
            Mockito.verify(mockedStatusDataChannel, atLeast(0)).send(any())
            Mockito.verifyNoMoreInteractions(mockedStatusDataChannel)
        }
    }

    @Test
    fun testRemovePeerConnectionWhileReceivingWithDifferentThreads() {
        // A brute force approach is used to test race conditions between different threads just repeating the test
        // several times. Due to this the test passing could be a false positive, as it could have been a matter of
        // luck, but even if the test may wrongly pass sometimes it is better than nothing (although, in general, with
        // that number of reruns, it fails when it should).
        for (i in 1..1000) {
            val peerConnectionObserverArgumentCaptor: ArgumentCaptor<PeerConnection.Observer> =
                ArgumentCaptor.forClass(PeerConnection.Observer::class.java)

            Mockito.`when`(
                mockedPeerConnectionFactory!!.createPeerConnection(
                    any(PeerConnection.RTCConfiguration::class.java),
                    peerConnectionObserverArgumentCaptor.capture()
                )
            ).thenReturn(mockedPeerConnection)

            val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
            Mockito.`when`(mockedStatusDataChannel.label()).thenAnswer(ReturnValueOrThrowIfDisposed("status"))
            Mockito.`when`(mockedStatusDataChannel.state()).thenAnswer(
                ReturnValueOrThrowIfDisposed(DataChannel.State.OPEN)
            )
            Mockito.`when`(mockedPeerConnection!!.createDataChannel(eq("status"), any()))
                .thenReturn(mockedStatusDataChannel)

            val statusDataChannelObserverArgumentCaptor: ArgumentCaptor<DataChannel.Observer> =
                ArgumentCaptor.forClass(DataChannel.Observer::class.java)

            doNothing().`when`(mockedStatusDataChannel)
                .registerObserver(statusDataChannelObserverArgumentCaptor.capture())

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

            // Exceptions thrown in threads are not propagated to the main thread, so it needs to be explicitly done
            // (for example, for IllegalStateExceptions when using a disposed data channel).
            var exceptionOnMessage: Exception? = null

            val onMessageThread = thread {
                try {
                    // It is assumed that, even if its data channel was disposed, its buffers can be used while there
                    // is a reference to them, so no special mock behaviour is added to throw an exception in that case.
                    statusDataChannelObserverArgumentCaptor.value.onMessage(
                        dataChannelMessageToBuffer(DataChannelMessage("audioOn"))
                    )

                    statusDataChannelObserverArgumentCaptor.value.onMessage(
                        dataChannelMessageToBuffer(DataChannelMessage("audioOff"))
                    )
                } catch (e: Exception) {
                    exceptionOnMessage = e
                }
            }

            val removePeerConnectionThread = thread {
                peerConnectionWrapper!!.removePeerConnection()
            }

            onMessageThread.join()
            removePeerConnectionThread.join()

            if (exceptionOnMessage !== null) {
                throw exceptionOnMessage!!
            }

            Mockito.verify(mockedStatusDataChannel).registerObserver(any())
            Mockito.verify(mockedStatusDataChannel).dispose()
            Mockito.verify(mockedStatusDataChannel, atLeast(0)).label()
            Mockito.verify(mockedStatusDataChannel, atLeast(0)).state()
            Mockito.verifyNoMoreInteractions(mockedStatusDataChannel)
            Mockito.verify(mockedDataChannelMessageListener, atMostOnce()).onAudioOn()
            Mockito.verify(mockedDataChannelMessageListener, atMostOnce()).onAudioOff()
            Mockito.verifyNoMoreInteractions(mockedDataChannelMessageListener)
        }
    }
}
