/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAudioCredentialsTest {

    private val baseUrl = "https://cloud.example.com"

    @Test
    fun `files of the account are authenticated`() {
        assertTrue(
            ChatAudioCredentials.belongsToUser(
                "https://cloud.example.com/remote.php/dav/files/alice/Talk/voice.mp3",
                baseUrl,
                "alice"
            )
        )
    }

    @Test
    fun `server installed in a sub directory is supported`() {
        assertTrue(
            ChatAudioCredentials.belongsToUser(
                "https://example.com/nextcloud/remote.php/dav/files/alice/Talk/voice.mp3",
                "https://example.com/nextcloud",
                "alice"
            )
        )
    }

    @Test
    fun `other hosts never get the credentials`() {
        val attacks = listOf(
            "https://cloud.example.com.evil.org/remote.php/dav/files/alice/voice.mp3",
            "https://cloud.example.com@evil.org/remote.php/dav/files/alice/voice.mp3",
            "http://cloud.example.com/remote.php/dav/files/alice/voice.mp3",
            "https://evil.org/https://cloud.example.com/remote.php/dav/files/alice/voice.mp3",
            "https://cloud.example.com:8443/remote.php/dav/files/alice/voice.mp3"
        )

        attacks.forEach { url ->
            assertFalse(url, ChatAudioCredentials.belongsToUser(url, baseUrl, "alice"))
        }
    }

    @Test
    fun `files of other accounts or other endpoints are not authenticated`() {
        assertFalse(
            ChatAudioCredentials.belongsToUser(
                "https://cloud.example.com/remote.php/dav/files/alice2/voice.mp3",
                baseUrl,
                "alice"
            )
        )
        assertFalse(
            ChatAudioCredentials.belongsToUser(
                "https://cloud.example.com/ocs/v2.php/apps/spreed/api/v1/chat/abc",
                baseUrl,
                "alice"
            )
        )
    }

    @Test
    fun `incomplete accounts are never matched`() {
        val url = "https://cloud.example.com/remote.php/dav/files/alice/voice.mp3"

        assertFalse(ChatAudioCredentials.belongsToUser(url, null, "alice"))
        assertFalse(ChatAudioCredentials.belongsToUser(url, "", "alice"))
        assertFalse(ChatAudioCredentials.belongsToUser(url, baseUrl, null))
        assertFalse(ChatAudioCredentials.belongsToUser(url, baseUrl, ""))
    }
}
