/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel

/**
 * Helper class for managing Android shortcuts for conversations.
 *
 * Provides methods to create, update, and manage dynamic shortcuts that allow
 * users to quickly access conversations from their launcher.
 */
object ShortcutManagerHelper {

    private const val TAG = "ShortcutManagerHelper"
    private const val MAX_DYNAMIC_SHORTCUTS = 4
    private const val CONVERSATION_SHORTCUT_PREFIX = "conversation_"

    /**
     * Creates a shortcut for a conversation using a nextcloudtalk:// deep link URI.
     * This ensures proper multi-account user resolution when the shortcut is opened.
     *
     * @param context Application context
     * @param conversation The conversation to create a shortcut for
     * @param user The user account associated with the conversation
     * @return ShortcutInfoCompat ready to be added, or null if user ID is invalid
     */
    fun createConversationShortcut(context: Context, conversation: ConversationModel, user: User): ShortcutInfoCompat? {
        val userId = user.id
        val baseUrl = user.baseUrl
        val uri = baseUrl?.let {
            DeepLinkHandler.createConversationUri(conversation.token, it, user.username)
                .takeIf { built -> built != Uri.EMPTY }
        }
        if (userId == null || uri == null) return null

        val displayName = conversation.displayName.ifBlank { conversation.name }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
        }

        return ShortcutInfoCompat.Builder(context, getShortcutId(conversation.token, userId))
            .setShortLabel(displayName)
            .setLongLabel(displayName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.baseline_chat_bubble_outline_24))
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
    fun updateDynamicShortcuts(context: Context, conversations: List<ConversationModel>, user: User) {
        val userId = user.id ?: run {
            Log.w(TAG, "Cannot update shortcuts: user ID is null")
            return
        }

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
            createConversationShortcut(context, conversation, user)?.let { shortcut ->
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            }
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
    fun requestPinShortcut(context: Context, conversation: ConversationModel, user: User): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return createLegacyShortcut(context, conversation, user)
        }

        val shortcut = createConversationShortcut(context, conversation, user)
        return shortcut != null && ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
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
    private fun createLegacyShortcut(context: Context, conversation: ConversationModel, user: User): Boolean {
        if (user.id == null || user.baseUrl == null) {
            Log.w(TAG, "Cannot create legacy shortcut: user ID or base URL is null")
            return false
        }

        val displayName = conversation.displayName.ifBlank { conversation.name }

        val uri = DeepLinkHandler.createConversationUri(
            roomToken = conversation.token,
            serverUrl = user.baseUrl!!,
            username = user.username
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
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
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to create legacy shortcut: permission denied", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to create legacy shortcut: invalid arguments", e)
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
     * Disables all shortcuts (dynamic and pinned) for a deleted conversation.
     * Dynamic shortcuts are removed; pinned shortcuts are disabled with a message.
     *
     * @param context Application context
     * @param roomToken The conversation token
     * @param userId The user ID
     * @param disabledMessage Message shown when a disabled pinned shortcut is tapped
     */
    fun disableConversationShortcut(context: Context, roomToken: String, userId: Long, disabledMessage: String) {
        val shortcutId = getShortcutId(roomToken, userId)
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
        ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId), disabledMessage)
    }

    /**
     * Generates a unique shortcut ID for a conversation.
     */
    private fun getShortcutId(roomToken: String, userId: Long): String =
        "${CONVERSATION_SHORTCUT_PREFIX}${userId}_$roomToken"
}
