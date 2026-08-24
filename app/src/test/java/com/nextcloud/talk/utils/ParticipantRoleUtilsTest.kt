/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import org.junit.Assert
import org.junit.Test

class ParticipantRoleUtilsTest {

    private fun participant(type: Participant.ParticipantType): Participant =
        Participant(actorType = Participant.ActorType.USERS, actorId = "alice", type = type)

    @Test
    fun testRoleOf_ownerInGroupConversation_isOwner() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.OWNER),
            ConversationEnums.ConversationType.ROOM_GROUP_CALL
        )
        Assert.assertEquals(ParticipantRole.OWNER, result)
    }

    @Test
    fun testRoleOf_moderatorInPublicConversation_isModerator() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.MODERATOR),
            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
        )
        Assert.assertEquals(ParticipantRole.MODERATOR, result)
    }

    @Test
    fun testRoleOf_guestModerator_isModerator() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.GUEST_MODERATOR),
            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
        )
        Assert.assertEquals(ParticipantRole.MODERATOR, result)
    }

    @Test
    fun testRoleOf_plainUser_isNone() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.USER),
            ConversationEnums.ConversationType.ROOM_GROUP_CALL
        )
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    @Test
    fun testRoleOf_guest_isNone() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.GUEST),
            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
        )
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    @Test
    fun testRoleOf_userFollowingLink_isNone() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.USER_FOLLOWING_LINK),
            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
        )
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    /**
     * Both participants of a one-to-one conversation are owners by design, so the rank carries no
     * information there.
     */
    @Test
    fun testRoleOf_ownerInOneToOneConversation_isNone() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.OWNER),
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        )
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    @Test
    fun testRoleOf_ownerInFormerOneToOneConversation_isNone() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.OWNER),
            ConversationEnums.ConversationType.FORMER_ONE_TO_ONE
        )
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    @Test
    fun testRoleOf_ownerInChangelogConversation_isNone() {
        val result = ParticipantRoleUtils.roleOf(
            participant(Participant.ParticipantType.OWNER),
            ConversationEnums.ConversationType.ROOM_SYSTEM
        )
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    @Test
    fun testRoleOf_unknownConversationType_isNone() {
        val result = ParticipantRoleUtils.roleOf(participant(Participant.ParticipantType.OWNER), null)
        Assert.assertEquals(ParticipantRole.NONE, result)
    }

    @Test
    fun testLabelAndIcon_areNullOnlyForNoRole() {
        Assert.assertNotNull(ParticipantRoleUtils.labelRes(ParticipantRole.OWNER))
        Assert.assertNotNull(ParticipantRoleUtils.iconRes(ParticipantRole.OWNER))
        Assert.assertNotNull(ParticipantRoleUtils.labelRes(ParticipantRole.MODERATOR))
        Assert.assertNotNull(ParticipantRoleUtils.iconRes(ParticipantRole.MODERATOR))
        Assert.assertNull(ParticipantRoleUtils.labelRes(ParticipantRole.NONE))
        Assert.assertNull(ParticipantRoleUtils.iconRes(ParticipantRole.NONE))
    }
}
