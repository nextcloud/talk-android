/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.text.TextUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.PollResultVotersOverviewItemBinding
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.utils.DisplayUtils

class PollResultVotersOverviewViewHolder(
    private val user: User,
    private val roomToken: String,
    override val binding: PollResultVotersOverviewItemBinding
) : PollResultViewHolder(binding) {

    @SuppressLint("SetTextI18n")
    override fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        val item = pollResultItem as PollResultVotersOverviewItem

        binding.root.setOnClickListener { clickListener.onClick() }

        val layoutParams = LinearLayout.LayoutParams(
            AVATAR_SIZE,
            AVATAR_SIZE
        )

        var avatarsToDisplay = MAX_AVATARS
        if (item.detailsList.size < avatarsToDisplay) {
            avatarsToDisplay = item.detailsList.size
        }
        val shotsDots = item.detailsList.size > avatarsToDisplay

        for (i in 0 until avatarsToDisplay) {
            val pollDetails = item.detailsList[i]
            val avatar = ImageView(binding.root.context)

            layoutParams.marginStart = i * AVATAR_OFFSET
            avatar.layoutParams = layoutParams

            avatar.translationZ = i.toFloat() * -1

            loadAvatar(pollDetails, avatar)

            binding.votersAvatarsOverviewWrapper.addView(avatar)

            if (i == avatarsToDisplay - 1 && shotsDots) {
                val dotsView = TextView(itemView.context)
                layoutParams.marginStart = i * AVATAR_OFFSET + DOTS_OFFSET
                dotsView.layoutParams = layoutParams
                dotsView.text = DOTS_TEXT
                binding.votersAvatarsOverviewWrapper.addView(dotsView)
            }
        }
    }

    private fun loadAvatar(pollDetail: PollDetails, avatar: ImageView) {
        when (pollDetail.actorType) {
            Participant.ActorType.GUESTS -> {
                var displayName = NextcloudTalkApplication.sharedApplication?.resources?.getString(R.string.nc_guest)
                if (!TextUtils.isEmpty(pollDetail.actorDisplayName)) {
                    displayName = pollDetail.actorDisplayName!!
                }
                avatar.loadGuestAvatar(user, displayName!!, false)
            }

            Participant.ActorType.FEDERATED -> {
                val darkTheme = if (DisplayUtils.isDarkModeOn(binding.root.context)) 1 else 0
                avatar.loadFederatedUserAvatar(
                    user,
                    user.baseUrl!!,
                    roomToken,
                    pollDetail.actorId!!,
                    darkTheme,
                    false,
                    false
                )
            }

            else -> {
                avatar.loadUserAvatar(user, pollDetail.actorId!!, false, false)
            }
        }
    }

    companion object {
        const val AVATAR_SIZE = 60
        const val MAX_AVATARS = 10
        const val AVATAR_OFFSET = AVATAR_SIZE - 20
        const val DOTS_OFFSET = 70
        const val DOTS_TEXT = "…"
    }
}
