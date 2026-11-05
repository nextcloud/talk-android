/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.sharetarget.ShortcutInfoCompatSaverImpl
import com.nextcloud.talk.activities.DeepLinkActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The conversation shortcuts open the conversation of exactly their account through the internal entry point of the
 * conversation list, and those and the direct share shortcuts of earlier versions are given that intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ShortcutManagerHelperTest {

    private val context = RuntimeEnvironment.getApplication()

    // two accounts with the same user name on the same host, which a deep link can't tell apart
    private val alice = User(id = 1L, username = "alice", baseUrl = "https://example.com/nc1")
    private val aliceOnOtherPort = User(id = 2L, username = "alice", baseUrl = "https://example.com:8443")

    private val internalEntryPoint = InternalEntryPoint.nameOf(ConversationsListActivity::class.java)

    private val shareTargetCategory = "com.nextcloud.talk.sharing.SHARE_TARGET_CATEGORY"

    private fun pushShortcut(id: String, intent: Intent) {
        ShortcutManagerCompat.pushDynamicShortcut(
            context,
            ShortcutInfoCompat.Builder(context, id).setShortLabel("Chat").setIntent(intent).build()
        )
    }

    // the notification shortcuts of earlier versions
    private fun pushShortcutWithExtras(userId: Long, roomToken: String) {
        val intent = Intent().setClassName(context, AppTasks.LAUNCHER_ACTIVITY)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
            .putExtra(BundleKeys.KEY_INTERNAL_USER_ID, userId)
        pushShortcut("conversation_${userId}_$roomToken", intent)
    }

    // the conversation shortcuts of earlier versions
    private fun pushShortcutWithDeepLink(user: User, roomToken: String) {
        val intent = Intent(context, DeepLinkActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(DeepLinkHandler.createConversationUri(roomToken, user.baseUrl!!, user.username))
        pushShortcut("conversation_${user.id}_$roomToken", intent)
    }

    private fun intentOf(shortcutId: String): Intent =
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC)
            .single { it.id == shortcutId }
            .intent

    private fun assertOpensConversation(intent: Intent, userId: Long, roomToken: String) {
        assertEquals(internalEntryPoint, intent.component?.className)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(userId, intent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L))
        assertEquals(roomToken, intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN))
        // delivered to the list of a task an earlier shortcut started, instead of only bringing that task to the front
        val deliveredToRunningList =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(deliveredToRunningList, intent.flags and deliveredToRunningList)
    }

    @Test
    fun `a conversation intent names exactly its account`() {
        val intent = ShortcutManagerHelper.createConversationIntent(context, "abcd1234", aliceOnOtherPort)!!

        assertOpensConversation(intent, userId = 2L, roomToken = "abcd1234")
    }

    @Test
    fun `a conversation shortcut with only extras is given the intent of its conversation`() {
        pushShortcutWithExtras(userId = 1L, roomToken = "abcd1234")

        ShortcutManagerHelper.updateShortcutsOfEarlierVersions(context, listOf(alice, aliceOnOtherPort))

        assertOpensConversation(intentOf("conversation_1_abcd1234"), userId = 1L, roomToken = "abcd1234")
    }

    @Test
    fun `a conversation shortcut with a deep link is given the intent of exactly its account`() {
        pushShortcutWithDeepLink(aliceOnOtherPort, roomToken = "abcd1234")

        ShortcutManagerHelper.updateShortcutsOfEarlierVersions(context, listOf(alice, aliceOnOtherPort))

        assertOpensConversation(intentOf("conversation_2_abcd1234"), userId = 2L, roomToken = "abcd1234")
    }

    // a direct share shortcut of an earlier version, which targets the conversation list
    private fun directShareShortcut(): ShortcutInfoCompat {
        val intent = Intent(Intent.ACTION_VIEW, null, context, ConversationsListActivity::class.java)
            .putExtra(BundleKeys.KEY_ROOM_TOKEN, "abcd1234")
            .putExtra(BundleKeys.KEY_INTERNAL_USER_ID, 2L)
        return ShortcutInfoCompat.Builder(context, "direct_share_2_abcd1234")
            .setShortLabel("Chat")
            .setCategories(setOf(shareTargetCategory))
            .setIntent(intent)
            .build()
    }

    @Test
    fun `a direct share shortcut that targets the conversation list is given the intent of its conversation`() {
        ShortcutManagerCompat.pushDynamicShortcut(context, directShareShortcut())

        ShortcutManagerHelper.updateShortcutsOfEarlierVersions(context, listOf(alice, aliceOnOtherPort))

        assertOpensConversation(intentOf("direct_share_2_abcd1234"), userId = 2L, roomToken = "abcd1234")
    }

    // the share target androidx.sharetarget stores for the share sheet before Android 10, which holds its icon
    @SuppressLint("RestrictedApi")
    @Test
    fun `the share target stored for the share sheet of a direct share shortcut is kept as it is`() {
        val shortcut = directShareShortcut()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        val saver = ShortcutInfoCompatSaverImpl.getInstance(context)
        // Not waited for: it is stored on disk in the files of the test that first used the saver, which is shared by
        // the tests and ShortcutManagerCompat, while getShortcuts reads what it holds, after adding it
        saver.addShortcuts(listOf(shortcut))

        ShortcutManagerHelper.updateShortcutsOfEarlierVersions(context, listOf(alice, aliceOnOtherPort))

        val shareTarget = saver.getShortcuts().single { it.id == shortcut.id }
        assertEquals(ConversationsListActivity::class.java.name, shareTarget.intent.component?.className)
    }

    @Test
    fun `a conversation shortcut of an account that is gone is kept as it is`() {
        pushShortcutWithExtras(userId = 3L, roomToken = "abcd1234")

        ShortcutManagerHelper.updateShortcutsOfEarlierVersions(context, listOf(alice, aliceOnOtherPort))

        assertEquals(AppTasks.LAUNCHER_ACTIVITY, intentOf("conversation_3_abcd1234").component?.className)
    }

    @Test
    fun `other shortcuts are kept as they are`() {
        val other = Intent().setClassName(context, AppTasks.LAUNCHER_ACTIVITY).setAction(Intent.ACTION_VIEW)
        pushShortcut("note_to_self", other)

        ShortcutManagerHelper.updateShortcutsOfEarlierVersions(context, listOf(alice))

        assertEquals(AppTasks.LAUNCHER_ACTIVITY, intentOf("note_to_self").component?.className)
    }

    @Test
    fun `the account and conversation are read from the id of a conversation shortcut`() {
        assertEquals(2L to "abcd1234", ShortcutManagerHelper.conversationOfShortcutId("conversation_2_abcd1234"))
        assertEquals(2L to "abcd1234", ShortcutManagerHelper.conversationOfShortcutId("direct_share_2_abcd1234"))
        assertNull(ShortcutManagerHelper.conversationOfShortcutId("note_to_self_2_abcd1234"))
        assertNull(ShortcutManagerHelper.conversationOfShortcutId("conversation_x_abcd1234"))
        assertNull(ShortcutManagerHelper.conversationOfShortcutId("conversation_2"))
        assertNull(ShortcutManagerHelper.conversationOfShortcutId("conversation_2_../abcd"))
    }
}
