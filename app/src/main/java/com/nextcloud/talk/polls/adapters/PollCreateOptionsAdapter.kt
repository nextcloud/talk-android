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
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class PollCreateOptionsAdapter(
    private val clickListener: PollCreateOptionsItemListener,
    private val viewThemeUtils: ViewThemeUtils
) : RecyclerView.Adapter<PollCreateOptionViewHolder>() {

    internal var list: ArrayList<PollCreateOptionItem> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollCreateOptionViewHolder {
        val itemBinding = PollCreateOptionsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return PollCreateOptionViewHolder(itemBinding, viewThemeUtils)
    }

    override fun onBindViewHolder(holder: PollCreateOptionViewHolder, position: Int) {
        val currentItem = list[position]
        var focus = false

        if (list.size - 1 == position && currentItem.pollOption.isBlank()) {
            focus = true
        }

        holder.bind(currentItem, clickListener, position, focus)
    }

    override fun getItemCount(): Int = list.size

    fun updateOptionsList(optionsList: ArrayList<PollCreateOptionItem>) {
        list = optionsList
        notifyDataSetChanged()
    }
}
