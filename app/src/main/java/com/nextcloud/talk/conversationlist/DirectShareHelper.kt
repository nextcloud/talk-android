/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-FileCopyrightText: 2026 Jens Zalzala <jens@shakingearthdigital.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys

object DirectShareHelper {

    private val TAG = DirectShareHelper::class.java.simpleName
    private const val SHARE_TARGET_CATEGORY = "com.nextcloud.talk.sharing.SHARE_TARGET_CATEGORY"
    private const val SHORTCUT_ID_PREFIX = "direct_share_"
    private const val SHORTCUT_ID_MIN_PARTS = 4
    private const val AVATAR_SIZE_PX = 256

    private enum class MessageDirection { NONE, SEND, RECEIVE }

    suspend fun publishShareTargetShortcuts(context: Context, user: User, conversations: List<ConversationModel>) {
        val maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)

        // Preserve shortcuts not managed by DirectShareHelper (e.g. Note to Self).
        val existingOtherShortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
            .filter { !it.id.startsWith(SHORTCUT_ID_PREFIX) }
        val limit = (maxShortcuts - existingOtherShortcuts.size).coerceAtLeast(0)

        @Suppress("MagicNumber")
        val thirtyDaysAgoSeconds = System.currentTimeMillis() / 1000 - 30 * 24 * 60 * 60
        val afterFilter = conversations
            .filter { !ConversationUtils.isNoteToSelfConversation(it) }
            .filter { it.lastActivity >= thirtyDaysAgoSeconds }
        val candidates = afterFilter
            .sortedByDescending { it.lastActivity }
            .take(limit)

        val credentials = ApiUtils.getCredentials(user.username, user.token)
        // Build shortcuts most-recent-first; setDynamicShortcuts uses index order (0 = most important).
        val shareShortcuts = candidates.map { conversation ->
            val displayName = conversation.displayName
            val icon = loadAvatarIcon(context, user, conversation.token, displayName, credentials)
            prepShortcutBuilder(context, user, conversation.token, displayName, icon).build()
        }

        ShortcutManagerCompat.setDynamicShortcuts(context, shareShortcuts + existingOtherShortcuts)
    }

    /**
     * Reports an incoming message for the given conversation, improving its share sheet ranking.
     * Per Android docs, communication apps should republish the shortcut via pushDynamicShortcut
     * when a message is received.
     */
    suspend fun reportIncomingMessage(
        context: Context,
        user: User,
        token: String,
        displayName: String,
        isOneToOne: Boolean
    ) {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val icon = loadAvatarIcon(context, user, token, displayName, credentials)
        val shortcut = prepShortcutBuilder(context, user, token, displayName, icon)
            .also { applyCapabilityBinding(it, MessageDirection.RECEIVE, isOneToOne) }
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Reports an outgoing message for the given conversation. Per Android docs, outgoing messages
     * should republish the shortcut via pushDynamicShortcut with SEND_MESSAGE capability binding.
     */
    suspend fun reportOutgoingMessage(
        context: Context,
        user: User,
        token: String,
        displayName: String,
        isOneToOne: Boolean
    ) {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val icon = loadAvatarIcon(context, user, token, displayName, credentials)
        val shortcut = prepShortcutBuilder(context, user, token, displayName, icon)
            .also { applyCapabilityBinding(it, MessageDirection.SEND, isOneToOne) }
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Removes all direct-share shortcuts belonging to the user.
     * Called on logout/Remove account
     */
    fun removeShortcutsForUser(context: Context, userId: Long) {
        val prefix = "${SHORTCUT_ID_PREFIX}${userId}_"
        val ids = ShortcutManagerCompat.getDynamicShortcuts(context)
            .map { it.id }
            .filter { it.startsWith(prefix) }
        if (ids.isNotEmpty()) {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        }
    }

    /**
     * Removes the shortcut for a conversation.
     * Called when a conversation is deleted or left.
     */
    fun removeShortcutForConversation(context: Context, userId: Long, token: String) {
        val id = "${SHORTCUT_ID_PREFIX}${userId}_$token"
        ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(id))
    }

    /**
     * Removes all direct-share shortcuts.
     * Called on account switch.
     */
    fun removeAllShareTargetShortcuts(context: Context) {
        val ids = ShortcutManagerCompat.getDynamicShortcuts(context)
            .map { it.id }
            .filter { it.startsWith(SHORTCUT_ID_PREFIX) }
        if (ids.isNotEmpty()) {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        }
    }

    /**
     * Parses the room token from a shortcut ID of the form "direct_share_{userId}_{token}".
     * Returns null if the ID does not belong to this app's direct share shortcuts.
     */
    fun extractTokenFromShortcutId(shortcutId: String): String? {
        if (!shortcutId.startsWith(SHORTCUT_ID_PREFIX)) return null
        val parts = shortcutId.split("_")
        return if (parts.size < SHORTCUT_ID_MIN_PARTS) null
        else parts.drop(SHORTCUT_ID_MIN_PARTS - 1).joinToString("_")
    }

    private fun shortcutId(user: User, token: String): String =
        "$SHORTCUT_ID_PREFIX${user.id}_$token"

    private suspend fun loadAvatarIcon(
        context: Context,
        user: User,
        token: String,
        displayName: String,
        credentials: String?
    ): IconCompat {
        return try {
            val avatarUrl = ApiUtils.getUrlForConversationAvatar(
                version = 1,
                baseUrl = user.baseUrl,
                token = token
            )
            val requestBuilder = ImageRequest.Builder(context)
                .data(avatarUrl)
                .size(AVATAR_SIZE_PX)
                .allowHardware(false)
                .transformations(CircleCropTransformation())
            if (credentials != null) {
                requestBuilder.addHeader("Authorization", credentials)
            }
            val result = context.imageLoader.execute(requestBuilder.build())
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                IconCompat.createWithBitmap(bitmap)
            } else {
                defaultIcon(context)
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Failed to load avatar for shortcut: $displayName", e)
            defaultIcon(context)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid avatar request for shortcut: $displayName", e)
            defaultIcon(context)
        }
    }

    private fun defaultIcon(context: Context): IconCompat =
        IconCompat.createWithResource(context, R.drawable.ic_circular_group)

    private fun prepShortcutBuilder(
        context: Context,
        user: User,
        token: String,
        displayName: String,
        icon: IconCompat
    ): ShortcutInfoCompat.Builder {
        // ACTION_VIEW is used so that tapping this shortcut on the home screen opens the
        // conversation directly. When used from the share sheet, Android delivers ACTION_SEND
        // with the shared content and EXTRA_SHORTCUT_ID, not this intent.
        val intent = Intent(Intent.ACTION_VIEW, null, context, ConversationsListActivity::class.java).apply {
            putExtra(BundleKeys.KEY_ROOM_TOKEN, token)
            putExtra(BundleKeys.KEY_INTERNAL_USER_ID, user.id)
        }

        val person = Person.Builder()
            .setName(displayName)
            .setIcon(icon)
            .setKey(shortcutId(user, token))
            .build()

        return ShortcutInfoCompat.Builder(context, shortcutId(user, token))
            .setShortLabel(displayName)
            .setLongLabel(displayName)
            .setIcon(icon)
            .setPerson(person)
            .setCategories(setOf(SHARE_TARGET_CATEGORY))
            .setIntent(intent)
            .setIsConversation()
            .setLongLived(true)
    }

    private fun applyCapabilityBinding(
        builder: ShortcutInfoCompat.Builder,
        direction: MessageDirection,
        isOneToOne: Boolean
    ) {
        when (direction) {
            MessageDirection.SEND -> if (isOneToOne) {
                builder.addCapabilityBinding("actions.intent.SEND_MESSAGE")
            } else {
                builder.addCapabilityBinding(
                    "actions.intent.SEND_MESSAGE",
                    "message.recipient.@type",
                    listOf("Audience")
                )
            }
            MessageDirection.RECEIVE -> if (isOneToOne) {
                builder.addCapabilityBinding("actions.intent.RECEIVE_MESSAGE")
            } else {
                builder.addCapabilityBinding(
                    "actions.intent.RECEIVE_MESSAGE",
                    "message.sender.@type",
                    listOf("Audience")
                )
            }
            MessageDirection.NONE -> Unit
        }
    }
}
