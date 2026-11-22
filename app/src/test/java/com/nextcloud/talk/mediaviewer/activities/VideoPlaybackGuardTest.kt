/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import androidx.media3.common.Player
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VideoPlaybackGuardTest {
    private val player: Player = mock()
    private var allowed = true
    private val guard = VideoPlaybackGuard(player) { allowed }

    @Test
    fun `call or recording stops video and ending it never resumes automatically`() {
        allowed = false
        guard.enforcePolicy()
        verify(player).pause()
        allowed = true
        guard.enforcePolicy()
        verify(player, never()).play()
    }

    @Test
    fun `PiP play command during a call cannot start video`() {
        allowed = false
        whenever(player.playWhenReady).thenReturn(false)
        guard.toggle()
        verify(player, never()).play()
    }

    @Test
    fun `player UI play request during a call is immediately paused`() {
        allowed = false
        guard.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        verify(player).pause()
    }

    @Test
    fun `PiP controls pause playing video and resume only on user request`() {
        whenever(player.playWhenReady).thenReturn(true)
        guard.toggle()
        verify(player).pause()
        whenever(player.playWhenReady).thenReturn(false)
        guard.toggle()
        verify(player).play()
    }
}
