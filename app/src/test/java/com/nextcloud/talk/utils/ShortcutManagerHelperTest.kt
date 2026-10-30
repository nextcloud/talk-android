/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.nextcloud.talk.activities.DeepLinkActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The launcher activity takes no extras from outside the app, so the conversation shortcuts that notifications
 * published with only extras are given the deep link of their conversation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ShortcutManagerHelperTest {

    private val context = RuntimeEnvironment.getApplication()

    private val alice = User(id = 1L, username = "alice", baseUrl = "https://cloud.example.com")

    private fun pushShortcutWithExtras(userId: Long, roomToken: String) {
        val intent = Intent().setClassName(context, AppTasks.LAUNCHER_ACTIVITY)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
            .putExtra(BundleKeys.KEY_INTERNAL_USER_ID, userId)
        ShortcutManagerCompat.pushDynamicShortcut(
            context,
            ShortcutInfoCompat.Builder(context, "conversation_${userId}_$roomToken")
                .setShortLabel("Chat")
                .setIntent(intent)
                .build()
        )
    }

    private fun intentOf(shortcutId: String): Intent =
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC)
            .single { it.id == shortcutId }
            .intent

    @Test
    fun `a conversation shortcut with only extras is given the deep link of its conversation`() {
        pushShortcutWithExtras(userId = 1L, roomToken = "abcd1234")

        ShortcutManagerHelper.updateShortcutsWithoutDeepLink(context, listOf(alice))

        val intent = intentOf("conversation_1_abcd1234")
        assertEquals(DeepLinkActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val deepLink = DeepLinkHandler.createConversationUri("abcd1234", alice.baseUrl!!, alice.username)
        assertEquals(deepLink, intent.data)
    }

    @Test
    fun `a conversation shortcut of an account that is gone is kept as it is`() {
        pushShortcutWithExtras(userId = 2L, roomToken = "abcd1234")

        ShortcutManagerHelper.updateShortcutsWithoutDeepLink(context, listOf(alice))

        assertEquals(AppTasks.LAUNCHER_ACTIVITY, intentOf("conversation_2_abcd1234").component?.className)
    }
}
