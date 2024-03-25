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
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadMailAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.InCallFlags
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
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
    private val viewThemeUtils: ViewThemeUtils
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
        if (!isOnline) {
            holder.binding.nameText.setTextColor(
                ResourcesCompat.getColor(
                    holder.binding.nameText.context.resources,
                    R.color.medium_emphasis_text,
                    null
                )
            )
            holder.binding.avatarView.setAlpha(0.38f)
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
        if (TextUtils.isEmpty(model.displayName) &&
            (
                model.type == Participant.ParticipantType.GUEST ||
                    model.type == Participant.ParticipantType.USER_FOLLOWING_LINK
                )
        ) {
            holder.binding.nameText.text = sharedApplication!!.getString(R.string.nc_guest)
        }

        // when(){        // check if    model.source    can be removed!
        //
        // }

        if (model.calculatedActorType == Participant.ActorType.GROUPS ||
            model.calculatedActorType == Participant.ActorType.CIRCLES
        ) {
            holder.binding.avatarView.loadDefaultGroupCallAvatar(viewThemeUtils)
        } else if (model.calculatedActorType == Participant.ActorType.EMAILS) {
            holder.binding.avatarView.loadMailAvatar(viewThemeUtils)
        } else if (model.calculatedActorType == Participant.ActorType.GUESTS ||
            model.type == Participant.ParticipantType.GUEST ||
            model.type == Participant.ParticipantType.GUEST_MODERATOR
        ) {
            var displayName: String? = sharedApplication!!.resources.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(model.displayName)) {
                displayName = model.displayName
            }
            holder.binding.avatarView.loadGuestAvatar(user, displayName!!, false)
        } else if (model.calculatedActorType == Participant.ActorType.USERS) {
            holder.binding.avatarView
                .loadUserAvatar(user, model.calculatedActorId!!, true, false)
        }

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
        var userType = ""
        when (EnumParticipantTypeConverter().convertToInt(model.type)) {
            1, 2, 6 -> userType = sharedApplication!!.getString(R.string.nc_moderator)

            3 -> {
                userType = sharedApplication!!.getString(R.string.nc_user)
                if (model.calculatedActorType == Participant.ActorType.GROUPS) {
                    userType = sharedApplication!!.getString(R.string.nc_group)
                }
                if (model.calculatedActorType == Participant.ActorType.CIRCLES) {
                    userType = sharedApplication!!.getString(R.string.nc_team)
                }
            }

            4 -> {
                userType = sharedApplication!!.getString(R.string.nc_guest)
                if (model.calculatedActorType == Participant.ActorType.EMAILS) {
                    userType = sharedApplication!!.getString(R.string.nc_email)
                }
            }

            5 -> userType = sharedApplication!!.getString(R.string.nc_following_link)

            else -> {}
        }
        if (userType != sharedApplication!!.getString(R.string.nc_user)
        ) {
            holder.binding.secondaryText.text = "($userType)"
        }
    }

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
        return model.displayName != null &&
            (
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                    .matcher(model.displayName!!.trim { it <= ' ' }).find() ||
                    Pattern.compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                        .matcher(model.calculatedActorId!!.trim { it <= ' ' }).find()
                )
    }

    class ParticipantItemViewHolder internal constructor(view: View?, adapter: FlexibleAdapter<*>?) :
        FlexibleViewHolder(view, adapter) {
        var binding: RvItemConversationInfoParticipantBinding

        /**
         * Default constructor.
         */
        init {
            binding = RvItemConversationInfoParticipantBinding.bind(view!!)
        }
    }

    companion object {
        private const val STATUS_SIZE_IN_DP = 9f
        private const val NO_ICON = ""
    }
}
