/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.bundle.BundleKeys

/**
 * Helper class for managing Android shortcuts for conversations.
 *
 * Provides methods to create, update, and manage dynamic shortcuts that allow
 * users to quickly access conversations from their launcher.
 */
object ShortcutManagerHelper {

    private const val MAX_DYNAMIC_SHORTCUTS = 4
    private const val CONVERSATION_SHORTCUT_PREFIX = "conversation_"

    /**
     * Creates a shortcut for a conversation.
     *
     * @param context Application context
     * @param conversation The conversation to create a shortcut for
     * @param user The user account associated with the conversation
     * @return ShortcutInfoCompat ready to be added via ShortcutManagerCompat
     */
    fun createConversationShortcut(
        context: Context,
        conversation: ConversationModel,
        user: User
    ): ShortcutInfoCompat {
        val shortcutId = getShortcutId(conversation.token, user.id!!)
        val displayName = conversation.displayName.ifBlank { conversation.name }

        // Use custom URI scheme for the intent
        val uri = DeepLinkHandler.createConversationUri(conversation.token, user.id)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(context.packageName)
        }

        // Use the app icon as the shortcut icon - avatar loading would require async operations
        val icon = IconCompat.createWithResource(context, R.drawable.baseline_chat_bubble_outline_24)

        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(displayName)
            .setLongLabel(displayName)
            .setIcon(icon)
            .setIntent(intent)
            .build()
    }

    /**
     * Creates a shortcut using bundle extras (alternative to URI scheme).
     * This matches the existing Note To Self shortcut pattern.
     *
     * @param context Application context
     * @param conversation The conversation to create a shortcut for
     * @param user The user account associated with the conversation
     * @return ShortcutInfoCompat ready to be added via ShortcutManagerCompat
     */
    fun createConversationShortcutWithBundle(
        context: Context,
        conversation: ConversationModel,
        user: User
    ): ShortcutInfoCompat {
        val shortcutId = getShortcutId(conversation.token, user.id!!)
        val displayName = conversation.displayName.ifBlank { conversation.name }

        val bundle = Bundle().apply {
            putString(BundleKeys.KEY_ROOM_TOKEN, conversation.token)
            putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id!!)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtras(bundle)
        }

        val icon = IconCompat.createWithResource(context, R.drawable.baseline_chat_bubble_outline_24)

        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(displayName)
            .setLongLabel(displayName)
            .setIcon(icon)
            .setIntent(intent)
            .build()
    }

    /**
     * Updates dynamic shortcuts with the user's top conversations.
     * Excludes Note To Self (handled separately) and archived conversations.
     *
     * @param context Application context
     * @param conversations List of all conversations
     * @param user The current user
     */
    fun updateDynamicShortcuts(
        context: Context,
        conversations: List<ConversationModel>,
        user: User
    ) {
        // Remove existing conversation shortcuts (keep Note To Self shortcut)
        val existingShortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        val conversationShortcutIds = existingShortcuts
            .filter { it.id.startsWith(CONVERSATION_SHORTCUT_PREFIX) }
            .map { it.id }

        if (conversationShortcutIds.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, conversationShortcutIds)
        }

        // Get top conversations: favorites first, then by last activity
        val topConversations = conversations
            .filter { !it.hasArchived }
            .filter { !ConversationUtils.isNoteToSelfConversation(it) }
            .sortedWith(compareByDescending<ConversationModel> { it.favorite }.thenByDescending { it.lastActivity })
            .take(MAX_DYNAMIC_SHORTCUTS)

        // Create and push shortcuts
        topConversations.forEach { conversation ->
            val shortcut = createConversationShortcutWithBundle(context, conversation, user)
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    /**
     * Requests to pin a shortcut to the home screen.
     *
     * @param context Application context
     * @param conversation The conversation to create a pinned shortcut for
     * @param user The user account associated with the conversation
     * @return true if the pin request was successfully sent, false otherwise
     */
    fun requestPinShortcut(
        context: Context,
        conversation: ConversationModel,
        user: User
    ): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            // Fall back to legacy shortcut broadcast
            return createLegacyShortcut(context, conversation, user)
        }

        val shortcut = createConversationShortcutWithBundle(context, conversation, user)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * Creates a shortcut using the legacy broadcast method for older launchers.
     *
     * @param context Application context
     * @param conversation The conversation to create a shortcut for
     * @param user The user account associated with the conversation
     * @return true if the broadcast was sent successfully
     */
    @Suppress("DEPRECATION")
    private fun createLegacyShortcut(
        context: Context,
        conversation: ConversationModel,
        user: User
    ): Boolean {
        val displayName = conversation.displayName.ifBlank { conversation.name }

        val bundle = Bundle().apply {
            putString(BundleKeys.KEY_ROOM_TOKEN, conversation.token)
            putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id!!)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtras(bundle)
        }

        val shortcutIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName)
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher)
            )
        }

        return try {
            context.sendBroadcast(shortcutIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reports that a shortcut has been used (helps with shortcut ranking).
     *
     * @param context Application context
     * @param roomToken The conversation token
     * @param userId The user ID
     */
    fun reportShortcutUsed(context: Context, roomToken: String, userId: Long) {
        val shortcutId = getShortcutId(roomToken, userId)
        ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
    }

    /**
     * Removes a specific conversation shortcut.
     *
     * @param context Application context
     * @param roomToken The conversation token
     * @param userId The user ID
     */
    fun removeConversationShortcut(context: Context, roomToken: String, userId: Long) {
        val shortcutId = getShortcutId(roomToken, userId)
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
    }

    /**
     * Generates a unique shortcut ID for a conversation.
     */
    private fun getShortcutId(roomToken: String, userId: Long): String {
        return "${CONVERSATION_SHORTCUT_PREFIX}${userId}_$roomToken"
    }
}
