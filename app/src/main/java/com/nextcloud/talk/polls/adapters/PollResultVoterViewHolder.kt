/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.text.TextUtils
import android.widget.ImageView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.PollResultVoterItemBinding
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class PollResultVoterViewHolder(
    private val user: User,
    override val binding: PollResultVoterItemBinding,
    private val viewThemeUtils: ViewThemeUtils
) : PollResultViewHolder(binding) {

    @SuppressLint("SetTextI18n")
    override fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        val item = pollResultItem as PollResultVoterItem

        binding.root.setOnClickListener { clickListener.onClick() }

        binding.pollVoterName.text = item.details.actorDisplayName
        loadAvatar(item.details, binding.pollVoterAvatar)
        viewThemeUtils.dialog.colorDialogSupportingText(binding.pollVoterName)
    }

    private fun loadAvatar(pollDetail: PollDetails, avatar: ImageView) {
        if (pollDetail.actorType == "guests") {
            var displayName = NextcloudTalkApplication.sharedApplication?.resources?.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(pollDetail.actorDisplayName)) {
                displayName = pollDetail.actorDisplayName!!
            }
            avatar.loadGuestAvatar(user, displayName!!, false)
        } else if (pollDetail.actorType == "users") {
            avatar.loadUserAvatar(user, pollDetail.actorId!!, false, false)
        }
    }
}
