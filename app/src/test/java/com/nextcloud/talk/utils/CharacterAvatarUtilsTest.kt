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
import org.junit.Assert.assertNotEquals
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
    fun `named guest is drawn from the first character of their name`() {
        assertEquals("A", characterOf("guests", "guest-hash", "alice"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("B", characterOf("guests", "guest-hash", "  bob "))
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
    fun `any name counts as custom when no placeholder name is given`() {
        val avatar = CharacterAvatarUtils.guestAvatar("Guest", null)
        assertEquals("G", (avatar as ActorAvatar.Character).character)
    }

    @Test
    fun `first character outside the basic plane stays whole`() {
        val name = "😀 party"
        assertEquals(name.substring(0, 2), characterOf("guests", "guest-hash", name))
    }

    @Test
    fun `both the plural and the singular spelling of an actor type are understood`() {
        assertEquals("T", characterOf("guests", "guest-hash", "Test"))
        assertEquals("T", characterOf("guest", "guest-hash", "Test"))
        assertEquals("T", characterOf("emails", "email-hash", "Test"))
        assertEquals("T", characterOf("email", "email-hash", "Test"))
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
    fun `guests are drawn in the guest colours`() {
        val avatar = CharacterAvatarUtils.guestAvatar("alice", guestLabel) as ActorAvatar.Character
        assertEquals(R.color.character_avatar_background_guest, avatar.backgroundColor)
        assertEquals(R.color.character_avatar_text_guest, avatar.textColor)
    }

    @Test
    fun `bots and guests do not share a colour pair`() {
        val bot = CharacterAvatarUtils.botAvatar("weather-bot") as ActorAvatar.Character
        val guest = CharacterAvatarUtils.guestAvatar("alice", guestLabel) as ActorAvatar.Character
        assertNotEquals(bot.backgroundColor, guest.backgroundColor)
        assertNotEquals(bot.textColor, guest.textColor)
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
