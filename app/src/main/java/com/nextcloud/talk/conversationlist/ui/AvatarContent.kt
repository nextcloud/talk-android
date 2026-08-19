/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

import androidx.annotation.DrawableRes
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.SpreedFeatures

internal sealed class AvatarContent {
    /**
     * [versioned] marks an immutable avatar URL: the avatarVersion parameter is the invalidation
     * token, so the image may be cached without expiry - a new version changes the URL. Only
     * conversation avatars are versioned; a one-to-one room's avatar is the peer's user avatar,
     * which is outside the version scheme and must revalidate via the response cache headers.
     */
    data class Url(val url: String, val versioned: Boolean) : AvatarContent()
    data class Res(@param:DrawableRes val resId: Int) : AvatarContent()
    object System : AvatarContent()
    object NoteToSelf : AvatarContent()
}

/**
 * Resolves what to show as a conversation's avatar.
 *
 * On servers with the avatar capability (Talk 17+) rooms use the conversation-avatar endpoint.
 * For everything but one-to-one rooms the URL carries the avatarVersion as invalidation token,
 * so those avatars are immutable content refreshed solely by version changes from the room list
 * sync. One-to-one rooms get the peer's user avatar from the same endpoint (which also handles
 * federation proxying), but their avatarVersion is a server-side constant that never changes
 * when the peer updates their avatar - their URL therefore stays unversioned and relies on the
 * default loader's header-driven revalidation. Servers without the capability fall back to the
 * unversioned user-avatar endpoint for one-to-one rooms and to themed default icons for group
 * and public rooms, whose endpoint does not exist there.
 */
internal fun buildAvatarContent(model: ConversationModel, currentUser: User, isDark: Boolean): AvatarContent {
    val hasConversationAvatars = currentUser.hasSpreedFeatureCapability(SpreedFeatures.AVATAR.value)
    val avatarVersion = model.avatarVersion.takeIf { it.isNotEmpty() }

    return when {
        model.objectType == ConversationEnums.ObjectType.SHARE_PASSWORD ->
            AvatarContent.Res(R.drawable.ic_circular_lock)

        model.objectType == ConversationEnums.ObjectType.FILE ->
            AvatarContent.Res(R.drawable.ic_avatar_document)

        model.type == ConversationEnums.ConversationType.ROOM_SYSTEM ->
            AvatarContent.System

        model.type == ConversationEnums.ConversationType.NOTE_TO_SELF ->
            AvatarContent.NoteToSelf

        hasConversationAvatars && model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ->
            AvatarContent.Url(
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    1,
                    currentUser.baseUrl,
                    model.token,
                    isDark,
                    null
                ),
                versioned = false
            )

        hasConversationAvatars && avatarVersion != null ->
            AvatarContent.Url(
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    1,
                    currentUser.baseUrl,
                    model.token,
                    isDark,
                    avatarVersion
                ),
                versioned = true
            )

        model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            model.type == ConversationEnums.ConversationType.FORMER_ONE_TO_ONE ->
            AvatarContent.Url(
                ApiUtils.getUrlForAvatar(currentUser.baseUrl, model.name, false, isDark),
                versioned = false
            )

        model.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ->
            AvatarContent.Res(R.drawable.ic_circular_group)

        model.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
            AvatarContent.Res(R.drawable.ic_circular_link)

        else ->
            AvatarContent.Res(R.drawable.account_circle_96dp)
    }
}
