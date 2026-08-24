/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import org.junit.Assert
import org.junit.Test

@Suppress("TooManyFunctions")
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

    // region canChangeOwnership

    private fun capabilities(vararg features: String) =
        SpreedCapability(features = features.toList(), config = null, version = "")

    private val ownerCapability = capabilities(SpreedFeatures.PROMOTE_DEMOTE_OWNER.value)

    private fun conversation(
        selfType: Participant.ParticipantType = Participant.ParticipantType.OWNER,
        type: ConversationEnums.ConversationType = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
        objectType: ConversationEnums.ObjectType = ConversationEnums.ObjectType.DEFAULT
    ) = ConversationModel(
        internalId = "1@token",
        accountId = 1L,
        token = "token",
        name = "conversation",
        displayName = "Conversation",
        description = "",
        type = type,
        participantType = selfType,
        sessionId = "",
        actorId = "self",
        actorType = "users",
        objectType = objectType,
        notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
        conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
        lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
        lobbyTimer = 0L,
        canLeaveConversation = true,
        canDeleteConversation = true,
        unreadMentionDirect = false,
        notificationCalls = 0,
        avatarVersion = "",
        hasCustomAvatar = false,
        callStartTime = 0L
    )

    private fun actor(
        type: Participant.ParticipantType = Participant.ParticipantType.USER,
        actorType: Participant.ActorType = Participant.ActorType.USERS
    ) = Participant(actorType = actorType, actorId = "alice", displayName = "Alice", type = type)

    @Test
    fun testCanChangeOwnership_whenEverythingLinesUp_isTrue() {
        Assert.assertTrue(ParticipantRoleUtils.canChangeOwnership(actor(), conversation(), ownerCapability))
    }

    @Test
    fun testCanChangeOwnership_withoutTheCapability_isFalse() {
        Assert.assertFalse(ParticipantRoleUtils.canChangeOwnership(actor(), conversation(), capabilities()))
        Assert.assertFalse(ParticipantRoleUtils.canChangeOwnership(actor(), conversation(), null))
    }

    @Test
    fun testCanChangeOwnership_whenSelfIsNotOwner_isFalse() {
        val asModerator = conversation(selfType = Participant.ParticipantType.MODERATOR)
        Assert.assertFalse(ParticipantRoleUtils.canChangeOwnership(actor(), asModerator, ownerCapability))
    }

    @Test
    fun testCanChangeOwnership_withoutAConversation_isFalse() {
        Assert.assertFalse(ParticipantRoleUtils.canChangeOwnership(actor(), null, ownerCapability))
    }

    @Test
    fun testCanChangeOwnership_inPublicConversation_isTrue() {
        val public = conversation(type = ConversationEnums.ConversationType.ROOM_PUBLIC_CALL)
        Assert.assertTrue(ParticipantRoleUtils.canChangeOwnership(actor(), public, ownerCapability))
    }

    @Test
    fun testCanChangeOwnership_inConversationTypesWithoutRanks_isFalse() {
        val rankless = listOf(
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
            ConversationEnums.ConversationType.FORMER_ONE_TO_ONE,
            ConversationEnums.ConversationType.ROOM_SYSTEM,
            ConversationEnums.ConversationType.NOTE_TO_SELF
        )
        rankless.forEach { type ->
            Assert.assertFalse(
                "owner change must not be offered in $type",
                ParticipantRoleUtils.canChangeOwnership(actor(), conversation(type = type), ownerCapability)
            )
        }
    }

    @Test
    fun testCanChangeOwnership_inAllowedObjectTypes_isTrue() {
        val allowed = listOf(
            ConversationEnums.ObjectType.DEFAULT,
            ConversationEnums.ObjectType.CLASSIFIED,
            ConversationEnums.ObjectType.INSTANT_MEETING
        )
        allowed.forEach { objectType ->
            Assert.assertTrue(
                "owner change must be offered for $objectType",
                ParticipantRoleUtils.canChangeOwnership(
                    actor(),
                    conversation(objectType = objectType),
                    ownerCapability
                )
            )
        }
    }

    @Test
    fun testCanChangeOwnership_inObjectTypesWithAnImplicitOwner_isFalse() {
        val bound = listOf(
            ConversationEnums.ObjectType.SHARE_PASSWORD,
            ConversationEnums.ObjectType.FILE,
            ConversationEnums.ObjectType.ROOM,
            ConversationEnums.ObjectType.EVENT,
            ConversationEnums.ObjectType.PHONE_TEMPORARY,
            ConversationEnums.ObjectType.PHONE_PERSIST
        )
        bound.forEach { objectType ->
            Assert.assertFalse(
                "owner change must not be offered for $objectType",
                ParticipantRoleUtils.canChangeOwnership(
                    actor(),
                    conversation(objectType = objectType),
                    ownerCapability
                )
            )
        }
    }

    @Test
    fun testCanChangeOwnership_forActorsThatCanNeverBeOwners_isFalse() {
        val nonUsers = listOf(
            Participant.ActorType.GUESTS,
            Participant.ActorType.EMAILS,
            Participant.ActorType.GROUPS,
            Participant.ActorType.CIRCLES,
            Participant.ActorType.FEDERATED,
            Participant.ActorType.PHONES
        )
        nonUsers.forEach { actorType ->
            Assert.assertFalse(
                "$actorType can never hold the owner rank",
                ParticipantRoleUtils.canChangeOwnership(
                    actor(actorType = actorType),
                    conversation(),
                    ownerCapability
                )
            )
        }
    }

    // endregion

    // region canBePromotedToOwner / canBeDemotedFromOwner

    @Test
    fun testCanBePromotedToOwner_forPromotableRanks_isTrue() {
        val promotable = listOf(
            Participant.ParticipantType.USER,
            Participant.ParticipantType.USER_FOLLOWING_LINK,
            Participant.ParticipantType.MODERATOR
        )
        promotable.forEach { type ->
            Assert.assertTrue(
                "$type should be promotable to owner",
                ParticipantRoleUtils.canBePromotedToOwner(actor(type), conversation(), ownerCapability)
            )
        }
    }

    @Test
    fun testCanBePromotedToOwner_forRanksThatCannotBe_isFalse() {
        val notPromotable = listOf(
            Participant.ParticipantType.OWNER,
            Participant.ParticipantType.GUEST,
            Participant.ParticipantType.GUEST_MODERATOR,
            Participant.ParticipantType.DUMMY
        )
        notPromotable.forEach { type ->
            Assert.assertFalse(
                "$type should not be promotable to owner",
                ParticipantRoleUtils.canBePromotedToOwner(actor(type), conversation(), ownerCapability)
            )
        }
    }

    @Test
    fun testCanBeDemotedFromOwner_onlyAppliesToOwners() {
        Assert.assertTrue(
            ParticipantRoleUtils.canBeDemotedFromOwner(
                actor(Participant.ParticipantType.OWNER),
                conversation(),
                ownerCapability
            )
        )
        Assert.assertFalse(
            ParticipantRoleUtils.canBeDemotedFromOwner(
                actor(Participant.ParticipantType.MODERATOR),
                conversation(),
                ownerCapability
            )
        )
    }

    @Test
    fun testOwnerActions_withoutTheCapability_areAllFalse() {
        val noCapability = capabilities()
        Assert.assertFalse(
            ParticipantRoleUtils.canBePromotedToOwner(actor(), conversation(), noCapability)
        )
        Assert.assertFalse(
            ParticipantRoleUtils.canBeDemotedFromOwner(
                actor(Participant.ParticipantType.OWNER),
                conversation(),
                noCapability
            )
        )
    }

    // endregion
}
