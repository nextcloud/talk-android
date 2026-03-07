/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.adapters.items.AdvancedUserItem
import com.nextcloud.talk.databinding.AccountItemBinding
import com.nextcloud.talk.extensions.loadUserAvatar

class AccountItemAdapter(private val onClick: (AdvancedUserItem) -> Unit) :
    ListAdapter<AdvancedUserItem, AccountItemAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: AccountItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AdvancedUserItem) {
            itemView.setOnClickListener { onClick(item) }
            binding.userName.text = item.model.displayName
            if (item.user != null && !TextUtils.isEmpty(item.user.baseUrl)) {
                val host = item.user.baseUrl!!.toUri().host
                binding.account.text = if (!TextUtils.isEmpty(host)) host else item.user.baseUrl
            }
            if (item.user?.baseUrl != null &&
                (item.user.baseUrl!!.startsWith("http://") || item.user.baseUrl!!.startsWith("https://"))
            ) {
                binding.userIcon.loadUserAvatar(item.user, item.model.calculatedActorId!!, true, false)
            }
            binding.actionRequired.visibility = if (item.actionRequiredCount > 0) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(AccountItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<AdvancedUserItem>() {
            override fun areItemsTheSame(oldItem: AdvancedUserItem, newItem: AdvancedUserItem): Boolean =
                oldItem.model == newItem.model

            override fun areContentsTheSame(oldItem: AdvancedUserItem, newItem: AdvancedUserItem): Boolean =
                oldItem == newItem
        }
    }
}
