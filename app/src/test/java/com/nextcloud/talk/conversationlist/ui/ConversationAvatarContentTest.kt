/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAvatarContentTest {

    @Test
    fun `group rooms use the versioned conversation avatar endpoint with the avatar capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_GROUP_CALL, avatarVersion = "5"),
            user(withAvatarCapability = true),
            isDark = false
        )

        val url = content as AvatarContent.Url
        assertTrue(url.versioned)
        assertTrue(url.url.contains("/room/$ROOM_TOKEN/avatar"))
        assertTrue(url.url.contains("avatarVersion=5"))
    }

    @Test
    fun `one-to-one rooms use the unversioned conversation avatar endpoint with the avatar capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL, avatarVersion = "3"),
            user(withAvatarCapability = true),
            isDark = false
        )

        val url = content as AvatarContent.Url
        assertFalse("one-to-one avatars are outside the version scheme", url.versioned)
        assertTrue(url.url.contains("/room/$ROOM_TOKEN/avatar"))
        assertFalse(url.url.contains("avatarVersion"))
    }

    @Test
    fun `one-to-one endpoint selection depends on the capability only, not on the avatar version`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL, avatarVersion = ""),
            user(withAvatarCapability = true),
            isDark = false
        )

        val url = content as AvatarContent.Url
        assertFalse(url.versioned)
        assertTrue(url.url.contains("/room/$ROOM_TOKEN/avatar"))
    }

    @Test
    fun `one-to-one avatar urls never carry an avatar version parameter`() {
        listOf(
            buildAvatarContent(
                conversation(ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL, avatarVersion = "7"),
                user(withAvatarCapability = true),
                isDark = false
            ),
            buildAvatarContent(
                conversation(ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL, avatarVersion = "7"),
                user(withAvatarCapability = false),
                isDark = false
            )
        ).forEach { content ->
            val url = content as AvatarContent.Url
            assertFalse(url.versioned)
            assertFalse(url.url.contains("avatarVersion"))
        }
    }

    @Test
    fun `one-to-one rooms fall back to the unversioned user avatar endpoint without the capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL),
            user(withAvatarCapability = false),
            isDark = false
        )

        // only the endpoint path is asserted: the name segment needs Uri.encode, which is stubbed here
        val url = content as AvatarContent.Url
        assertFalse(url.versioned)
        assertTrue(url.url.contains("/index.php/avatar/"))
    }

    @Test
    fun `group rooms fall back to the themed default icon without the capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_GROUP_CALL),
            user(withAvatarCapability = false),
            isDark = false
        )

        assertEquals(AvatarContent.Res(R.drawable.ic_circular_group), content)
    }

    @Test
    fun `public rooms fall back to the themed default icon without the capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_PUBLIC_CALL),
            user(withAvatarCapability = false),
            isDark = false
        )

        assertEquals(AvatarContent.Res(R.drawable.ic_circular_link), content)
    }

    @Test
    fun `an empty avatar version keeps the fallback even with the capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.ROOM_GROUP_CALL, avatarVersion = ""),
            user(withAvatarCapability = true),
            isDark = false
        )

        assertEquals(AvatarContent.Res(R.drawable.ic_circular_group), content)
    }

    @Test
    fun `note to self is independent of the avatar capability`() {
        val content = buildAvatarContent(
            conversation(ConversationEnums.ConversationType.NOTE_TO_SELF, avatarVersion = "5"),
            user(withAvatarCapability = true),
            isDark = false
        )

        assertEquals(AvatarContent.NoteToSelf, content)
    }

    private fun conversation(type: ConversationEnums.ConversationType, avatarVersion: String = ""): ConversationModel =
        ConversationModel.mapToConversationModel(
            Conversation(
                token = ROOM_TOKEN,
                name = PEER_NAME,
                type = type,
                avatarVersion = avatarVersion
            ),
            user(withAvatarCapability = true)
        )

    private fun user(withAvatarCapability: Boolean): User {
        val features = if (withAvatarCapability) listOf("avatar") else emptyList()
        return User(
            id = 1L,
            userId = "me",
            username = "me",
            baseUrl = "https://server.example.com",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply { this.features = features }
            }
        )
    }

    companion object {
        private const val ROOM_TOKEN = "room1"
        private const val PEER_NAME = "peer"
    }
}
