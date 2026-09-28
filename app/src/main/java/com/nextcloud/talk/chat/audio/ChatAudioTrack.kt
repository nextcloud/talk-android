/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CharacterAvatarUtils

/** An audio message as it is handed to the playback service. */
data class ChatAudioTrack(
    val key: ChatAudioKey,
    val kind: ChatAudioKind,
    val uri: String,
    val cacheKey: String,
    val title: String,
    val artist: String,
    val artworkUri: String?
)

private const val FILE_PARAMETER = "file"
private const val FILE_PATH = "path"
private const val FILE_ID = "id"
private const val FILE_ETAG = "etag"
private const val EXTRA_KIND = "com.nextcloud.talk.chat.audio.KIND"

fun ChatMessageUi.chatAudioKind(): ChatAudioKind? =
    when (content) {
        is MessageTypeContent.Voice -> ChatAudioKind.VOICE_MESSAGE
        is MessageTypeContent.AudioFile -> ChatAudioKind.AUDIO_FILE
        else -> null
    }

/**
 * Creates the track that plays this message for [user], or null if the message is no playable audio message.
 * The file is streamed from the user's WebDAV files, where Talk shares attachments to.
 */
fun ChatMessageUi.toChatAudioTrack(user: User, voiceMessageLabel: String): ChatAudioTrack? {
    val kind = chatAudioKind() ?: return null
    val file = messageParameters[FILE_PARAMETER].orEmpty()
    val path = file[FILE_PATH].orEmpty()
    val baseUrl = user.baseUrl.orEmpty()
    val userId = user.userId.orEmpty()
    val key = user.id?.let { internalUserId -> roomToken?.let { ChatAudioKey(internalUserId, it, id) } }
    return if (key == null || path.isEmpty() || baseUrl.isEmpty() || userId.isEmpty()) {
        null
    } else {
        val uri = ApiUtils.getUrlForFileDownload(baseUrl, userId, path)
        val fileId = file[FILE_ID].orEmpty()
        val fileName = (content as? MessageTypeContent.AudioFile)?.fileName.orEmpty()
        val hasServerAvatar = CharacterAvatarUtils.avatarFor(actorType, actorId, actorDisplayName, null) == null
        ChatAudioTrack(
            key = key,
            kind = kind,
            uri = uri,
            cacheKey = if (fileId.isEmpty()) uri else "$baseUrl/$fileId/${file[FILE_ETAG].orEmpty()}",
            title = if (kind == ChatAudioKind.AUDIO_FILE && fileName.isNotEmpty()) fileName else actorDisplayName,
            artist = if (kind == ChatAudioKind.VOICE_MESSAGE) voiceMessageLabel else actorDisplayName,
            artworkUri = actorId?.takeIf { hasServerAvatar }?.let { ApiUtils.getUrlForAvatar(baseUrl, it, true) }
        )
    }
}

@OptIn(UnstableApi::class)
fun ChatAudioTrack.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(key.mediaId)
        .setUri(uri)
        .setCustomCacheKey(cacheKey)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUri?.toUri())
                .setExtras(bundleOf(EXTRA_KIND to kind.name))
                .build()
        )
        .build()

fun MediaItem.chatAudioKind(): ChatAudioKind {
    val name = mediaMetadata.extras?.getString(EXTRA_KIND)
    return ChatAudioKind.entries.firstOrNull { it.name == name } ?: ChatAudioKind.VOICE_MESSAGE
}

fun PlaybackException.toChatAudioError(): ChatAudioError =
    when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> ChatAudioError.NETWORK

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> ChatAudioError.UNAVAILABLE

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> ChatAudioError.UNSUPPORTED

        else -> ChatAudioError.UNKNOWN
    }
