/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.Observer
import androidx.media3.common.Player

/**
 * Whether a player needs the audio focus: while it plays or is about to play, not while it is paused, idle or at the
 * end of the media.
 */
fun playerNeedsAudioFocus(playWhenReady: Boolean, playbackState: Int): Boolean =
    playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

/**
 * Holds the audio focus of the app while a player plays, so that the music of another app pauses and, after the
 * playback, comes back: the focus is transient. The player is paused when the focus is lost.
 * [release] gives the focus back.
 */
class PlaybackAudioFocus(context: Context, private val player: Player) : Player.Listener {
    private val focusManager = AudioFocusRequestManager(
        context.applicationContext,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    )
    private val focusLostObserver = Observer<AudioFocusRequestManager.ManagerState> { player.pause() }
    private var held = false

    init {
        focusManager.getManagerState.observeForever(focusLostObserver)
        player.addListener(this)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = update()

    override fun onPlaybackStateChanged(playbackState: Int) = update()

    private fun update() {
        val needed = playerNeedsAudioFocus(player.playWhenReady, player.playbackState)
        if (needed != held) {
            held = needed
            focusManager.audioFocusRequest(needed) {}
        }
    }

    fun release() {
        player.removeListener(this)
        focusManager.getManagerState.removeObserver(focusLostObserver)
        if (held) {
            held = false
            focusManager.audioFocusRequest(false) {}
        }
    }
}
