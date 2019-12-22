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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import coil.Coil
import coil.api.get
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ShortcutService constructor(private val context: Context,
                                  private val conversationsRepository: ConversationsRepository,
                                  conversationsService: GlobalService
) {
    private var currentUser: UserNgEntity? = null
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    @RequiresApi(Build.VERSION_CODES.P)
    private var lastThreeActiveConversations: LiveData<List<Conversation>> = Transformations.switchMap(conversationsService.currentUserLiveData) { user ->
        currentUser = user
        var internalUserId: Long = -1
        currentUser?.let {
            internalUserId = it.id!!
        }
        conversationsRepository.getLastThreeActiveConversationsForUser(internalUserId)
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lastThreeActiveConversations.observeForever {
                GlobalScope.launch {
                    registerShortcuts()
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private suspend fun registerShortcuts() {
        val openNewConversationIntent = Intent(context, MainActivity::class.java)
        openNewConversationIntent.action = BundleKeys.KEY_NEW_CONVERSATION

        val shortcuts: MutableList<ShortcutInfo> = mutableListOf()
        val images = Images()

        currentUser?.let { user ->
            shortcuts.add(ShortcutInfo.Builder(context, "new")
                    .setShortLabel(context.resources.getString(R.string.nc_new_conversation_short))
                    .setLongLabel(context.resources.getString(R.string.nc_new_conversation))
                    .setIcon(Icon.createWithBitmap(context.resources.getDrawable(R.drawable.new_conversation_shortcut).toBitmap()))
                    .setIntent(openNewConversationIntent)
                    .build())

            lastThreeActiveConversations.value?.let { conversations ->
                for ((index, conversation) in conversations.withIndex()) {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.action = BundleKeys.KEY_OPEN_CONVERSATION
                    intent.putExtra(BundleKeys.KEY_INTERNAL_USER_ID, user.id)
                    intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, conversation.token)

                    var iconImage = images.getImageForConversation(context, conversation)

                    if (iconImage == null) {
                        iconImage = Coil.get(ApiUtils.getUrlForAvatarWithName(user.baseUrl, conversation.name, R.dimen.avatar_size_big)) {
                            addHeader("Authorization", user.getCredentials())
                            transformations(CircleCropTransformation())
                        }
                    }

                    shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_" + (index + 1))
                            .setShortLabel(conversation.displayName as String)
                            .setLongLabel(conversation.displayName as String)
                            .setIcon(Icon.createWithBitmap((iconImage as BitmapDrawable).bitmap))
                            .setIntent(intent)
                            .build())
                }
            }
        }

        shortcutManager?.dynamicShortcuts = shortcuts
    }
}
