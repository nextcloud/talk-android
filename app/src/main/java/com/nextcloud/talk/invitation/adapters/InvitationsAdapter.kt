/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.invitation.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemInvitationBinding
import com.nextcloud.talk.invitation.InvitationsActivity
import com.nextcloud.talk.invitation.data.Invitation
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class InvitationsAdapter(
    val user: User,
    private val handleInvitation: (Invitation, InvitationsActivity.InvitationAction) -> Unit
) : ListAdapter<Invitation, InvitationsAdapter.InvitationsViewHolder>(InvitationsCallback) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    inner class InvitationsViewHolder(private val itemBinding: RvItemInvitationBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        private var currentInvitation: Invitation? = null

        fun bindItem(invitation: Invitation) {
            currentInvitation = invitation

            itemBinding.title.text = invitation.roomName
            itemBinding.subject.text = String.format(
                itemBinding.root.context.resources.getString(R.string.nc_federation_invited_to_room),
                invitation.inviterDisplayName,
                invitation.remoteServerUrl
            )

            itemBinding.acceptInvitation.setOnClickListener {
                currentInvitation?.let {
                    handleInvitation(it, InvitationsActivity.InvitationAction.ACCEPT)
                }
            }

            itemBinding.rejectInvitation.setOnClickListener {
                currentInvitation?.let {
                    handleInvitation(it, InvitationsActivity.InvitationAction.REJECT)
                }
            }

            viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(itemBinding.rejectInvitation)
            viewThemeUtils.material.colorMaterialButtonPrimaryTonal(itemBinding.acceptInvitation)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvitationsViewHolder {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        return InvitationsViewHolder(
            RvItemInvitationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: InvitationsViewHolder, position: Int) {
        val invitation = getItem(position)
        holder.bindItem(invitation)
    }
}

object InvitationsCallback : DiffUtil.ItemCallback<Invitation>() {
    override fun areItemsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
        return oldItem.id == newItem.id
    }
}
