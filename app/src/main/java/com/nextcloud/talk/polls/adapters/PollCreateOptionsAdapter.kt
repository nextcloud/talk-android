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
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding

class PollCreateOptionsAdapter(
    private val clickListener: PollCreateOptionsItemListener
) : RecyclerView.Adapter<PollCreateOptionViewHolder>() {

    internal var list: ArrayList<PollCreateOptionItem> = ArrayList<PollCreateOptionItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollCreateOptionViewHolder {
        val itemBinding = PollCreateOptionsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return PollCreateOptionViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: PollCreateOptionViewHolder, position: Int) {
        val currentItem = list[position]
        var focus = false

        if (list.size - 1 == position && currentItem.pollOption.isBlank()) {
            focus = true
        }

        holder.bind(currentItem, clickListener, position, focus)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun updateOptionsList(optionsList: ArrayList<PollCreateOptionItem>) {
        list = optionsList
        notifyDataSetChanged()
    }
}
