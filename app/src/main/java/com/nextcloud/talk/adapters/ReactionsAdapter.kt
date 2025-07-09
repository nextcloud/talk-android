/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ReactionItemBinding

class ReactionsAdapter(private val clickListener: ReactionItemClickListener, private val user: User?) :
    RecyclerView.Adapter<ReactionsViewHolder>() {
    internal var list: MutableList<ReactionItem> = ArrayList<ReactionItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReactionsViewHolder {
        val itemBinding = ReactionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReactionsViewHolder(itemBinding, user)
    }

    override fun onBindViewHolder(holder: ReactionsViewHolder, position: Int) {
        holder.bind(list[position], clickListener)
    }

    override fun getItemCount(): Int = list.size
}
