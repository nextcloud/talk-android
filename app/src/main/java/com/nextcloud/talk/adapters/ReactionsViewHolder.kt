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
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ReactionItemBinding
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.reactions.ReactionVoter

class ReactionsViewHolder(
    private val binding: ReactionItemBinding,
    private val user: User?
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(reactionItem: ReactionItem, clickListener: ReactionItemClickListener) {
        binding.root.setOnClickListener { clickListener.onClick(reactionItem) }
        binding.reaction.text = reactionItem.reaction
        binding.name.text = reactionItem.reactionVoter.actorDisplayName

        if (user != null && user.baseUrl?.isNotEmpty() == true) {
            loadAvatar(reactionItem)
        }
    }

    private fun loadAvatar(reactionItem: ReactionItem) {
        if (reactionItem.reactionVoter.actorType == ReactionVoter.ReactionActorType.GUESTS) {
            var displayName = sharedApplication?.resources?.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(reactionItem.reactionVoter.actorDisplayName)) {
                displayName = reactionItem.reactionVoter.actorDisplayName!!
            }
            binding.avatar.loadGuestAvatar(user!!.baseUrl!!, displayName!!, false)
        } else if (reactionItem.reactionVoter.actorType == ReactionVoter.ReactionActorType.USERS) {
            binding.avatar.loadUserAvatar(
                user!!,
                reactionItem.reactionVoter.actorId!!,
                false,
                false
            )
        }
    }
}
