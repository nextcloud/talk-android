/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.models.json.participants.ParticipantDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantComparatorTest {

    private fun model(
        displayName: String,
        type: ParticipantDto.ParticipantType,
        isOnline: Boolean = true,
        actorType: ParticipantDto.ActorType = ParticipantDto.ActorType.USERS
    ) = ParticipantModel(
        participant = ParticipantDto(
            actorType = actorType,
            actorId = displayName.lowercase(),
            displayName = displayName,
            type = type
        ),
        isOnline = isOnline
    )

    private fun sortedNames(vararg items: ParticipantModel) =
        items.sortedWith(ConversationInfoViewModel.PARTICIPANT_COMPARATOR).map { it.participant.displayName }

    @Test
    fun `owners sort above moderators despite the display name`() {
        val owner = model("Zoe", ParticipantDto.ParticipantType.OWNER)
        val moderator = model("Alice", ParticipantDto.ParticipantType.MODERATOR)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(moderator, owner))
    }

    @Test
    fun `owners sort above guest moderators`() {
        val owner = model("Zoe", ParticipantDto.ParticipantType.OWNER)
        val guestModerator = model("Alice", ParticipantDto.ParticipantType.GUEST_MODERATOR)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(guestModerator, owner))
    }

    @Test
    fun `moderators sort above plain users`() {
        val moderator = model("Zoe", ParticipantDto.ParticipantType.MODERATOR)
        val user = model("Alice", ParticipantDto.ParticipantType.USER)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(user, moderator))
    }

    @Test
    fun `offline participants sort below online ones regardless of rank`() {
        val offlineOwner = model("Zoe", ParticipantDto.ParticipantType.OWNER, isOnline = false)
        val onlineUser = model("Alice", ParticipantDto.ParticipantType.USER)

        assertEquals(listOf("Alice", "Zoe"), sortedNames(offlineOwner, onlineUser))
    }

    @Test
    fun `groups and teams sort last`() {
        val group = model("Aaa Team", ParticipantDto.ParticipantType.USER, actorType = ParticipantDto.ActorType.GROUPS)
        val user = model("Zoe", ParticipantDto.ParticipantType.USER)

        assertEquals(listOf("Zoe", "Aaa Team"), sortedNames(group, user))
    }

    @Test
    fun `same rank falls back to the display name`() {
        val bob = model("Bob", ParticipantDto.ParticipantType.USER)
        val alice = model("alice", ParticipantDto.ParticipantType.USER)

        assertEquals(listOf("alice", "Bob"), sortedNames(bob, alice))
    }
}
