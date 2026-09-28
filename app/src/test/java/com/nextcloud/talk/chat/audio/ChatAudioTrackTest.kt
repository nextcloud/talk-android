/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import androidx.media3.common.PlaybackException
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.data.user.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatAudioTrackTest {

    private val user = User(
        id = 7L,
        userId = "alice",
        username = "alice",
        baseUrl = "https://cloud.example.com",
        token = "app-password"
    )

    private fun message(
        content: MessageTypeContent?,
        actorType: String = "users",
        roomToken: String? = "room1",
        path: String = "Talk/voice message.m4a"
    ) = ChatMessageUi(
        id = 42,
        message = "{file}",
        renderMarkdown = false,
        actorDisplayName = "Bob",
        actorType = actorType,
        actorId = "bob",
        isThread = false,
        threadTitle = "",
        threadReplies = 0,
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = 0L,
        date = LocalDate.of(2026, 1, 1),
        content = content,
        roomToken = roomToken,
        messageParameters = mapOf("file" to mapOf("path" to path, "id" to "123", "etag" to "abc"))
    )

    @Test
    fun `voice message is streamed from the WebDAV files of the account`() {
        val track = requireNotNull(message(MessageTypeContent.Voice).toChatAudioTrack(user, "Voice message"))

        assertEquals(ChatAudioKey(7L, "room1", 42), track.key)
        assertEquals(ChatAudioKind.VOICE_MESSAGE, track.kind)
        assertEquals("https://cloud.example.com/remote.php/dav/files/alice/Talk/voice%20message.m4a", track.uri)
        assertEquals("https://cloud.example.com/123/abc", track.cacheKey)
        assertEquals("Bob", track.title)
        assertEquals("Voice message", track.artist)
        assertTrue(track.artworkUri.orEmpty().startsWith("https://cloud.example.com/index.php/avatar/bob/"))
        assertTrue(ChatAudioCredentials.belongsToUser(track.uri, user.baseUrl, user.userId))
    }

    @Test
    fun `audio file is titled with its file name`() {
        val track = message(MessageTypeContent.AudioFile("song.mp3")).toChatAudioTrack(user, "Voice message")

        assertEquals(ChatAudioKind.AUDIO_FILE, track?.kind)
        assertEquals("song.mp3", track?.title)
        assertEquals("Bob", track?.artist)
    }

    @Test
    fun `messages that cannot be played give no track`() {
        assertNull(message(MessageTypeContent.RegularText).toChatAudioTrack(user, "Voice message"))
        assertNull(message(MessageTypeContent.Voice, path = "").toChatAudioTrack(user, "Voice message"))
        assertNull(message(MessageTypeContent.Voice, roomToken = null).toChatAudioTrack(user, "Voice message"))
        assertNull(message(MessageTypeContent.Voice).toChatAudioTrack(User(), "Voice message"))
    }

    @Test
    fun `guests have no server avatar as artwork`() {
        val track = message(MessageTypeContent.Voice, actorType = "guests").toChatAudioTrack(user, "Voice message")

        assertNotNull(track)
        assertNull(track?.artworkUri)
    }

    @Test
    fun `media item keeps identity, cache key and kind`() {
        val track = requireNotNull(
            message(MessageTypeContent.AudioFile("song.mp3")).toChatAudioTrack(user, "Voice message")
        )

        val mediaItem = track.toMediaItem()

        assertEquals(track.key, ChatAudioKey.fromMediaId(mediaItem.mediaId))
        assertEquals(track.uri, mediaItem.localConfiguration?.uri.toString())
        assertEquals(track.cacheKey, mediaItem.localConfiguration?.customCacheKey)
        assertEquals(ChatAudioKind.AUDIO_FILE, mediaItem.chatAudioKind())
    }

    @Test
    fun `playback errors are mapped to what the user can do about them`() {
        fun errorOf(errorCode: Int) = PlaybackException("test", null, errorCode).toChatAudioError()

        assertEquals(ChatAudioError.NETWORK, errorOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(ChatAudioError.NETWORK, errorOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(ChatAudioError.UNAVAILABLE, errorOf(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(ChatAudioError.UNAVAILABLE, errorOf(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(ChatAudioError.UNSUPPORTED, errorOf(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(ChatAudioError.UNKNOWN, errorOf(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
