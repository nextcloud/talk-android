/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedPollItem

class SharedItemsAdapter(
    private val showGrid: Boolean,
    private val user: User,
    private val roomToken: String,
    private val isUserConversationOwnerOrModerator: Boolean
) : RecyclerView.Adapter<SharedItemsViewHolder>() {

    var items: List<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedItemsViewHolder {

        return if (showGrid) {
            SharedItemsGridViewHolder(
                SharedItemGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                user
            )
        } else {
            SharedItemsListViewHolder(
                SharedItemListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                user
            )
        }
    }

    override fun onBindViewHolder(holder: SharedItemsViewHolder, position: Int) {
        when (val item = items[position]) {
            is SharedPollItem -> holder.onBind(item, ::showPoll)
            is SharedFileItem -> holder.onBind(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

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

    companion object {
        private val TAG = SharedItemsAdapter::class.simpleName
    }
}
