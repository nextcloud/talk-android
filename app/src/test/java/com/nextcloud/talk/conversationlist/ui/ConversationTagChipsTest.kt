/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.tags.ConversationTagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTagChipsTest {

    private val workTag = ConversationTagDto(id = "work", name = "Work")
    private val favoritesTag = ConversationTagDto(id = "fav", name = "", type = ConversationTagDto.TYPE_FAVORITES)

    @Test
    fun `counts conversations with unread messages, not the unread messages themselves`() {
        val rooms = listOf(
            conversation("a", tagIds = listOf("work"), unreadMessages = 5),
            conversation("b", tagIds = listOf("work"), unreadMessages = 1),
            conversation("c", tagIds = listOf("work"), unreadMessages = 0),
            conversation("d", tagIds = listOf("other"), unreadMessages = 3)
        )

        val info = chips(listOf(workTag), rooms).single().unreadInfo!!

        assertEquals(2, info.conversationCount)
        assertEquals(UnreadBadgeStyle.GREY, info.style)
    }

    @Test
    fun `no unread info when no tagged conversation is unread`() {
        val rooms = listOf(conversation("a", tagIds = listOf("work"), unreadMessages = 0))

        assertNull(chips(listOf(workTag), rooms).single().unreadInfo)
    }

    @Test
    fun `tags without conversations get no chip`() {
        val rooms = listOf(conversation("a", tagIds = listOf("work"), unreadMessages = 1))

        assertEquals(listOf(workTag), chips(listOf(workTag, favoritesTag), rooms).map { it.tag })
    }

    @Test
    fun `duplicate conversations are counted once`() {
        val room = conversation("a", tagIds = listOf("work"), unreadMessages = 1)

        assertEquals(1, chips(listOf(workTag), listOf(room, room.copy())).single().unreadInfo!!.conversationCount)
    }

    @Test
    fun `favorites tag counts unread favorite conversations`() {
        val rooms = listOf(
            conversation("a", favorite = true, unreadMessages = 2),
            conversation("b", favorite = false, unreadMessages = 2)
        )

        assertEquals(1, chips(listOf(favoritesTag), rooms).single().unreadInfo!!.conversationCount)
    }

    @Test
    fun `direct mention wins over group mention and plain unread`() {
        val rooms = listOf(
            conversation("a", tagIds = listOf("work"), unreadMessages = 1),
            conversation("b", tagIds = listOf("work"), unreadMessages = 1, unreadMention = true),
            conversation(
                "c",
                tagIds = listOf("work"),
                unreadMessages = 1,
                unreadMention = true,
                unreadMentionDirect = true
            )
        )

        assertEquals(UnreadBadgeStyle.FILLED, chips(listOf(workTag), rooms).single().unreadInfo!!.style)
    }

    @Test
    fun `group mention is outlined with the direct mention flag`() {
        val rooms = listOf(
            conversation("a", tagIds = listOf("work"), unreadMessages = 1),
            conversation("b", tagIds = listOf("work"), unreadMessages = 1, unreadMention = true)
        )

        assertEquals(UnreadBadgeStyle.OUTLINED, chips(listOf(workTag), rooms).single().unreadInfo!!.style)
        assertEquals(
            "without the flag every mention is treated as direct",
            UnreadBadgeStyle.FILLED,
            chips(listOf(workTag), rooms, hasDirectMentionFlag = false).single().unreadInfo!!.style
        )
    }

    @Test
    fun `unread one-to-one conversation is highlighted like a direct mention`() {
        val rooms = listOf(
            conversation(
                "a",
                tagIds = listOf("work"),
                unreadMessages = 1,
                type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
            )
        )

        assertEquals(UnreadBadgeStyle.FILLED, chips(listOf(workTag), rooms).single().unreadInfo!!.style)
    }

    private fun chips(
        tags: List<ConversationTagDto>,
        rooms: List<ConversationModel>,
        hasDirectMentionFlag: Boolean = true
    ) = buildConversationTagChips(tags, rooms, hasDirectMentionFlag)

    @Suppress("LongParameterList")
    private fun conversation(
        token: String,
        tagIds: List<String> = emptyList(),
        favorite: Boolean = false,
        unreadMessages: Int = 0,
        unreadMention: Boolean = false,
        unreadMentionDirect: Boolean = false,
        type: ConversationEnums.ConversationType = ConversationEnums.ConversationType.ROOM_GROUP_CALL
    ): ConversationModel =
        ConversationModel.mapToConversationModel(
            ConversationDto(token = token, name = token, type = type),
            User(id = 1L, userId = "me", username = "me", baseUrl = "https://server.example.com")
        ).copy(
            tagIds = tagIds,
            favorite = favorite,
            unreadMessages = unreadMessages,
            unreadMention = unreadMention,
            unreadMentionDirect = unreadMentionDirect
        )
}
