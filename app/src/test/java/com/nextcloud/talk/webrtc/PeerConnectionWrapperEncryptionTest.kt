/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.call.e2ee.FrameKeyRing
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.webrtc.DataChannel
import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SessionDescription

class PeerConnectionWrapperEncryptionTest {

    private class FakeKeyRing : FrameKeyRing {
        val encryptors = ArrayList<FrameEncryptor>()
        val decryptors = ArrayList<FrameDecryptor>()
        private var disposed = false

        override fun setKey(key: ByteArray, keyIndex: Int): Boolean = true

        override fun attachEncryptor(sender: RtpSender): Boolean {
            if (disposed) return false
            sender.setFrameEncryptor(FrameEncryptor { 0 }.also { encryptors.add(it) })
            return true
        }

        override fun attachDecryptor(receiver: RtpReceiver): Boolean {
            if (disposed) return false
            receiver.setFrameDecryptor(FrameDecryptor { 0 }.also { decryptors.add(it) })
            return true
        }

        override fun dispose() {
            disposed = true
        }
    }

    private lateinit var mockedPeerConnection: PeerConnection
    private lateinit var mockedPeerConnectionFactory: PeerConnectionFactory
    private lateinit var mockedSignalingMessageReceiver: SignalingMessageReceiver
    private lateinit var mockedSignalingMessageSender: SignalingMessageSender
    private lateinit var observerCaptor: ArgumentCaptor<PeerConnection.Observer>

    @Before
    fun setUp() {
        mockedPeerConnection = Mockito.mock(PeerConnection::class.java)
        mockedPeerConnectionFactory = Mockito.mock(PeerConnectionFactory::class.java)
        mockedSignalingMessageReceiver = Mockito.mock(SignalingMessageReceiver::class.java)
        mockedSignalingMessageSender = Mockito.mock(SignalingMessageSender::class.java)
        observerCaptor = ArgumentCaptor.forClass(PeerConnection.Observer::class.java)
        Mockito.`when`(
            mockedPeerConnectionFactory.createPeerConnection(
                any(PeerConnection.RTCConfiguration::class.java),
                observerCaptor.capture()
            )
        ).thenReturn(mockedPeerConnection)
        val mockedStatusDataChannel = Mockito.mock(DataChannel::class.java)
        Mockito.`when`(mockedStatusDataChannel.label()).thenReturn("status")
        Mockito.`when`(mockedPeerConnection.createDataChannel(eq("status"), any())).thenReturn(mockedStatusDataChannel)
    }

    private fun createWrapper(
        senderKeyRing: FrameKeyRing?,
        receiverKeyRing: FrameKeyRing?,
        isMCUPublisher: Boolean = senderKeyRing != null,
        encryptionRequired: Boolean = false,
        localStream: MediaStream? = null
    ): PeerConnectionWrapper =
        PeerConnectionWrapper(
            mockedPeerConnectionFactory,
            ArrayList(),
            MediaConstraints(),
            "the-session-id",
            "the-local-session-id",
            localStream,
            isMCUPublisher,
            true,
            "video",
            mockedSignalingMessageReceiver,
            mockedSignalingMessageSender,
            encryptionRequired,
            senderKeyRing,
            receiverKeyRing
        )

    @Test
    fun testEncryptorIsAttachedToEverySender() {
        val senders = listOf(Mockito.mock(RtpSender::class.java), Mockito.mock(RtpSender::class.java))
        Mockito.`when`(mockedPeerConnection.senders).thenReturn(senders)
        val keyRing = FakeKeyRing()

        createWrapper(senderKeyRing = keyRing, receiverKeyRing = null)

        assertEquals(2, keyRing.encryptors.size)
        verify(senders[0]).setFrameEncryptor(keyRing.encryptors[0])
        verify(senders[1]).setFrameEncryptor(keyRing.encryptors[1])
    }

    @Test
    fun testDecryptorIsAttachedWhenTrackIsAdded() {
        val keyRing = FakeKeyRing()
        createWrapper(senderKeyRing = null, receiverKeyRing = keyRing)
        val receiver = Mockito.mock(RtpReceiver::class.java)

        observerCaptor.value.onAddTrack(receiver, arrayOf())

        assertEquals(1, keyRing.decryptors.size)
        verify(receiver).setFrameDecryptor(keyRing.decryptors[0])
    }

    @Test
    fun testEncryptedConnectionWithoutKeysIsRefused() {
        for (publisher in listOf(true, false)) {
            assertThrows(IllegalStateException::class.java) {
                createWrapper(
                    senderKeyRing = null,
                    receiverKeyRing = null,
                    isMCUPublisher = publisher,
                    encryptionRequired = true
                )
            }
        }
        // A connection that also sends, like one without the MCU, needs both
        assertThrows(IllegalStateException::class.java) {
            createWrapper(
                senderKeyRing = null,
                receiverKeyRing = FakeKeyRing(),
                isMCUPublisher = false,
                encryptionRequired = true,
                localStream = Mockito.mock(MediaStream::class.java)
            )
        }

        verify(mockedPeerConnectionFactory, never()).createPeerConnection(
            any(PeerConnection.RTCConfiguration::class.java),
            any(PeerConnection.Observer::class.java)
        )
    }

    @Test
    fun testEncryptedConnectionsWithKeysAreCreated() {
        createWrapper(senderKeyRing = FakeKeyRing(), receiverKeyRing = null, encryptionRequired = true)
        createWrapper(senderKeyRing = null, receiverKeyRing = FakeKeyRing(), encryptionRequired = true)

        verify(mockedPeerConnectionFactory, Mockito.times(2)).createPeerConnection(
            any(PeerConnection.RTCConfiguration::class.java),
            any(PeerConnection.Observer::class.java)
        )
    }

    @Test
    fun testPublisherWithDisposedKeyRingSendsNothing() {
        val sender = Mockito.mock(RtpSender::class.java)
        Mockito.`when`(sender.track()).thenReturn(Mockito.mock(org.webrtc.MediaStreamTrack::class.java))
        Mockito.`when`(mockedPeerConnection.senders).thenReturn(listOf(sender))
        val keyRing = FakeKeyRing()
        keyRing.dispose()

        createWrapper(senderKeyRing = keyRing, receiverKeyRing = null)

        verify(sender, never()).setFrameEncryptor(any())
        verify(mockedPeerConnection).removeTrack(sender)
    }

    @Test
    fun testTrackOfDisposedKeyRingIsSkipped() {
        val keyRing = FakeKeyRing()
        createWrapper(senderKeyRing = null, receiverKeyRing = keyRing)
        val receiver = Mockito.mock(RtpReceiver::class.java)
        keyRing.dispose()

        observerCaptor.value.onAddTrack(receiver, arrayOf())

        verify(receiver, never()).setFrameDecryptor(any())
    }

    @Test
    fun testNothingIsAttachedWithoutKeyRings() {
        val sender = Mockito.mock(RtpSender::class.java)
        Mockito.`when`(mockedPeerConnection.senders).thenReturn(listOf(sender))
        createWrapper(senderKeyRing = null, receiverKeyRing = null, isMCUPublisher = true)
        val receiver = Mockito.mock(RtpReceiver::class.java)

        observerCaptor.value.onAddTrack(receiver, arrayOf())

        verify(sender, never()).setFrameEncryptor(any())
        verify(receiver, never()).setFrameDecryptor(any())
    }

    @Test
    fun testVideoCodecPreference() {
        val sdp = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96 98\r\na=rtpmap:96 H264/90000\r\na=rtpmap:98 VP8/90000\r\n"

        createWrapper(senderKeyRing = null, receiverKeyRing = FakeKeyRing())
        createWrapper(senderKeyRing = null, receiverKeyRing = null)
        val (encryptedListener, plainListener) = captureWebRtcListeners()

        encryptedListener.onOffer(sdp, null)
        verify(mockedPeerConnection).setRemoteDescription(any(), descriptionWith("m=video 9 UDP/TLS/RTP/SAVPF 98 96"))

        plainListener.onOffer(sdp, null)
        verify(mockedPeerConnection).setRemoteDescription(any(), descriptionWith("m=video 9 UDP/TLS/RTP/SAVPF 96 98"))
    }

    private fun captureWebRtcListeners(): List<SignalingMessageReceiver.WebRtcMessageListener> {
        val captor = ArgumentCaptor.forClass(SignalingMessageReceiver.WebRtcMessageListener::class.java)
        verify(mockedSignalingMessageReceiver, Mockito.times(2))
            .addListener(captor.capture(), eq("the-session-id"), eq("video"))
        return captor.allValues
    }

    private fun descriptionWith(mediaLine: String): SessionDescription = argThat { description.contains(mediaLine) }
}
