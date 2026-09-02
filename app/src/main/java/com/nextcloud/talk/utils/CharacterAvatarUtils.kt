/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import androidx.annotation.ColorRes
import com.nextcloud.talk.R
import java.util.Locale

/**
 * The avatar of an actor the server has no avatar for, drawn by the client instead of being
 * requested. Resolved by [CharacterAvatarUtils.avatarFor], rendered by
 * [com.nextcloud.talk.ui.ActorAvatarImage] in Compose and by
 * [com.nextcloud.talk.ui.CharacterAvatarDrawable] everywhere else.
 */
sealed interface ActorAvatar {

    /**
     * A character on a coloured circle: the first character of a guest's name, or the shell prompt
     * of a bot. Both colours are theme-aware resources, as the web client's are.
     */
    data class Character(
        val character: String,
        @ColorRes val backgroundColor: Int,
        @ColorRes val textColor: Int
    ) : ActorAvatar

    /**
     * The app's own icon, for the bots that ship their avatar with the app.
     */
    object AppIcon : ActorAvatar

    /**
     * The generic person icon, for guests who did not tell us their name.
     */
    object PersonIcon : ActorAvatar
}

/**
 * Decides what to draw for actors the server has no avatar for.
 *
 * Guests, email participants and bots have no avatar on the server, so theirs is rendered on the
 * client instead of being requested: a guest who told us their name gets the first character of it,
 * a bot gets a shell prompt. This mirrors the web client's AvatarWrapper, so both clients show the
 * same avatar for the same actor.
 *
 * A guest without a name is not represented by a character at all - they get the generic person
 * icon, because the placeholder name ("Guest") is the same for everyone in the conversation and its
 * initial would tell the reader nothing.
 */
object CharacterAvatarUtils {

    /**
     * Shell prompt drawn for bots, matching the web client.
     */
    const val BOT_CHARACTER = ">_"

    /**
     * Actor types without an avatar on the server. Both the plural spelling of the participant and
     * chat APIs and the singular one of the message parameters are accepted, so every caller can
     * pass the type it was given.
     */
    private val GUEST_ACTOR_TYPES = setOf("guests", "guest", "emails", "email")
    private val BOT_ACTOR_TYPES = setOf("bots", "bot")

    /**
     * Bots that ship their own avatar with the app and therefore never draw a character.
     */
    private val CHANGELOG_BOT_IDS = setOf("changelog", "sample")

    /**
     * What to draw for the given actor, or null when the actor has an avatar on the server and it
     * should be requested as usual.
     *
     * @param actorType the actor type as the API reports it, e.g. "guests" or "bots"
     * @param actorId the actor's id, only used to spot the bots shipping their own avatar
     * @param displayName the actor's display name, a guest's character is derived from it
     * @param guestLabel the localized placeholder name for unnamed guests, null to accept any name
     */
    fun avatarFor(actorType: String?, actorId: String?, displayName: String?, guestLabel: String?): ActorAvatar? =
        when (actorType) {
            in GUEST_ACTOR_TYPES -> guestAvatar(displayName, guestLabel)
            in BOT_ACTOR_TYPES -> botAvatar(actorId)
            else -> null
        }

    /**
     * The avatar of a guest or email participant, for callers whose actor type is already narrowed
     * down to those - the name is all that is left to decide.
     */
    fun guestAvatar(displayName: String?, guestLabel: String?): ActorAvatar =
        guestCharacter(displayName, guestLabel)
            ?.let {
                ActorAvatar.Character(
                    character = it,
                    backgroundColor = R.color.character_avatar_background_guest,
                    textColor = R.color.character_avatar_text_guest
                )
            }
            ?: ActorAvatar.PersonIcon

    /**
     * The avatar of a bot: the shell prompt, or the app's icon for the bots shipping their own.
     */
    fun botAvatar(actorId: String?): ActorAvatar =
        if (actorId in CHANGELOG_BOT_IDS) {
            ActorAvatar.AppIcon
        } else {
            ActorAvatar.Character(
                character = BOT_CHARACTER,
                backgroundColor = R.color.character_avatar_background_bot,
                textColor = R.color.character_avatar_text_bot
            )
        }

    /**
     * Whether the actor told us their name, as opposed to being labelled with the generic
     * placeholder every unnamed guest shares.
     *
     * @param displayName the actor's display name, may be null or blank
     * @param guestLabel the localized placeholder name for unnamed guests, null to accept any name
     */
    fun hasCustomName(displayName: String?, guestLabel: String?): Boolean {
        val name = displayName?.trim()
        return !name.isNullOrEmpty() && name != guestLabel?.trim()
    }

    /**
     * The character to draw for a guest or email actor, or null when there is no name to derive it
     * from and the generic person icon should be used instead.
     */
    private fun guestCharacter(displayName: String?, guestLabel: String?): String? {
        if (!hasCustomName(displayName, guestLabel)) {
            return null
        }
        return firstCharacterOf(displayName!!.trim().uppercase(Locale.getDefault()))
    }

    /**
     * First character as a whole code point, so names starting with an emoji or any other
     * character outside the basic plane do not get cut in half into an unrenderable fragment.
     */
    private fun firstCharacterOf(name: String): String = String(Character.toChars(name.codePointAt(0)))
}
