/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Playback speeds of chat audio, kept per account and separately for voice messages and audio files. */
@Singleton
class ChatAudioSpeeds @Inject constructor(
    private val appPreferences: AppPreferences,
    private val userManager: UserManager
) {
    fun speedFor(userId: String, kind: ChatAudioKind): PlaybackSpeed =
        when (kind) {
            ChatAudioKind.VOICE_MESSAGE -> appPreferences.getPreferredPlayback(userId)
            ChatAudioKind.AUDIO_FILE -> appPreferences.getAudioFilePlaybackSpeed(userId)
        }

    fun save(userId: String, kind: ChatAudioKind, speed: PlaybackSpeed) {
        when (kind) {
            ChatAudioKind.VOICE_MESSAGE -> appPreferences.savePreferredPlayback(userId, speed)
            ChatAudioKind.AUDIO_FILE -> appPreferences.saveAudioFilePlaybackSpeed(userId, speed)
        }
    }

    /** Saves [speed] for the account of [key], which is not necessarily the account of the current screen. */
    suspend fun save(key: ChatAudioKey, kind: ChatAudioKind, speed: PlaybackSpeed) {
        userManager.getUserWithId(key.internalUserId)?.userId?.let { save(it, kind, speed) }
    }
}
