/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAvatarUtilsTest {

    private val guestLabel = "Guest"

    private fun characterOf(actorType: String?, actorId: String?, displayName: String?): String? =
        (CharacterAvatarUtils.avatarFor(actorType, actorId, displayName, guestLabel) as? ActorAvatar.Character)
            ?.character

    @Test
    fun `named guest gets the person icon instead of initials`() {
        assertSame(
            ActorAvatar.PersonIcon,
            CharacterAvatarUtils.avatarFor("guests", "guest-hash", "Alice Smith", guestLabel)
        )
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("  bob ", guestLabel))
    }

    @Test
    fun `guest without a name gets the person icon`() {
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar(null, guestLabel))
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("", guestLabel))
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("   ", guestLabel))
    }

    @Test
    fun `guest labelled with the placeholder name gets the person icon`() {
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar(guestLabel, guestLabel))
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar(" Guest ", guestLabel))
    }

    @Test
    fun `a missing placeholder label cannot bring back guest initials`() {
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("Guest", null))
    }

    @Test
    fun `Persian and emoji guest names use the same person icon`() {
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("مریم احمدی", guestLabel))
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("😀 party", guestLabel))
    }

    @Test
    fun `both the plural and the singular spelling of an actor type are understood`() {
        for (type in listOf("guests", "guest", "emails", "email")) {
            assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.avatarFor(type, "guest-hash", "Test", guestLabel))
        }
        assertEquals(">_", characterOf("bots", "weather-bot", "Weather"))
        assertEquals(">_", characterOf("bot", "weather-bot", "Weather"))
    }

    @Test
    fun `bots are drawn as a shell prompt in the bot colours`() {
        val avatar = CharacterAvatarUtils.botAvatar("weather-bot") as ActorAvatar.Character
        assertEquals(CharacterAvatarUtils.BOT_CHARACTER, avatar.character)
        assertEquals(R.color.character_avatar_background_bot, avatar.backgroundColor)
        assertEquals(R.color.character_avatar_text_bot, avatar.textColor)
    }

    @Test
    fun `deleted users keep the generic person icon`() {
        assertSame(
            ActorAvatar.PersonIcon,
            CharacterAvatarUtils.avatarFor("deleted_users", "deleted_users", "Alice", guestLabel)
        )
    }

    @Test
    fun `the new person icon does not replace bot characters`() {
        assertTrue(CharacterAvatarUtils.botAvatar("weather-bot") is ActorAvatar.Character)
        assertSame(ActorAvatar.PersonIcon, CharacterAvatarUtils.guestAvatar("alice", guestLabel))
    }

    @Test
    fun `bots shipping their own avatar are drawn with the app icon`() {
        assertSame(ActorAvatar.AppIcon, CharacterAvatarUtils.botAvatar("changelog"))
        assertSame(ActorAvatar.AppIcon, CharacterAvatarUtils.botAvatar("sample"))
        assertSame(ActorAvatar.AppIcon, CharacterAvatarUtils.avatarFor("bots", "changelog", "Changelog", guestLabel))
    }

    @Test
    fun `actors with an avatar on the server are left to the server`() {
        assertNull(CharacterAvatarUtils.avatarFor("users", "alice", "Alice", guestLabel))
        assertNull(CharacterAvatarUtils.avatarFor("federated_users", "alice@cloud", "Alice", guestLabel))
        assertNull(CharacterAvatarUtils.avatarFor(null, null, "Test", guestLabel))
    }

    @Test
    fun `custom name detection matches the placeholder name exactly`() {
        assertTrue(CharacterAvatarUtils.hasCustomName("Alice", guestLabel))
        assertTrue(CharacterAvatarUtils.hasCustomName("Guest of honour", guestLabel))
        assertFalse(CharacterAvatarUtils.hasCustomName("Guest", guestLabel))
        assertFalse(CharacterAvatarUtils.hasCustomName(null, guestLabel))
    }
}
