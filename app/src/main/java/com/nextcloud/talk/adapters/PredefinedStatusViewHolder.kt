/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2020 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2020 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.PredefinedStatusBinding
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus
import com.nextcloud.talk.utils.DisplayUtils

private const val ONE_SECOND_IN_MILLIS = 1000

@Suppress("DEPRECATION")
class PredefinedStatusViewHolder(private val binding: PredefinedStatusBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        status: PredefinedStatus,
        clickListener: PredefinedStatusClickListener,
        context: Context,
        isBackupStatusAvailable: Boolean
    ) {
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
        if (isBackupStatusAvailable) {
            binding.resetStatusButton.visibility = if (position == 0) View.VISIBLE else View.GONE
            if (position == 0) {
                binding.clearAt.text = context.getString(R.string.previously_set)
            }
            binding.resetStatusButton.setOnClickListener {
                clickListener.revertStatus()
            }
        }
    }
}
