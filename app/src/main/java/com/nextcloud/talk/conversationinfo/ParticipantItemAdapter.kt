/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemConversationInfoParticipantBinding
import com.nextcloud.talk.extensions.loadDefaultAvatar
import com.nextcloud.talk.extensions.loadDefaultGroupCallAvatar
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.extensions.loadFirstLetterAvatar
import com.nextcloud.talk.extensions.loadPhoneAvatar
import com.nextcloud.talk.extensions.loadTeamAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.InCallFlags
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DisplayUtils.convertDpToPixel

class ParticipantItemAdapter(
    private val context: Context,
    private val user: User,
    private val viewThemeUtils: ViewThemeUtils,
    private val conversation: ConversationModel,
    private val onItemClick: (ParticipantModel) -> Unit
) : ListAdapter<ParticipantModel, ParticipantItemAdapter.ViewHolder>(DiffCallback) {

    var filterQuery: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class ViewHolder(val binding: RvItemConversationInfoParticipantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ParticipantModel) {
            itemView.setOnClickListener { onItemClick(item) }
            val model = item.participant
            drawStatus(binding, model)
            setOnlineStateColor(binding, item.isOnline)
            binding.nameText.text = model.displayName

            if (model.type == Participant.ParticipantType.GUEST && model.displayName.isNullOrBlank()) {
                binding.nameText.text = sharedApplication!!.getString(R.string.nc_guest)
            }

            if (filterQuery != null) {
                viewThemeUtils.talk.themeAndHighlightText(
                    binding.nameText,
                    model.displayName,
                    filterQuery!!
                )
            }

            loadAvatars(binding, model)
            showCallIcons(binding, model)
            setParticipantInfo(binding, model)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RvItemConversationInfoParticipantBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    @SuppressLint("SetTextI18n")
    private fun setParticipantInfo(binding: RvItemConversationInfoParticipantBinding, model: Participant) {
        if (TextUtils.isEmpty(model.displayName) &&
            (
                model.type == Participant.ParticipantType.GUEST ||
                    model.type == Participant.ParticipantType.USER_FOLLOWING_LINK
                )
        ) {
            binding.nameText.text = sharedApplication!!.getString(R.string.nc_guest)
        }

        var userType = ""
        when (model.type) {
            Participant.ParticipantType.OWNER,
            Participant.ParticipantType.MODERATOR,
            Participant.ParticipantType.GUEST_MODERATOR -> {
                userType = sharedApplication!!.getString(R.string.nc_moderator)
            }

            Participant.ParticipantType.USER -> {
                userType = sharedApplication!!.getString(R.string.nc_user)
                if (model.calculatedActorType == Participant.ActorType.GROUPS) {
                    userType = sharedApplication!!.getString(R.string.nc_group)
                }
                if (model.calculatedActorType == Participant.ActorType.CIRCLES) {
                    userType = sharedApplication!!.getString(R.string.nc_team)
                }
            }

            Participant.ParticipantType.GUEST -> {
                userType = sharedApplication!!.getString(R.string.nc_guest)
                if (model.calculatedActorType == Participant.ActorType.EMAILS) {
                    userType = sharedApplication!!.getString(R.string.nc_guest)
                }

                if (model.invitedActorId?.isNotEmpty() == true &&
                    ConversationUtils.isParticipantOwnerOrModerator(conversation)
                ) {
                    binding.conversationInfoStatusMessage.text = model.invitedActorId
                    alignUsernameVertical(binding, 0f)
                }
            }

            Participant.ParticipantType.USER_FOLLOWING_LINK -> {
                userType = sharedApplication!!.getString(R.string.nc_following_link)
            }

            else -> {}
        }
        if (userType != sharedApplication!!.getString(R.string.nc_user)) {
            binding.secondaryText.text = "($userType)"
        }
    }

    private fun setOnlineStateColor(binding: RvItemConversationInfoParticipantBinding, isOnline: Boolean) {
        if (!isOnline) {
            binding.nameText.setTextColor(
                ResourcesCompat.getColor(
                    binding.nameText.context.resources,
                    R.color.medium_emphasis_text,
                    null
                )
            )
            binding.avatarView.alpha = NOT_ONLINE_ALPHA
        } else {
            binding.nameText.setTextColor(
                ResourcesCompat.getColor(
                    binding.nameText.context.resources,
                    R.color.high_emphasis_text,
                    null
                )
            )
            binding.avatarView.alpha = 1.0f
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun showCallIcons(binding: RvItemConversationInfoParticipantBinding, model: Participant) {
        val resources = sharedApplication!!.resources
        val inCallFlag = model.inCall
        if (inCallFlag and InCallFlags.WITH_PHONE.toLong() > 0) {
            binding.videoCallIcon.setImageResource(R.drawable.ic_call_grey_600_24dp)
            binding.videoCallIcon.visibility = View.VISIBLE
            binding.videoCallIcon.contentDescription =
                resources.getString(R.string.nc_call_state_with_phone, model.displayName)
        } else if (inCallFlag and InCallFlags.WITH_VIDEO.toLong() > 0) {
            binding.videoCallIcon.setImageResource(R.drawable.ic_videocam_grey_600_24dp)
            binding.videoCallIcon.visibility = View.VISIBLE
            binding.videoCallIcon.contentDescription =
                resources.getString(R.string.nc_call_state_with_video, model.displayName)
        } else if (inCallFlag > InCallFlags.DISCONNECTED) {
            binding.videoCallIcon.setImageResource(R.drawable.ic_mic_grey_600_24dp)
            binding.videoCallIcon.visibility = View.VISIBLE
            binding.videoCallIcon.contentDescription =
                resources.getString(R.string.nc_call_state_in_call, model.displayName)
        } else {
            binding.videoCallIcon.visibility = View.GONE
        }
    }

    private fun loadAvatars(binding: RvItemConversationInfoParticipantBinding, model: Participant) {
        when (model.calculatedActorType) {
            Participant.ActorType.GROUPS -> {
                binding.avatarView.loadDefaultGroupCallAvatar(viewThemeUtils)
            }

            Participant.ActorType.CIRCLES -> {
                binding.avatarView.loadTeamAvatar(viewThemeUtils)
            }

            Participant.ActorType.USERS -> {
                binding.avatarView.loadUserAvatar(user, model.calculatedActorId!!, true, false)
            }

            Participant.ActorType.GUESTS, Participant.ActorType.EMAILS -> {
                val actorName = model.displayName
                if (!actorName.isNullOrBlank()) {
                    binding.avatarView.loadFirstLetterAvatar(actorName)
                } else {
                    binding.avatarView.loadDefaultAvatar(viewThemeUtils)
                }
            }

            Participant.ActorType.FEDERATED -> {
                val darkTheme = if (DisplayUtils.isDarkModeOn(context)) 1 else 0
                binding.avatarView.loadFederatedUserAvatar(
                    user,
                    user.baseUrl!!,
                    conversation.token,
                    model.actorId!!,
                    darkTheme,
                    true,
                    false
                )
            }

            Participant.ActorType.PHONES -> {
                binding.avatarView.loadPhoneAvatar(viewThemeUtils)
            }

            else -> {
                Log.w(TAG, "Avatar not shown because of unknown ActorType " + model.calculatedActorType)
            }
        }
    }

    @Suppress("MagicNumber")
    private fun drawStatus(binding: RvItemConversationInfoParticipantBinding, model: Participant) {
        val size = convertDpToPixel(STATUS_SIZE_IN_DP, context)
        binding.userStatusImage.setImageDrawable(
            StatusDrawable(
                model.status,
                NO_ICON,
                size,
                context.resources.getColor(R.color.bg_default),
                context
            )
        )
        if (model.statusMessage != null) {
            binding.conversationInfoStatusMessage.text = model.statusMessage
            alignUsernameVertical(binding, 0f)
        } else {
            binding.conversationInfoStatusMessage.text = ""
            alignUsernameVertical(binding, 10f)
        }
        if (model.statusIcon != null && model.statusIcon!!.isNotEmpty()) {
            binding.participantStatusEmoji.setText(model.statusIcon)
        } else {
            binding.participantStatusEmoji.visibility = View.GONE
        }
        if (model.status != null && model.status == StatusType.DND.string) {
            if (model.statusMessage == null || model.statusMessage!!.isEmpty()) {
                binding.conversationInfoStatusMessage.setText(R.string.dnd)
            }
        } else if (model.status != null && model.status == StatusType.BUSY.string) {
            if (model.statusMessage == null || model.statusMessage!!.isEmpty()) {
                binding.conversationInfoStatusMessage.setText(R.string.busy)
            }
        } else if (model.status != null && model.status == StatusType.AWAY.string) {
            if (model.statusMessage == null || model.statusMessage!!.isEmpty()) {
                binding.conversationInfoStatusMessage.setText(R.string.away)
            }
        }
    }

    private fun alignUsernameVertical(binding: RvItemConversationInfoParticipantBinding, densityPixelsFromTop: Float) {
        val layoutParams = binding.nameText.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.topMargin = convertDpToPixel(densityPixelsFromTop, context).toInt()
        binding.nameText.layoutParams = layoutParams
    }

    companion object {
        private val TAG = ParticipantItemAdapter::class.simpleName
        private const val STATUS_SIZE_IN_DP = 9f
        private const val NO_ICON = ""
        private const val NOT_ONLINE_ALPHA = 0.38f

        val DiffCallback = object : DiffUtil.ItemCallback<ParticipantModel>() {
            override fun areItemsTheSame(oldItem: ParticipantModel, newItem: ParticipantModel): Boolean =
                oldItem.participant.calculatedActorType == newItem.participant.calculatedActorType &&
                    oldItem.participant.calculatedActorId == newItem.participant.calculatedActorId

            override fun areContentsTheSame(oldItem: ParticipantModel, newItem: ParticipantModel): Boolean =
                oldItem == newItem
        }
    }
}
