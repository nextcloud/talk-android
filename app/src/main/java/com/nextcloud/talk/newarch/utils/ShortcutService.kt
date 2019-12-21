/*
 * Nextcloud Talk application
 *
 * @author Thomas Ebert<thomas@thomasebert.net>
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.newarch.utils

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.bundle.BundleKeys

class ShortcutService constructor(private val context: Context,
                                  private val conversationsRepository: ConversationsRepository,
                                  conversationsService: ConversationService
) {
    private var lastThreeActiveConversations: LiveData<List<Conversation>> = MutableLiveData()
    private var currentUser: UserNgEntity? = null
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            conversationsService.currentUserLiveData.observeForever {
                currentUser = it
                it?.let {
                    lastThreeActiveConversations = conversationsRepository.getLastThreeActiveConversationsForUser(it.id!!)
                } ?: run {
                    shortcutManager?.dynamicShortcuts = listOf()
                }
            }

            lastThreeActiveConversations.observeForever {
                registerShortcuts()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    fun registerShortcuts() {
        val openNewConversationIntent = Intent(context, MainActivity::class.java)
        openNewConversationIntent.action = BundleKeys.KEY_NEW_CONVERSATION

        val shortcuts: MutableList<ShortcutInfo> = mutableListOf()
        val images = Images()

        currentUser?.let { user ->
            shortcuts.add(ShortcutInfo.Builder(context, "new")
                    .setShortLabel(context.resources.getString(R.string.nc_new_conversation_short))
                    .setLongLabel(context.resources.getString(R.string.nc_new_conversation))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                    .setIntent(openNewConversationIntent)
                    .build())

            lastThreeActiveConversations.value?.let { conversations ->
                for ((index, conversation) in conversations.withIndex()) {
                    // Only do this for the first 3 conversations
                    if (index <= 3) continue

                    val intent = Intent(context, MainActivity::class.java)
                    intent.action = BundleKeys.KEY_OPEN_CONVERSATION
                    intent.putExtra(BundleKeys.KEY_INTERNAL_USER_ID, user.id)
                    intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, conversation.token)

                    val icon = images.getImageForConversation(context, conversation)
                    shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_" + (index + 1))
                            .setShortLabel(conversation.displayName as String)
                            .setLongLabel(conversation.displayName as String)
                            // @TODO: Use avatar as icon
                            .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                            .setIntent(intent)
                            .build())
                }
            }
        }

        shortcutManager?.dynamicShortcuts = shortcuts
    }
}
