/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.SharedItemGridBinding
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.polls.ui.PollMainDialogFragment
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.shareditems.model.SharedDeckCardItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedLocationItem
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.model.SharedPinnedItem
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import java.util.Collections.emptyList

class SharedItemsAdapter(
    private val showGrid: Boolean,
    private val user: User,
    private val roomToken: String,
    private val isUserConversationOwnerOrModerator: Boolean,
    private val isOne2One: Boolean,
    private val viewThemeUtils: ViewThemeUtils
) : RecyclerView.Adapter<SharedItemsViewHolder>() {

    var items: MutableList<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedItemsViewHolder =
        if (showGrid) {
            SharedItemsGridViewHolder(
                SharedItemGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                user,
                viewThemeUtils
            )
        } else {
            SharedItemsListViewHolder(
                SharedItemListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                user,
                viewThemeUtils
            )
        }

    override fun onBindViewHolder(holder: SharedItemsViewHolder, position: Int) {
        when (val item = items[position]) {
            is SharedPollItem -> holder.onBind(item, ::showPoll)
            is SharedFileItem -> holder.onBind(item)
            is SharedLocationItem -> holder.onBind(item)
            is SharedOtherItem -> holder.onBind(item)
            is SharedDeckCardItem -> holder.onBind(item)
            is SharedPinnedItem -> holder.onBind(item, ::openMessage, ::unpinMessage)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun showPoll(item: SharedItem, context: Context) {
        val pollVoteDialog = PollMainDialogFragment.newInstance(
            user,
            roomToken,
            isUserConversationOwnerOrModerator,
            item.id,
            item.name
        )
        pollVoteDialog.show(
            (context as SharedItemsActivity).supportFragmentManager,
            TAG
        )
    }

    private fun unpinMessage(item: SharedItem, context: Context) {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val url = ApiUtils.getUrlForChatMessagePinning(1, user.baseUrl, roomToken, item.id)

        val canPin = isOne2One || isUserConversationOwnerOrModerator
        if (canPin) {
            credentials?.let {
                (context as SharedItemsActivity).chatViewModel.unPinMessage(credentials, url)
                val index = items.indexOf(item)
                items.remove(item)
                this.notifyItemRemoved(index)
            }
        }
    }

    private fun openMessage(item: SharedItem, context: Context) {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val baseUrl = user.baseUrl
        (context as SharedItemsActivity).startContextChatWindowForMessage(
            credentials,
            baseUrl,
            roomToken,
            item.id,
            null
        )
    }

    companion object {
        private val TAG = SharedItemsAdapter::class.simpleName
    }
}
