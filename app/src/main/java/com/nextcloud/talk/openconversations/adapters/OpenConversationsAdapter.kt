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
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemOpenConversationBinding
import com.nextcloud.talk.extensions.loadConversationAvatar
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class OpenConversationsAdapter(
    val user: User,
    val viewThemeUtils: ViewThemeUtils,
    private val onClick: (Conversation) -> Unit
) :
    ListAdapter<Conversation, OpenConversationsAdapter.OpenConversationsViewHolder>(ConversationsCallback) {
    private var originalList: List<Conversation> = emptyList()
    private var isFiltering = false

    inner class OpenConversationsViewHolder(val itemBinding: RvItemOpenConversationBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        var currentConversation: Conversation? = null

        init {
            itemBinding.root.setOnClickListener {
                currentConversation?.let {
                    onClick(it)
                }
            }
        }

        fun bindItem(conversation: Conversation) {
            val nameTextLayoutParams: RelativeLayout.LayoutParams = itemBinding.nameText.layoutParams as
                RelativeLayout.LayoutParams
            currentConversation = conversation
            val currentConversationModel = ConversationModel.mapToConversationModel(conversation, user)
            itemBinding.nameText.text = conversation.displayName
            if (conversation.description == "") {
                itemBinding.descriptionText.visibility = View.GONE
                nameTextLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
            } else {
                itemBinding.descriptionText.text = conversation.description
            }

            // load avatar from server when https://github.com/nextcloud/spreed/issues/9600 is solved
            itemBinding.avatarView.loadConversationAvatar(
                user,
                currentConversationModel,
                false,
                viewThemeUtils
            )
            // itemBinding.avatarView.loadUserAvatar(R.drawable.ic_circular_group)
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

    fun filter(text: String) {
        if (text == "") {
            submitList(originalList)
            isFiltering = false
            return
        }

        isFiltering = true
        val newList = mutableListOf<Conversation>()
        for (conversation in originalList) {
            if (conversation.displayName.contains(text, true) || conversation.description!!.contains(text, true)) {
                newList.add(conversation)
            }
        }

        if (newList.isNotEmpty()) {
            submitList(newList)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<Conversation>, currentList: MutableList<Conversation>) {
        if (!isFiltering) {
            originalList = currentList
        }
        super.onCurrentListChanged(previousList, currentList)
    }
}

object ConversationsCallback : DiffUtil.ItemCallback<Conversation>() {
    override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
        return oldItem.token == newItem.token
    }
}
