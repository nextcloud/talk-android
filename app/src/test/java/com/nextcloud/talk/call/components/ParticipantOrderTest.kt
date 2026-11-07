/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.components

import com.nextcloud.talk.activities.ParticipantUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantOrderTest {

    private val allKeys = setOf("a", "b", "c", "d")

    @Test
    fun `speaker that is not visible is moved to the front`() {
        val result = promoteSpeakers(
            speakers = listOf("a", "b"),
            startedSpeaking = setOf("c"),
            visibleSessionKeys = setOf("a"),
            participantSessionKeys = allKeys
        )

        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun `existing speaker that is not visible is moved to the front`() {
        val result = promoteSpeakers(
            speakers = listOf("a", "b", "c"),
            startedSpeaking = setOf("c"),
            visibleSessionKeys = emptySet(),
            participantSessionKeys = allKeys
        )

        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun `visible speaker is not promoted`() {
        val result = promoteSpeakers(
            speakers = listOf("a", "b"),
            startedSpeaking = setOf("b", "c"),
            visibleSessionKeys = setOf("b", "c"),
            participantSessionKeys = allKeys
        )

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `speakers that left the call are dropped`() {
        val result = promoteSpeakers(
            speakers = listOf("a", "x"),
            startedSpeaking = setOf("y"),
            visibleSessionKeys = emptySet(),
            participantSessionKeys = allKeys
        )

        assertEquals(listOf("a"), result)
    }

    @Test
    fun `speakers are sorted after screen sharers and before other participants in speaker order`() {
        val participants = listOf(
            participant("video", isStreamEnabled = true),
            participant("speaker2"),
            participant("screen", isScreenStreamEnabled = true),
            participant("speaker1")
        )

        val result = sortParticipants(participants, speakers = listOf("speaker1", "speaker2"))

        assertEquals(listOf("screen", "speaker1", "speaker2", "video"), result.map { it.sessionKey })
    }

    @Test
    fun `participants without speaker priority keep the existing order`() {
        val participants = listOf(
            participant("muted"),
            participant("audio", isAudioEnabled = true),
            participant("video", isStreamEnabled = true)
        )

        val result = sortParticipants(participants, speakers = emptyList())

        assertEquals(listOf("video", "audio", "muted"), result.map { it.sessionKey })
    }

    private fun participant(
        sessionKey: String,
        isStreamEnabled: Boolean = false,
        isAudioEnabled: Boolean = false,
        isScreenStreamEnabled: Boolean = false
    ) = ParticipantUiState(
        sessionKey = sessionKey,
        baseUrl = "",
        roomToken = "",
        nick = sessionKey,
        isConnected = true,
        isAudioEnabled = isAudioEnabled,
        isStreamEnabled = isStreamEnabled,
        isScreenStreamEnabled = isScreenStreamEnabled,
        raisedHand = false,
        isInternal = false
    )
}
