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
import com.nextcloud.talk.activities.DeepLinkActivity
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
        val intent = createConversationIntent(context, conversation.token, user)
        if (userId == null || intent == null) return null

        val displayName = conversation.displayName.ifBlank { conversation.name }

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
     * Gives the conversation shortcuts that target the launcher activity with only the room token and user id as
     * extras the deep link of their conversation, like [createConversationShortcut]: the launcher activity takes no
     * extras from intents of outside the app, so such a shortcut opens only the conversation list. Pinned ones, e.g.
     * on the home screen or in a conversation widget, and cached ones keep their intent until their conversation has a
     * notification or is among the dynamic shortcuts.
     *
     * @param context Application context
     * @param users The accounts whose shortcuts can be updated
     */
    fun updateShortcutsWithoutDeepLink(context: Context, users: List<User>) {
        try {
            val updated = shortcutsWithDeepLinkFor(context, users)
            if (updated.isNotEmpty()) {
                ShortcutManagerCompat.updateShortcuts(context, updated)
            }
        } catch (e: IllegalArgumentException) {
            // e.g. for a shortcut that can't be updated. Old shortcuts must not crash the conversation list.
            Log.w(TAG, "Failed to update the shortcuts without a deep link", e)
        } catch (e: IllegalStateException) {
            // Thrown while the user is locked
            Log.w(TAG, "Failed to update the shortcuts without a deep link", e)
        }
    }

    /** The shortcuts of [updateShortcutsWithoutDeepLink] with the deep link of their conversation. */
    private fun shortcutsWithDeepLinkFor(context: Context, users: List<User>): List<ShortcutInfoCompat> {
        val usersById = users.associateBy { it.id }
        val matchFlags = ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED or
            ShortcutManagerCompat.FLAG_MATCH_CACHED
        return ShortcutManagerCompat.getShortcuts(context, matchFlags)
            .filter { it.id.startsWith(CONVERSATION_SHORTCUT_PREFIX) && isWithoutDeepLink(it.intent) }
            .mapNotNull { shortcut ->
                val user = usersById[shortcut.intent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L)]
                val roomToken = shortcut.intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)
                val intent = roomToken?.let { token -> user?.let { createConversationIntent(context, token, it) } }
                // the system keeps the other fields of a shortcut that is updated, e.g. its icon and person
                intent?.let {
                    ShortcutInfoCompat.Builder(context, shortcut.id)
                        .setShortLabel(shortcut.shortLabel)
                        .setIntent(it)
                        .build()
                }
            }
    }

    private fun isWithoutDeepLink(intent: Intent): Boolean =
        intent.component?.className == AppTasks.LAUNCHER_ACTIVITY && intent.data == null

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
        val launchIntent = createConversationIntent(context, conversation.token, user)
        if (launchIntent == null) {
            Log.w(TAG, "Cannot create legacy shortcut: user ID or base URL is missing")
            return false
        }

        val displayName = conversation.displayName.ifBlank { conversation.name }

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
     * Creates the intent that opens the conversation [roomToken] of [user] through its nextcloudtalk:// deep link, or
     * null if [user] has no id or no deep link can be built from its server url. The intent of all conversation
     * shortcuts, also of those that notifications publish.
     */
    fun createConversationIntent(context: Context, roomToken: String, user: User): Intent? {
        val uri = user.baseUrl
            ?.takeIf { user.id != null }
            ?.let { DeepLinkHandler.createConversationUri(roomToken, it, user.username) }
            ?.takeIf { it != Uri.EMPTY }
            ?: return null
        return Intent(context, DeepLinkActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
        }
    }

    /**
     * Generates a unique shortcut ID for a conversation.
     */
    fun getShortcutId(roomToken: String, userId: Long): String = "${CONVERSATION_SHORTCUT_PREFIX}${userId}_$roomToken"
}
