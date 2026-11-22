/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import androidx.annotation.ColorRes
import com.nextcloud.talk.R

/**
 * The avatar of an actor the server has no avatar for, drawn by the client instead of being
 * requested. Resolved by [CharacterAvatarUtils.avatarFor], rendered by
 * [com.nextcloud.talk.ui.ActorAvatarImage] in Compose and by
 * [com.nextcloud.talk.ui.CharacterAvatarDrawable] everywhere else.
 */
sealed interface ActorAvatar {

    /**
     * A bot's shell prompt on a coloured circle. Both colours are theme-aware resources.
     */
    data class Character(val character: String, @ColorRes val backgroundColor: Int, @ColorRes val textColor: Int) :
        ActorAvatar

    /**
     * The app's own icon, for the bots that ship their avatar with the app.
     */
    object AppIcon : ActorAvatar

    /**
     * The generic person icon for people without a server-provided avatar.
     */
    object PersonIcon : ActorAvatar
}

/**
 * Decides what to draw for actors the server has no avatar for.
 *
 * Guests and email participants use the generic person icon. Bots retain their shell prompt or
 * bundled app icon. Registered and federated users are left to the server's avatar response.
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
     * Former logged-in users whose account was deleted. The server hardcodes their actor id to
     * "deleted_users" and never returns an avatar for them.
     */
    private val DELETED_USER_ACTOR_TYPES = setOf("deleted_users")

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
     * @param displayName the actor's display name; does not affect a guest's person icon
     * @param guestLabel the localized placeholder name; does not affect a guest's person icon
     */
    fun avatarFor(actorType: String?, actorId: String?, displayName: String?, guestLabel: String?): ActorAvatar? =
        when (actorType) {
            in GUEST_ACTOR_TYPES -> guestAvatar(displayName, guestLabel)
            in BOT_ACTOR_TYPES -> botAvatar(actorId)
            in DELETED_USER_ACTOR_TYPES -> ActorAvatar.PersonIcon
            else -> null
        }

    /**
     * The avatar of a guest or email participant, independent of their display name.
     */
    @Suppress("UNUSED_PARAMETER")
    fun guestAvatar(displayName: String?, guestLabel: String?): ActorAvatar = ActorAvatar.PersonIcon

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
}
