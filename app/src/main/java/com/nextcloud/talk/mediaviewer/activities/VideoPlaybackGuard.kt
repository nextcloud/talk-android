/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import androidx.media3.common.Player

internal class VideoPlaybackGuard(private val player: Player, private val canPlay: () -> Boolean) : Player.Listener {
    fun enforcePolicy() {
        if (!canPlay()) player.pause()
    }

    fun toggle() {
        if (player.playWhenReady) {
            player.pause()
        } else if (canPlay()) {
            player.play()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) enforcePolicy()
    }
}
