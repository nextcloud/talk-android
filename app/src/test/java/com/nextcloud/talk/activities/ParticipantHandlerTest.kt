/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.DataChannelMessageListener
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.webrtc.AudioTrack
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.VideoTrack

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class ParticipantHandlerTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var signalingMessageReceiver: SignalingMessageReceiver
    private lateinit var onParticipantShareScreen: (String?) -> Unit
    private lateinit var onParticipantUnshareScreen: (String?) -> Unit

    private lateinit var handler: ParticipantHandler

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        signalingMessageReceiver = mock {}
        onParticipantShareScreen = mock {}
        onParticipantUnshareScreen = mock {}
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Initial state is correct`() =
        runTest {
            // Given
            val sessionId = "session-123"

            // When
            handler = ParticipantHandler(
                sessionId = sessionId,
                baseUrl = "",
                roomToken = "",
                signalingMessageReceiver = signalingMessageReceiver,
                onParticipantShareScreen = onParticipantShareScreen,
                onParticipantUnshareScreen = onParticipantUnshareScreen
            )

            // Then
            val expectedState = ParticipantUiState(
                sessionKey = sessionId,
                baseUrl = "",
                roomToken = "",
                nick = "Guest",
                isConnected = false,
                isAudioEnabled = false,
                isStreamEnabled = false,
                isScreenStreamEnabled = false,
                raisedHand = false,
                isInternal = false
            )
            assertEquals(expectedState, handler.uiState.value)
        }

    @Test
    fun `Speaking data channel messages update the speaking state`() {
        handler = createHandler()
        val (listener, _) = attachPeerConnectionWithObserver()

        listener.onSpeaking()
        assertTrue(handler.uiState.value.isSpeaking)

        listener.onStoppedSpeaking()
        assertFalse(handler.uiState.value.isSpeaking)
    }

    @Test
    fun `Connected participant with audio who speaks is an active speaker`() {
        handler = createHandler()
        val (listener, observer) = attachPeerConnectionWithObserver()
        connectWithAudio(listener, observer)

        listener.onSpeaking()

        assertTrue(handler.uiState.value.isActiveSpeaker)
    }

    @Test
    fun `Muting audio hides the active speaker`() {
        handler = createHandler()
        val (listener, observer) = attachPeerConnectionWithObserver()
        connectWithAudio(listener, observer)
        listener.onSpeaking()

        listener.onAudioOff()

        assertFalse(handler.uiState.value.isActiveSpeaker)
    }

    @Test
    fun `Speaking message while muted does not make an active speaker`() {
        handler = createHandler()
        val (listener, observer) = attachPeerConnectionWithObserver()
        connectWithAudio(listener, observer)
        listener.onAudioOff()

        listener.onSpeaking()

        assertFalse(handler.uiState.value.isActiveSpeaker)
    }

    @Test
    fun `Temporary ICE disconnection hides the active speaker until reconnected`() {
        handler = createHandler()
        val (listener, observer) = attachPeerConnectionWithObserver()
        connectWithAudio(listener, observer)
        listener.onSpeaking()

        observer.onIceConnectionStateChanged(IceConnectionState.DISCONNECTED)
        assertFalse(handler.uiState.value.isActiveSpeaker)

        observer.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
        assertTrue(handler.uiState.value.isActiveSpeaker)
    }

    @Test
    fun `Removing the stream hides the active speaker`() {
        handler = createHandler()
        val (listener, observer) = attachPeerConnectionWithObserver()
        observer.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
        val removedStream = MediaStream(0L).apply { audioTracks.add(mock<AudioTrack> {}) }
        observer.onStreamAdded(removedStream)
        listener.onSpeaking()
        assertTrue(handler.uiState.value.isActiveSpeaker)

        // The removed stream is passed to the observer and still lists its tracks.
        observer.onStreamRemoved(removedStream)

        assertFalse(handler.uiState.value.isActiveSpeaker)
        assertFalse(handler.uiState.value.isAudioEnabled)
        assertNull(handler.uiState.value.mediaStream)
    }

    @Test
    fun `Removing the screen stream clears the screen share`() {
        handler = createHandler()
        val screenPeerConnectionWrapper: PeerConnectionWrapper = mock {}
        handler.setScreenPeerConnection(screenPeerConnectionWrapper)
        val observerCaptor = argumentCaptor<PeerConnectionObserver>()
        verify(screenPeerConnectionWrapper).addObserver(observerCaptor.capture())
        val removedStream = MediaStream(0L).apply { videoTracks.add(mock<VideoTrack> {}) }
        observerCaptor.firstValue.onStreamAdded(removedStream)
        assertTrue(handler.uiState.value.isScreenStreamEnabled)

        // The removed stream is passed to the observer and still lists its tracks.
        observerCaptor.firstValue.onStreamRemoved(removedStream)

        assertFalse(handler.uiState.value.isScreenStreamEnabled)
        assertNull(handler.uiState.value.screenMediaStream)
        verify(onParticipantUnshareScreen).invoke("session-123")
    }

    @Test
    fun `Removing an old stream after a new one was added keeps the new stream`() {
        handler = createHandler()
        val (listener, observer) = attachPeerConnectionWithObserver()
        observer.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
        val oldStream = MediaStream(0L).apply { audioTracks.add(mock<AudioTrack> {}) }
        val newStream = MediaStream(0L).apply { audioTracks.add(mock<AudioTrack> {}) }
        observer.onStreamAdded(oldStream)
        observer.onStreamAdded(newStream)
        listener.onSpeaking()

        observer.onStreamRemoved(oldStream)

        assertSame(newStream, handler.uiState.value.mediaStream)
        assertTrue(handler.uiState.value.isAudioEnabled)
        assertTrue(handler.uiState.value.isActiveSpeaker)
    }

    @Test
    fun `Removing an old screen stream after a new one was added keeps the screen share`() {
        handler = createHandler()
        val screenPeerConnectionWrapper: PeerConnectionWrapper = mock {}
        handler.setScreenPeerConnection(screenPeerConnectionWrapper)
        val observerCaptor = argumentCaptor<PeerConnectionObserver>()
        verify(screenPeerConnectionWrapper).addObserver(observerCaptor.capture())
        val oldStream = MediaStream(0L).apply { videoTracks.add(mock<VideoTrack> {}) }
        val newStream = MediaStream(0L).apply { videoTracks.add(mock<VideoTrack> {}) }
        observerCaptor.firstValue.onStreamAdded(oldStream)
        observerCaptor.firstValue.onStreamAdded(newStream)

        observerCaptor.firstValue.onStreamRemoved(oldStream)

        assertSame(newStream, handler.uiState.value.screenMediaStream)
        assertTrue(handler.uiState.value.isScreenStreamEnabled)
        verify(onParticipantUnshareScreen, never()).invoke(any())
    }

    @Test
    fun `Replacing the peer connection resets the speaking state`() {
        handler = createHandler()
        val (listener, _) = attachPeerConnectionWithObserver()
        listener.onSpeaking()

        handler.setPeerConnection(mock<PeerConnectionWrapper> {})

        assertFalse(handler.uiState.value.isSpeaking)
    }

    private fun connectWithAudio(listener: DataChannelMessageListener, observer: PeerConnectionObserver) {
        observer.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
        listener.onAudioOn()
    }

    private fun createHandler() =
        ParticipantHandler(
            sessionId = "session-123",
            baseUrl = "",
            roomToken = "",
            signalingMessageReceiver = signalingMessageReceiver,
            onParticipantShareScreen = onParticipantShareScreen,
            onParticipantUnshareScreen = onParticipantUnshareScreen
        )

    private fun attachPeerConnectionWithObserver(): Pair<DataChannelMessageListener, PeerConnectionObserver> {
        val peerConnectionWrapper: PeerConnectionWrapper = mock {}
        handler.setPeerConnection(peerConnectionWrapper)
        val listenerCaptor = argumentCaptor<DataChannelMessageListener>()
        verify(peerConnectionWrapper).addListener(listenerCaptor.capture())
        val observerCaptor = argumentCaptor<PeerConnectionObserver>()
        verify(peerConnectionWrapper).addObserver(observerCaptor.capture())
        return listenerCaptor.firstValue to observerCaptor.firstValue
    }
}
