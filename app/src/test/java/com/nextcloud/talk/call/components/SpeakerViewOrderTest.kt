/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("TooManyFunctions")
class SpeakerViewOrderTest {

    private val participants = listOf("a", "b", "c", "d")

    @Test
    fun `initial order promotes the participant currently speaking`() {
        val result = initialSpeakerViewOrder(
            participantSessionKeys = participants,
            activeSpeakerSessionKeys = setOf("c"),
            previouslyPromotedSessionKey = "b",
            speakers = listOf("d")
        )

        assertEquals(listOf("c", "a", "b", "d"), result)
    }

    @Test
    fun `initial order keeps the previously promoted participant when nobody speaks`() {
        val result = initialSpeakerViewOrder(
            participantSessionKeys = participants,
            activeSpeakerSessionKeys = emptySet(),
            previouslyPromotedSessionKey = "b",
            speakers = listOf("d")
        )

        assertEquals(listOf("b", "a", "c", "d"), result)
    }

    @Test
    fun `initial order falls back to the most recent speaker and then the first participant`() {
        val withSpeaker = initialSpeakerViewOrder(
            participantSessionKeys = participants,
            activeSpeakerSessionKeys = emptySet(),
            previouslyPromotedSessionKey = "left",
            speakers = listOf("left", "d")
        )
        val withoutSpeaker = initialSpeakerViewOrder(
            participantSessionKeys = participants,
            activeSpeakerSessionKeys = emptySet(),
            previouslyPromotedSessionKey = null,
            speakers = emptyList()
        )

        assertEquals(listOf("d", "a", "b", "c"), withSpeaker)
        assertEquals(participants, withoutSpeaker)
    }

    @Test
    fun `participant that starts speaking is promoted and the previously promoted one leads the bar`() {
        val result = update(order = participants, activeSpeakers = setOf("c"))

        assertEquals(listOf("c", "a", "b", "d"), result)
    }

    @Test
    fun `participant bar is ordered by the most recent speakers`() {
        val afterC = update(order = participants, activeSpeakers = setOf("c"))
        val afterD = update(order = afterC, activeSpeakers = setOf("d"), previousActiveSpeakers = setOf("c"))
        val afterB = update(order = afterD, activeSpeakers = setOf("b"), previousActiveSpeakers = setOf("d"))

        assertEquals(listOf("b", "d", "c", "a"), afterB)
    }

    @Test
    fun `promotion is handed over to a participant still speaking when the promoted one stops`() {
        val result = update(
            order = participants,
            activeSpeakers = setOf("d"),
            previousActiveSpeakers = setOf("a", "d")
        )

        assertEquals(listOf("d", "a", "b", "c"), result)
    }

    @Test
    fun `promoted participant stays when it stops speaking and nobody else speaks`() {
        val result = update(order = participants, activeSpeakers = emptySet(), previousActiveSpeakers = setOf("a"))

        assertEquals(participants, result)
    }

    @Test
    fun `participant that stops speaking while not promoted does not change the order`() {
        val result = update(
            order = participants,
            activeSpeakers = setOf("a"),
            previousActiveSpeakers = setOf("a", "b")
        )

        assertEquals(participants, result)
    }

    @Test
    fun `new participants are added to the end and participants that left are dropped`() {
        val result = update(order = participants, participantSessionKeys = listOf("e", "a", "c", "d"))

        assertEquals(listOf("a", "c", "d", "e"), result)
    }

    @Test
    fun `new participant is promoted when the promoted participant left`() {
        val result = update(
            order = participants,
            participantSessionKeys = listOf("b", "c", "d"),
            speakers = listOf("c")
        )

        assertEquals(listOf("c", "b", "d"), result)
    }

    @Test
    fun `first participant in the bar is promoted when the promoted participant left and nobody speaks`() {
        val result = update(order = participants, participantSessionKeys = listOf("d", "c", "b"))

        assertEquals(listOf("b", "c", "d"), result)
    }

    @Test
    fun `participant shown first is promoted when several start speaking at once`() {
        val result = update(
            order = participants,
            participantSessionKeys = listOf("d", "c", "b", "a"),
            activeSpeakers = setOf("d", "c")
        )

        assertEquals(listOf("c", "a", "b", "d"), result)
    }

    @Test
    fun `first participant is promoted when the order is empty`() {
        val result = update(order = emptyList(), participantSessionKeys = listOf("a", "b"))

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `bar tiles keep their preferred width when all fit`() {
        assertEquals(150.dp, participantBarTileLength(availableLength = 400.dp, tileCount = 2))
    }

    @Test
    fun `bar tiles keep their preferred width when the cut tile is clearly visible`() {
        // Two tiles fit fully, 84dp of the third one is visible
        assertEquals(150.dp, participantBarTileLength(availableLength = 400.dp, tileCount = 3))
    }

    @Test
    fun `bar tiles are narrowed so the next tile peeks in`() {
        // Only 4dp of the third tile would be visible
        assertEquals(144.dp, participantBarTileLength(availableLength = 320.dp, tileCount = 3))
    }

    @Test
    fun `bar tiles are narrowed so all fit when only a few pixels are missing`() {
        assertEquals(148.dp, participantBarTileLength(availableLength = 460.dp, tileCount = 3))
    }

    @Test
    fun `vertical bar tiles keep their preferred height when all fit`() {
        assertEquals(
            100.dp,
            participantBarTileLength(availableLength = 300.dp, tileCount = 2, preferredLength = 100.dp)
        )
    }

    @Test
    fun `vertical bar tiles are shortened so the next tile peeks in`() {
        // Only 4dp of the third tile would be visible
        assertEquals(
            94.dp,
            participantBarTileLength(availableLength = 220.dp, tileCount = 3, preferredLength = 100.dp)
        )
    }

    @Test
    fun `bar tiles never get a negative length`() {
        assertEquals(0.dp, participantBarTileLength(availableLength = 10.dp, tileCount = 3))
    }

    @Test
    fun `bar is placed at the side when the space is wider than high`() {
        assertEquals(ParticipantBarPlacement.SIDE, participantBarPlacement(width = 700.dp, height = 300.dp))
    }

    @Test
    fun `bar is placed at the bottom when the space is higher than wide`() {
        assertEquals(ParticipantBarPlacement.BOTTOM, participantBarPlacement(width = 360.dp, height = 600.dp))
    }

    @Test
    fun `bar is placed at the bottom when it would take more than half of the width at the side`() {
        assertEquals(ParticipantBarPlacement.BOTTOM, participantBarPlacement(width = 300.dp, height = 250.dp))
    }

    @Test
    fun `bar is not placed when it does not fit at the side or the bottom`() {
        assertEquals(ParticipantBarPlacement.NONE, participantBarPlacement(width = 240.dp, height = 135.dp))
    }

    private fun update(
        order: List<String>,
        participantSessionKeys: List<String> = participants,
        activeSpeakers: Set<String> = emptySet(),
        previousActiveSpeakers: Set<String> = emptySet(),
        speakers: List<String> = emptyList()
    ) = updateSpeakerViewOrder(
        order = order,
        participantSessionKeys = participantSessionKeys,
        activeSpeakerSessionKeys = activeSpeakers,
        previousActiveSpeakerSessionKeys = previousActiveSpeakers,
        speakers = speakers
    )
}
