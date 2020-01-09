/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.services.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import coil.Coil
import coil.api.get
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.abs


class ShortcutService constructor(private var context: Context,
                                  private val conversationsRepository: ConversationsRepository,
                                  globalService: GlobalService
) {
    private var currentUser: UserNgEntity? = null
    private var lastFourActiveConversations: LiveData<List<Conversation>> = Transformations.switchMap(globalService.currentUserLiveData) { user ->
        currentUser = user
        var internalUserId: Long = -1
        currentUser?.let {
            internalUserId = it.id!!
        }
        conversationsRepository.getShortcutTargetConversations(internalUserId)
    }

    init {
        lastFourActiveConversations.observeForever {
            GlobalScope.launch {
                registerShortcuts(it)
            }
        }
    }

    private suspend fun registerShortcuts(conversations: List<Conversation>) {
        val openNewConversationIntent = Intent(context, MainActivity::class.java)
        openNewConversationIntent.action = BundleKeys.KEY_NEW_CONVERSATION

        val shortcuts: MutableList<ShortcutInfoCompat> = mutableListOf()
        val contactCategories: MutableSet<String> = HashSet()
        contactCategories.add(context.getString(R.string.nc_text_share_target))

        val images = Images()

        currentUser?.let { user ->
            shortcuts.add(ShortcutInfoCompat.Builder(context, "new")
                    //.setRank(4)
                    .setShortLabel(context.resources.getString(R.string.nc_new_conversation))
                    .setIcon(IconCompat.createWithBitmap(context.resources.getDrawable(R.drawable.new_conversation_shortcut).toBitmap()))
                    .setIntent(openNewConversationIntent)
                    .setAlwaysBadged()
                    .build())

            var iconImage: Drawable? = null
            for ((index, conversation) in conversations.withIndex()) {
                val intent = Intent(context, MainActivity::class.java)
                intent.action = BundleKeys.KEY_OPEN_CONVERSATION
                intent.putExtra(BundleKeys.KEY_INTERNAL_USER_ID, user.id)
                intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, conversation.token)

                val persons = mutableListOf<Person>()
                conversation.participants?.forEach {
                    val key = it.key
                    val participant = it.value
                    val personBuilder = Person.Builder()
                    personBuilder.setName(participant.name.toString())
                    personBuilder.setBot(false)
                    // we need a key for each of the users

                    val isGuest = participant.type == Participant.ParticipantType.GUEST || participant.type == Participant.ParticipantType.GUEST_AS_MODERATOR || participant.type == Participant.ParticipantType.USER_FOLLOWING_LINK

                    val avatarUrl = if (isGuest) ApiUtils.getUrlForAvatarWithNameForGuests(user.baseUrl, participant.name, R.dimen.avatar_size_big)
                    else ApiUtils.getUrlForAvatarWithName(user.baseUrl, it.key, R.dimen.avatar_size_big)

                    try {
                        iconImage = Coil.get(avatarUrl) {
                            addHeader("Authorization", user.getCredentials())
                            transformations(CircleCropTransformation())
                        }
                        personBuilder.setIcon(IconCompat.createWithBitmap((iconImage as BitmapDrawable).bitmap))
                    } catch (e: Exception) {
                        // No icon, that's fine for now
                    }
                    persons.add(personBuilder.build())
                }

                iconImage = images.getImageForConversation(context, conversation)

                if (iconImage == null) {
                    iconImage = Coil.get(ApiUtils.getUrlForAvatarWithName(user.baseUrl, conversation.name, R.dimen.avatar_size_big)) {
                        addHeader("Authorization", user.getCredentials())
                        transformations(CircleCropTransformation())
                    }
                }

                shortcuts.add(ShortcutInfoCompat.Builder(context, "current_conversation_" + (index + 1))
                        .setShortLabel(conversation.displayName as String)
                        .setLongLabel(conversation.displayName as String)
                        .setIcon(IconCompat.createWithBitmap((iconImage as BitmapDrawable).bitmap))
                        .setIntent(intent)
                        .setRank(abs(index - 4 + 1))
                        .setRank(0)
                        .setAlwaysBadged()
                        .setCategories(contactCategories)
                        .setPersons(persons.toTypedArray())
                        .build())
            }
        }

        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
    }
}
