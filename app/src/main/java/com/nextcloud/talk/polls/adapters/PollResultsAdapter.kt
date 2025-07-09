/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.PollResultHeaderItemBinding
import com.nextcloud.talk.databinding.PollResultVoterItemBinding
import com.nextcloud.talk.databinding.PollResultVotersOverviewItemBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class PollResultsAdapter(
    private val user: User,
    private val roomToken: String,
    private val clickListener: PollResultItemClickListener,
    private val viewThemeUtils: ViewThemeUtils
) : RecyclerView.Adapter<PollResultViewHolder>() {
    internal var list: MutableList<PollResultItem> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollResultViewHolder {
        var viewHolder: PollResultViewHolder? = null

        when (viewType) {
            PollResultHeaderItem.VIEW_TYPE -> {
                val itemBinding = PollResultHeaderItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                viewHolder = PollResultHeaderViewHolder(itemBinding, viewThemeUtils)
            }
            PollResultVoterItem.VIEW_TYPE -> {
                val itemBinding = PollResultVoterItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                viewHolder = PollResultVoterViewHolder(user, roomToken, itemBinding, viewThemeUtils)
            }
            PollResultVotersOverviewItem.VIEW_TYPE -> {
                val itemBinding = PollResultVotersOverviewItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                viewHolder = PollResultVotersOverviewViewHolder(user, roomToken, itemBinding)
            }
        }
        return viewHolder!!
    }

    override fun onBindViewHolder(holder: PollResultViewHolder, position: Int) {
        when (holder.itemViewType) {
            PollResultHeaderItem.VIEW_TYPE -> {
                val pollResultItem = list[position]
                holder.bind(pollResultItem as PollResultHeaderItem, clickListener)
            }
            PollResultVoterItem.VIEW_TYPE -> {
                val pollResultItem = list[position]
                holder.bind(pollResultItem as PollResultVoterItem, clickListener)
            }
            PollResultVotersOverviewItem.VIEW_TYPE -> {
                val pollResultItem = list[position]
                holder.bind(pollResultItem as PollResultVotersOverviewItem, clickListener)
            }
        }
    }

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int = list[position].getViewType()
}
