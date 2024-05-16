/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
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
            val nameTextLayoutParams: RelativeLayout.LayoutParams = itemBinding.nameText.layoutParams as
                RelativeLayout.LayoutParams

            currentConversation = conversation
            itemBinding.nameText.text = conversation.displayName
            if (conversation.description == "") {
                itemBinding.descriptionText.visibility = View.GONE
                nameTextLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
            } else {
                itemBinding.descriptionText.text = conversation.description
            }

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
