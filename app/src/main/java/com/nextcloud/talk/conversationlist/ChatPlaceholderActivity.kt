/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.conversationlist.ui.ChatPlaceholder

/**
 * Shown in the secondary pane next to [ConversationsListActivity] on wide screens while no chat is open.
 * Launched by the split placeholder rule in `res/xml/main_split_config.xml`.
 */
class ChatPlaceholderActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val colorScheme = viewThemeUtils.getColorScheme(this)
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                ChatPlaceholder()
            }
        }
    }
}
