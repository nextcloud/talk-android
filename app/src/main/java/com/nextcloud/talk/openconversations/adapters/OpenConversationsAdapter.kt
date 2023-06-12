/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.openconversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemOpenConversationBinding
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.openconversations.data.OpenConversation

class OpenConversationsAdapter(val user: User, private val onClick: (OpenConversation) -> Unit) :
    ListAdapter<OpenConversation, OpenConversationsAdapter.OpenConversationsViewHolder>(ConversationsCallback) {

    inner class OpenConversationsViewHolder(val itemBinding: RvItemOpenConversationBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        var currentConversation: OpenConversation? = null

        init {
            itemBinding.root.setOnClickListener {
                currentConversation?.let {
                    onClick(it)
                }
            }
        }

        fun bindItem(conversation: OpenConversation) {
            currentConversation = conversation
            itemBinding.nameText.text = conversation.displayName

            // load avatar from server when https://github.com/nextcloud/spreed/issues/9600 is solved
            // itemBinding.avatarView.loadUserAvatar(user, conversation.displayName, true, false)
            itemBinding.avatarView.loadUserAvatar(R.drawable.ic_circular_group)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OpenConversationsViewHolder {
        return OpenConversationsViewHolder(
            RvItemOpenConversationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: OpenConversationsViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bindItem(conversation)
    }
}

object ConversationsCallback : DiffUtil.ItemCallback<OpenConversation>() {
    override fun areItemsTheSame(oldItem: OpenConversation, newItem: OpenConversation): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: OpenConversation, newItem: OpenConversation): Boolean {
        return oldItem.roomId == newItem.roomId
    }
}
