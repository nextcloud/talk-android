/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAudioRepeatModeTest {

    @Test
    fun `repeat button switches from off to all to one and back to off`() {
        assertEquals(ChatAudioRepeatMode.ALL, ChatAudioRepeatMode.OFF.next())
        assertEquals(ChatAudioRepeatMode.ONE, ChatAudioRepeatMode.ALL.next())
        assertEquals(ChatAudioRepeatMode.OFF, ChatAudioRepeatMode.ONE.next())
    }

    @Test
    fun `stored names are restored and unknown ones mean off`() {
        assertEquals(ChatAudioRepeatMode.ONE, ChatAudioRepeatMode.fromName("ONE"))
        assertEquals(ChatAudioRepeatMode.OFF, ChatAudioRepeatMode.fromName(""))
        assertEquals(ChatAudioRepeatMode.OFF, ChatAudioRepeatMode.fromName(null))
    }
}
