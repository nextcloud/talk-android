/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAudioKeyTest {

    @Test
    fun `media id round trip restores the key`() {
        val key = ChatAudioKey(internalUserId = 3L, roomToken = "abc123xy", messageId = 4711)

        assertEquals(key, ChatAudioKey.fromMediaId(key.mediaId))
    }

    @Test
    fun `media ids of other sources are not parsed`() {
        assertNull(ChatAudioKey.fromMediaId(null))
        assertNull(ChatAudioKey.fromMediaId(""))
        assertNull(ChatAudioKey.fromMediaId("4711"))
        assertNull(ChatAudioKey.fromMediaId("file:///sdcard/voice.mp3"))
    }

    @Test
    fun `malformed media ids are rejected`() {
        assertNull(ChatAudioKey.fromMediaId("talk-audio:"))
        assertNull(ChatAudioKey.fromMediaId("talk-audio:3/abc"))
        assertNull(ChatAudioKey.fromMediaId("talk-audio:3//4711"))
        assertNull(ChatAudioKey.fromMediaId("talk-audio:x/abc/4711"))
        assertNull(ChatAudioKey.fromMediaId("talk-audio:3/abc/x"))
        assertNull(ChatAudioKey.fromMediaId("talk-audio:3/a/b/4711"))
    }
}
