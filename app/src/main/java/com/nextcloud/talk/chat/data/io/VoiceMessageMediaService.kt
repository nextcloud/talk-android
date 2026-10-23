/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class VoiceMessageMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
        // By default Media3 keeps the service in the foreground for 10 minutes after a pause or the end of the
        // message, and the notification of a foreground service cannot be swiped away.
        setForegroundServiceTimeoutMs(FOREGROUND_TIMEOUT_AFTER_PAUSE_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        /** The service leaves the foreground, and its notification can be swiped away, as soon as playback stops. */
        const val FOREGROUND_TIMEOUT_AFTER_PAUSE_MS = 0L
    }
}
