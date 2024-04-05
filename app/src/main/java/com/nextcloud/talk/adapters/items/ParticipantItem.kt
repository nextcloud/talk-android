/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ParticipantItem.ParticipantItemViewHolder
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemConversationInfoParticipantBinding
import com.nextcloud.talk.extensions.loadDefaultGroupCallAvatar
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadMailAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.InCallFlags
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DisplayUtils.convertDpToPixel
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import java.util.regex.Pattern

class ParticipantItem(
    private val context: Context,
    val model: Participant,
    private val user: User,
    private val viewThemeUtils: ViewThemeUtils,
    private val roomToken: String
) : AbstractFlexibleItem<ParticipantItemViewHolder>(), IFilterable<String?> {
    var isOnline = true
    override fun equals(o: Any?): Boolean {
        return if (o is ParticipantItem) {
            model.calculatedActorType == o.model.calculatedActorType &&
                model.calculatedActorId == o.model.calculatedActorId
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return model.hashCode()
    }

    override fun getLayoutRes(): Int {
        return R.layout.rv_item_conversation_info_participant
    }

    override fun createViewHolder(
        view: View?,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?
    ): ParticipantItemViewHolder {
        return ParticipantItemViewHolder(view, adapter)
    }

    @SuppressLint("SetTextI18n")
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?,
        holder: ParticipantItemViewHolder?,
        position: Int,
        payloads: List<*>?
    ) {
        drawStatus(holder!!)
        setOnlineStateColor(holder)
        holder.binding.nameText.text = model.displayName

        if (adapter!!.hasFilter()) {
            viewThemeUtils.talk.themeAndHighlightText(
                holder.binding.nameText,
                model.displayName,
                adapter.getFilter(
                    String::class.java
                ).toString()
            )
        }
        loadAvatars(holder)
        showCallIcons(holder)
        setParticipantInfo(holder)
    }

    @SuppressLint("SetTextI18n")
    private fun setParticipantInfo(holder: ParticipantItemViewHolder) {
        if (TextUtils.isEmpty(model.displayName) && (
                model.type == Participant.ParticipantType.GUEST ||
                    model.type == Participant.ParticipantType.USER_FOLLOWING_LINK
                )
        ) {
            holder.binding.nameText.text = sharedApplication!!.getString(R.string.nc_guest)
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
                    userType = sharedApplication!!.getString(R.string.nc_email)
                }
            }

            Participant.ParticipantType.USER_FOLLOWING_LINK -> {
                userType = sharedApplication!!.getString(R.string.nc_following_link)
            }

            else -> {}
        }
        if (userType != sharedApplication!!.getString(R.string.nc_user)) {
            holder.binding.secondaryText.text = "($userType)"
        }
    }

    private fun setOnlineStateColor(holder: ParticipantItemViewHolder) {
        if (!isOnline) {
            holder.binding.nameText.setTextColor(
                ResourcesCompat.getColor(
                    holder.binding.nameText.context.resources,
                    R.color.medium_emphasis_text,
                    null
                )
            )
            holder.binding.avatarView.setAlpha(NOT_ONLINE_ALPHA)
        } else {
            holder.binding.nameText.setTextColor(
                ResourcesCompat.getColor(
                    holder.binding.nameText.context.resources,
                    R.color.high_emphasis_text,
                    null
                )
            )
            holder.binding.avatarView.setAlpha(1.0f)
        }
    }

    private fun showCallIcons(holder: ParticipantItemViewHolder) {
        val resources = sharedApplication!!.resources
        val inCallFlag = model.inCall
        if (inCallFlag and InCallFlags.WITH_PHONE.toLong() > 0) {
            holder.binding.videoCallIcon.setImageResource(R.drawable.ic_call_grey_600_24dp)
            holder.binding.videoCallIcon.setVisibility(View.VISIBLE)
            holder.binding.videoCallIcon.setContentDescription(
                resources.getString(R.string.nc_call_state_with_phone, model.displayName)
            )
        } else if (inCallFlag and InCallFlags.WITH_VIDEO.toLong() > 0) {
            holder.binding.videoCallIcon.setImageResource(R.drawable.ic_videocam_grey_600_24dp)
            holder.binding.videoCallIcon.setVisibility(View.VISIBLE)
            holder.binding.videoCallIcon.setContentDescription(
                resources.getString(R.string.nc_call_state_with_video, model.displayName)
            )
        } else if (inCallFlag > InCallFlags.DISCONNECTED) {
            holder.binding.videoCallIcon.setImageResource(R.drawable.ic_mic_grey_600_24dp)
            holder.binding.videoCallIcon.setVisibility(View.VISIBLE)
            holder.binding.videoCallIcon.setContentDescription(
                resources.getString(R.string.nc_call_state_in_call, model.displayName)
            )
        } else {
            holder.binding.videoCallIcon.setVisibility(View.GONE)
        }
    }

    private fun loadAvatars(holder: ParticipantItemViewHolder) {
        when (model.calculatedActorType) {
            Participant.ActorType.GROUPS, Participant.ActorType.CIRCLES -> {
                holder.binding.avatarView.loadDefaultGroupCallAvatar(viewThemeUtils)
            }

            Participant.ActorType.EMAILS -> {
                holder.binding.avatarView.loadMailAvatar(viewThemeUtils)
            }

            Participant.ActorType.USERS -> {
                holder.binding.avatarView.loadUserAvatar(user, model.calculatedActorId!!, true, false)
            }

            Participant.ActorType.GUESTS -> {
                var displayName: String? = sharedApplication!!.resources.getString(R.string.nc_guest)
                if (!TextUtils.isEmpty(model.displayName)) {
                    displayName = model.displayName
                }
                holder.binding.avatarView.loadGuestAvatar(user, displayName!!, false)
            }

            Participant.ActorType.FEDERATED -> {
                val darkTheme = if (DisplayUtils.isDarkModeOn(context)) 1 else 0
                holder.binding.avatarView.loadFederatedUserAvatar(
                    user,
                    user.baseUrl!!,
                    roomToken,
                    model.actorId!!,
                    darkTheme,
                    true,
                    false
                )
            }

            else -> {
                Log.w(TAG, "Avatar not shown because of unknown ActorType " + model.calculatedActorType)
            }
        }
    }

    @Suppress("MagicNumber")
    private fun drawStatus(holder: ParticipantItemViewHolder) {
        val size = convertDpToPixel(STATUS_SIZE_IN_DP, context)
        holder.binding.userStatusImage.setImageDrawable(
            StatusDrawable(
                model.status,
                NO_ICON,
                size,
                context.resources.getColor(R.color.bg_default),
                context
            )
        )
        if (model.statusMessage != null) {
            holder.binding.conversationInfoStatusMessage.text = model.statusMessage
            alignUsernameVertical(holder, 0f)
        } else {
            holder.binding.conversationInfoStatusMessage.text = ""
            alignUsernameVertical(holder, 10f)
        }
        if (model.statusIcon != null && model.statusIcon!!.isNotEmpty()) {
            holder.binding.participantStatusEmoji.setText(model.statusIcon)
        } else {
            holder.binding.participantStatusEmoji.visibility = View.GONE
        }
        if (model.status != null && model.status == StatusType.DND.string) {
            if (model.statusMessage == null || model.statusMessage!!.isEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.dnd)
            }
        } else if (model.status != null && model.status == StatusType.AWAY.string) {
            if (model.statusMessage == null || model.statusMessage!!.isEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.away)
            }
        }
    }

    private fun alignUsernameVertical(holder: ParticipantItemViewHolder, densityPixelsFromTop: Float) {
        val layoutParams = holder.binding.nameText.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.topMargin = convertDpToPixel(densityPixelsFromTop, context).toInt()
        holder.binding.nameText.setLayoutParams(layoutParams)
    }

    override fun filter(constraint: String?): Boolean {
        return model.displayName != null && (
            Pattern.compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(model.displayName!!.trim { it <= ' ' }).find() ||
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                    .matcher(model.calculatedActorId!!.trim { it <= ' ' }).find()
            )
    }

    class ParticipantItemViewHolder internal constructor(view: View?, adapter: FlexibleAdapter<*>?) :
        FlexibleViewHolder(view, adapter) {
        var binding: RvItemConversationInfoParticipantBinding

        init {
            binding = RvItemConversationInfoParticipantBinding.bind(view!!)
        }
    }

    companion object {
        private val TAG = ParticipantItem::class.simpleName
        private const val STATUS_SIZE_IN_DP = 9f
        private const val NO_ICON = ""
        private const val NOT_ONLINE_ALPHA = 0.38f
    }
}
