/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.models.json.participants.Participant
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationInfoViewModelTest {

    @Test
    fun `createConversationNameByParticipants should combine names correctly`() {
        val original = listOf("Dave", null, "Charlie")
        val all = listOf("Bob", "Charlie", "Dave", "Alice", null, "Simon")

        val expectedName = "Charlie, Dave, Alice, Bob, Simon"
        val result = ConversationInfoViewModel.createConversationNameByParticipants(original, all)

        assertEquals(expectedName, result)
    }

    private fun model(
        displayName: String,
        type: Participant.ParticipantType,
        isOnline: Boolean = true,
        actorType: Participant.ActorType = Participant.ActorType.USERS
    ) = ParticipantModel(
        participant = Participant(
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
        val owner = model("Zoe", Participant.ParticipantType.OWNER)
        val moderator = model("Alice", Participant.ParticipantType.MODERATOR)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(moderator, owner))
    }

    @Test
    fun `owners sort above guest moderators`() {
        val owner = model("Zoe", Participant.ParticipantType.OWNER)
        val guestModerator = model("Alice", Participant.ParticipantType.GUEST_MODERATOR)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(guestModerator, owner))
    }

    @Test
    fun `moderators sort above plain users`() {
        val moderator = model("Zoe", Participant.ParticipantType.MODERATOR)
        val user = model("Alice", Participant.ParticipantType.USER)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(user, moderator))
    }

    @Test
    fun `offline participants sort below online ones regardless of rank`() {
        val offlineOwner = model("Zoe", Participant.ParticipantType.OWNER, isOnline = false)
        val onlineUser = model("Alice", Participant.ParticipantType.USER)

        assertEquals(listOf("Alice", "Zoe"), sortedNames(offlineOwner, onlineUser))
    }

    @Test
    fun `groups and teams sort last`() {
        val group = model("Aaa Team", Participant.ParticipantType.USER, actorType = Participant.ActorType.GROUPS)
        val user = model("Zoe", Participant.ParticipantType.USER)

        assertEquals(listOf("Zoe", "Aaa Team"), sortedNames(group, user))
    }

    @Test
    fun `same rank falls back to the display name`() {
        val bob = model("Bob", Participant.ParticipantType.USER)
        val alice = model("alice", Participant.ParticipantType.USER)

        assertEquals(listOf("alice", "Bob"), sortedNames(bob, alice))
    }
}
