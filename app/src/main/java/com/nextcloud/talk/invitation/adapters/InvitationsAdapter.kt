/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
    override fun areItemsTheSame(oldItem: Invitation, newItem: Invitation): Boolean = oldItem == newItem

    override fun areContentsTheSame(oldItem: Invitation, newItem: Invitation): Boolean = oldItem.id == newItem.id
}
