/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.adapters

import android.text.TextUtils
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ReactionItemBinding
import com.nextcloud.talk.models.json.reactions.ReactionVoter
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

class ReactionsViewHolder(
    private val binding: ReactionItemBinding,
    private val baseUrl: String?
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(reactionItem: ReactionItem, clickListener: ReactionItemClickListener) {
        binding.root.setOnClickListener { clickListener.onClick(reactionItem) }
        binding.reaction.text = reactionItem.reaction
        binding.name.text = reactionItem.reactionVoter.actorDisplayName

        if (baseUrl != null && baseUrl.isNotEmpty()) {
            loadAvatar(reactionItem)
        }
    }

    private fun loadAvatar(reactionItem: ReactionItem) {
        if (reactionItem.reactionVoter.actorType == ReactionVoter.ReactionActorType.GUESTS) {
            var displayName = sharedApplication?.resources?.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(reactionItem.reactionVoter.actorDisplayName)) {
                displayName = reactionItem.reactionVoter.actorDisplayName!!
            }
            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(binding.avatar.controller)
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForGuestAvatar(
                            baseUrl,
                            displayName,
                            false
                        )
                    )
                )
                .build()
            binding.avatar.controller = draweeController
        } else if (reactionItem.reactionVoter.actorType == ReactionVoter.ReactionActorType.USERS) {
            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(binding.avatar.controller)
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForAvatar(
                            baseUrl,
                            reactionItem.reactionVoter.actorId,
                            false
                        )
                    )
                )
                .build()
            binding.avatar.controller = draweeController
        }
    }
}
