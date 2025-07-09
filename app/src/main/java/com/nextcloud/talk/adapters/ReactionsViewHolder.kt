/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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

class ReactionsViewHolder(private val binding: ReactionItemBinding, private val user: User?) :
    RecyclerView.ViewHolder(binding.root) {

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
