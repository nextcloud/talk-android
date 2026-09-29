/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemLoadMoreBinding
import com.nextcloud.talk.databinding.RvItemSearchMessageBinding
import com.nextcloud.talk.extensions.loadThumbnail
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class MessageSearchAdapter(
    private val user: User,
    private val viewThemeUtils: ViewThemeUtils,
    private val onResultClick: (SearchMessageEntry) -> Unit,
    private val onLoadMoreClick: () -> Unit
) : ListAdapter<MessageSearchAdapter.Item, RecyclerView.ViewHolder>(ItemCallback) {

    sealed interface Item {
        data class Result(val entry: SearchMessageEntry) : Item
        data object LoadMore : Item
    }

    inner class ResultViewHolder(private val binding: RvItemSearchMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val item = getItemOrNull(bindingAdapterPosition)
                if (item is Item.Result) {
                    onResultClick(item.entry)
                }
            }
        }

        fun bind(entry: SearchMessageEntry) {
            binding.conversationTitle.text = entry.title
            viewThemeUtils.platform.highlightText(binding.messageExcerpt, entry.messageExcerpt, entry.searchTerm)
            entry.thumbnailURL?.let { binding.thumbnail.loadThumbnail(it, user) }
        }
    }

    inner class LoadMoreViewHolder(binding: RvItemLoadMoreBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { onLoadMoreClick() }
        }
    }

    private fun getItemOrNull(position: Int): Item? =
        if (position == RecyclerView.NO_POSITION) null else getItem(position)

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is Item.Result -> VIEW_TYPE_RESULT
            Item.LoadMore -> VIEW_TYPE_LOAD_MORE
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LOAD_MORE -> LoadMoreViewHolder(RvItemLoadMoreBinding.inflate(inflater, parent, false))
            else -> ResultViewHolder(RvItemSearchMessageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is ResultViewHolder && item is Item.Result) {
            holder.bind(item.entry)
        }
    }

    private object ItemCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
            when {
                oldItem is Item.Result && newItem is Item.Result ->
                    oldItem.entry.conversationToken == newItem.entry.conversationToken &&
                        oldItem.entry.messageId == newItem.entry.messageId
                else -> oldItem == newItem
            }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_RESULT = 0
        private const val VIEW_TYPE_LOAD_MORE = 1
    }
}
