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

import android.annotation.SuppressLint
import android.text.TextUtils
import android.widget.ImageView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.PollResultVoterItemBinding
import com.nextcloud.talk.extensions.loadAvatar
import com.nextcloud.talk.extensions.loadGuestAvatar
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
            avatar.loadAvatar(user, pollDetail.actorId!!, false)
        }
    }
}
