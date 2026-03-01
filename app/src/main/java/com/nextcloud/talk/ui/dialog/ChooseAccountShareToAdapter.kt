/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.AccountItemBinding
import com.nextcloud.talk.extensions.loadUserAvatar

class ChooseAccountShareToAdapter(private val onClick: (User) -> Unit) :
    ListAdapter<User, ChooseAccountShareToAdapter.AccountViewHolder>(AccountItemCallback) {

    inner class AccountViewHolder(val binding: AccountItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var currentUser: User? = null

        init {
            binding.root.setOnClickListener {
                currentUser?.let { onClick(it) }
            }
        }

        fun bindItem(user: User) {
            currentUser = user
            binding.userName.text = user.displayName
            val host = user.baseUrl?.toUri()?.host
            binding.account.text = if (!host.isNullOrEmpty()) host else user.baseUrl
            if (user.baseUrl != null &&
                (user.baseUrl!!.startsWith("http://") || user.baseUrl!!.startsWith("https://"))
            ) {
                val userId = user.userId ?: user.username
                binding.userIcon.loadUserAvatar(user, userId!!, true, false)
            }
            binding.actionRequired.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder =
        AccountViewHolder(AccountItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bindItem(getItem(position))
    }
}

object AccountItemCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
}
