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
import android.widget.LinearLayout
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.PollResultVotersOverviewItemBinding
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

class PollResultVotersOverviewViewHolder(
    private val user: User,
    override val binding: PollResultVotersOverviewItemBinding
) : PollResultViewHolder(binding) {

    @SuppressLint("SetTextI18n")
    override fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        val item = pollResultItem as PollResultVotersOverviewItem

        val lp = LinearLayout.LayoutParams(
            AVATAR_WIDTH,
            AVATAR_HEIGHT
        )

        item.detailsList.forEach {
            val avatar = SimpleDraweeView(binding.root.context)
            avatar.layoutParams = lp

            val roundingParams = RoundingParams.fromCornersRadius(AVATAR_RADIUS)
            roundingParams.roundAsCircle = true

            avatar.hierarchy.roundingParams = roundingParams
            avatar.controller = getAvatarDraweeController(it)

            binding.votersAvatarsOverviewWrapper.addView(avatar)
        }
    }

    private fun getAvatarDraweeController(pollDetail: PollDetails): DraweeController? {
        var draweeController: DraweeController? = null
        if (pollDetail.actorType == "guests") {
            var displayName = NextcloudTalkApplication.sharedApplication?.resources?.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(pollDetail.actorDisplayName)) {
                displayName = pollDetail.actorDisplayName!!
            }
            draweeController = Fresco.newDraweeControllerBuilder()
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForGuestAvatar(
                            user.baseUrl,
                            displayName,
                            false
                        ),
                        user
                    )
                )
                .build()
        } else if (pollDetail.actorType == "users") {
            draweeController = Fresco.newDraweeControllerBuilder()
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForAvatar(
                            user.baseUrl,
                            pollDetail.actorId,
                            false
                        ),
                        user
                    )
                )
                .build()
        }
        return draweeController
    }

    companion object {
        const val AVATAR_WIDTH = 90
        const val AVATAR_HEIGHT = 70
        const val AVATAR_RADIUS = 5f
    }
}
