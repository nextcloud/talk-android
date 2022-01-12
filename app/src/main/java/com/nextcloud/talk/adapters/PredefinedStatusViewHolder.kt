/*
 *
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky
 * Copyright (C) 2020 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.PredefinedStatusBinding
import com.nextcloud.talk.models.json.status.PredefinedStatus
import com.nextcloud.talk.utils.DisplayUtils

private const val ONE_SECOND_IN_MILLIS = 1000

class PredefinedStatusViewHolder(private val binding: PredefinedStatusBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(status: PredefinedStatus, clickListener: PredefinedStatusClickListener, context: Context) {
        binding.root.setOnClickListener { clickListener.onClick(status) }
        binding.icon.text = status.icon
        binding.name.text = status.message

        if (status.clearAt == null) {
            binding.clearAt.text = context.getString(R.string.dontClear)
        } else {
            val clearAt = status.clearAt!!
            if (clearAt.type.equals("period")) {
                binding.clearAt.text = DisplayUtils.getRelativeTimestamp(
                    context,
                    System.currentTimeMillis() + clearAt.time.toInt() * ONE_SECOND_IN_MILLIS,
                    true
                )
            } else {
                // end-of
                if (clearAt.time.equals("day")) {
                    binding.clearAt.text = context.getString(R.string.today)
                }
            }
        }
    }
}
