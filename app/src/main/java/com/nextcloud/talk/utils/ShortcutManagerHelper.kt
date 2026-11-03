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
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.conversationlist.DirectShareHelper
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
     * Creates a shortcut for a conversation with the intent of [createConversationIntent], which opens it for exactly
     * its account.
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
     * Gives the conversation and direct share shortcuts of earlier versions, also pinned and cached ones, the intent
     * of [createConversationIntent], with the account and conversation taken from the shortcut id. Their old intents
     * open only the list (extras from outside the app are removed) or name the account only by host and user name.
     *
     * @param context Application context
     * @param users The accounts whose shortcuts can be updated
     * @return false if the shortcuts couldn't be read or updated, e.g. while the user is locked, so it is tried again
     */
    @Suppress("TooGenericExceptionCaught")
    fun updateShortcutsOfEarlierVersions(context: Context, users: List<User>): Boolean =
        try {
            // one by one, so a shortcut that can't be updated doesn't keep the others from it
            shortcutsOfEarlierVersionsUpdated(context, users).forEach { shortcut ->
                try {
                    ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
                } catch (e: IllegalArgumentException) {
                    // Old shortcuts must not crash the conversation list.
                    Log.w(TAG, "Failed to update the shortcut ${shortcut.id} of an earlier version", e)
                }
            }
            true
        } catch (e: IllegalStateException) {
            // Thrown while the user is locked
            Log.w(TAG, "Failed to update the shortcuts of earlier versions", e)
            false
        } catch (e: RuntimeException) {
            // e.g. an old shortcut the system can't give as it is. Not tried again, as it would fail again.
            Log.e(TAG, "Failed to update the shortcuts of earlier versions", e)
            true
        }

    /** The shortcuts of [updateShortcutsOfEarlierVersions] with the intent of their conversation. */
    private fun shortcutsOfEarlierVersionsUpdated(context: Context, users: List<User>): List<ShortcutInfoCompat> {
        val usersById = users.associateBy { it.id }
        val matchFlags = ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED or
            ShortcutManagerCompat.FLAG_MATCH_CACHED
        val internalEntryPoint = InternalEntryPoint.nameOf(ConversationsListActivity::class.java)
        return ShortcutManagerCompat.getShortcuts(context, matchFlags)
            .filter { it.intent.component?.className != internalEntryPoint }
            .mapNotNull { shortcut ->
                val (userId, roomToken) = conversationOfShortcutId(shortcut.id) ?: return@mapNotNull null
                val intent = usersById[userId]?.let { createConversationIntent(context, roomToken, it) }
                // The system keeps the fields that aren't set, e.g. the icon, person and categories. Without
                // categories, androidx.sharetarget also keeps the share target it stores for the share sheet before
                // Android 10, with its icon, which it would replace by one without.
                intent?.let {
                    ShortcutInfoCompat.Builder(context, shortcut.id)
                        .setShortLabel(shortcut.shortLabel)
                        .apply {
                            shortcut.longLabel?.let(::setLongLabel)
                            shortcut.locusId?.let(::setLocusId)
                        }
                        .setIntent(it)
                        .build()
                }
            }
    }

    /**
     * The user id and room token of the conversation shortcut [shortcutId] of [getShortcutId], or of a direct share
     * shortcut of [DirectShareHelper], else null.
     */
    fun conversationOfShortcutId(shortcutId: String): Pair<Long, String>? {
        val prefix = listOf(CONVERSATION_SHORTCUT_PREFIX, DirectShareHelper.SHORTCUT_ID_PREFIX)
            .firstOrNull { shortcutId.startsWith(it) }
        val userIdAndToken = prefix?.let { shortcutId.removePrefix(it) }
        val userId = userIdAndToken?.substringBefore('_')?.toLongOrNull()
        val roomToken = userIdAndToken?.substringAfter('_', missingDelimiterValue = "")
            ?.takeIf(DeepLinkHandler::isValidToken)
        return if (userId != null && roomToken != null) userId to roomToken else null
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
        val launchIntent = createConversationDeepLinkIntent(context, conversation.token, user)
        if (launchIntent == null) {
            Log.w(TAG, "Cannot create legacy shortcut: base URL is missing")
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
     * Creates the intent that opens the conversation [roomToken] of exactly the account [user], or null if [user] has
     * no id. The intent of all conversation shortcuts, also of those that notifications publish and of the direct
     * share targets: the system starts a shortcut as the app that published it, so it can target the internal entry
     * point of the conversation list, which takes the account and conversation from its extras.
     */
    fun createConversationIntent(context: Context, roomToken: String, user: User): Intent? =
        user.id?.let { createConversationIntent(context, roomToken, it) }

    /** Like [createConversationIntent], for the account with the internal id [userId]. */
    fun createConversationIntent(context: Context, roomToken: String, userId: Long): Intent =
        ConversationsListActivity.createIntent(context).apply {
            action = Intent.ACTION_VIEW
            putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
            putExtra(BundleKeys.KEY_INTERNAL_USER_ID, userId)
            // With NEW_TASK alone, a task an earlier shortcut started is only brought to the front, without this
            // intent; the list on top of it gets it in onNewIntent instead.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    /**
     * Creates the intent that opens the conversation [roomToken] of [user] through its nextcloudtalk:// deep link, or
     * null if no deep link can be built from its server url. Only for legacy home screen shortcuts, which the
     * launcher starts as itself, so they can't target the internal entry point. The deep link names only the host and
     * user name of the account.
     */
    private fun createConversationDeepLinkIntent(context: Context, roomToken: String, user: User): Intent? {
        val uri = user.baseUrl
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
