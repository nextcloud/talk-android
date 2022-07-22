/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.polls.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.PollResultHeaderItemBinding
import com.nextcloud.talk.databinding.PollResultVoterItemBinding
import com.nextcloud.talk.databinding.PollResultVotersOverviewItemBinding

class PollResultsAdapter(
    private val user: User,
    private val clickListener: PollResultItemClickListener,
) : RecyclerView.Adapter<PollResultViewHolder>() {
    internal var list: MutableList<PollResultItem> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollResultViewHolder {
        var viewHolder: PollResultViewHolder? = null

        when (viewType) {
            PollResultHeaderItem.VIEW_TYPE -> {
                val itemBinding = PollResultHeaderItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent,
                    false
                )
                viewHolder = PollResultHeaderViewHolder(itemBinding)
            }
            PollResultVoterItem.VIEW_TYPE -> {
                val itemBinding = PollResultVoterItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent,
                    false
                )
                viewHolder = PollResultVoterViewHolder(user, itemBinding)
            }
            PollResultVotersOverviewItem.VIEW_TYPE -> {
                val itemBinding = PollResultVotersOverviewItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent,
                    false
                )
                viewHolder = PollResultVotersOverviewViewHolder(user, itemBinding)
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

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemViewType(position: Int): Int {
        return list[position].getViewType()
    }
}
