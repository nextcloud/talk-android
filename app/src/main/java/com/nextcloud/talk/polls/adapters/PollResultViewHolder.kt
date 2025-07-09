/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

abstract class PollResultViewHolder(open val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
    abstract fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener)
}
