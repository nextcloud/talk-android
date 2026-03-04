/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.PhoneUtils.isPhoneNumber
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_CALLS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_EMAILS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_FEDERATION
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_GROUPS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_GUESTS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_TEAMS
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemConversationInfoParticipantBinding
import com.nextcloud.talk.extensions.loadDefaultAvatar
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils

class MentionAutocompleteAdapter(
    private val context: Context,
    private val currentUser: User,
    private val viewThemeUtils: ViewThemeUtils,
    private val roomToken: String,
    private val onItemClick: (MentionAutocompleteItem) -> Unit
) : RecyclerView.Adapter<MentionAutocompleteAdapter.ViewHolder>() {

    private val items: MutableList<MentionAutocompleteItem> = mutableListOf()
    var filterQuery: String = ""

    fun updateDataSet(newItems: List<MentionAutocompleteItem>) {
        items.clear()
        items.addAll(newItems)
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: RvItemConversationInfoParticipantBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RvItemConversationInfoParticipantBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = items.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.setOnClickListener { onItemClick(item) }

        holder.binding.nameText.setTextColor(
            ResourcesCompat.getColor(
                context.resources,
                R.color.conversation_item_header,
                null
            )
        )

        if (filterQuery.isNotEmpty()) {
            viewThemeUtils.talk.themeAndHighlightText(
                holder.binding.nameText,
                item.displayName,
                filterQuery
            )
            viewThemeUtils.talk.themeAndHighlightText(
                holder.binding.secondaryText,
                "@${item.objectId}",
                filterQuery
            )
        } else {
            holder.binding.nameText.text = item.displayName
        }

        setAvatar(holder, item)
        drawStatus(holder, item)
    }

    private fun setAvatar(holder: ViewHolder, item: MentionAutocompleteItem) {
        val avatarView = holder.binding.avatarView
        when (item.source) {
            SOURCE_CALLS -> {
                val placeholder = if (isPhoneNumber(item.displayName)) {
                    R.drawable.ic_phone_small
                } else {
                    R.drawable.ic_avatar_group_small
                }
                avatarView.loadUserAvatar(viewThemeUtils.talk.themePlaceholderAvatar(avatarView, placeholder))
            }

            SOURCE_GROUPS ->
                avatarView.loadUserAvatar(
                    viewThemeUtils.talk.themePlaceholderAvatar(avatarView, R.drawable.ic_avatar_group_small)
                )

            SOURCE_FEDERATION -> {
                val darkTheme = if (DisplayUtils.isDarkModeOn(context)) 1 else 0
                avatarView.loadFederatedUserAvatar(
                    currentUser,
                    currentUser.baseUrl!!,
                    roomToken,
                    item.objectId!!,
                    darkTheme,
                    requestBigSize = true,
                    ignoreCache = false
                )
            }

            SOURCE_GUESTS, SOURCE_EMAILS -> {
                if (item.displayName.equals(context.resources.getString(R.string.nc_guest))) {
                    avatarView.loadDefaultAvatar(viewThemeUtils)
                } else {
                    avatarView.loadGuestAvatar(currentUser, item.displayName!!, false)
                }
            }

            SOURCE_TEAMS ->
                avatarView.loadUserAvatar(
                    viewThemeUtils.talk.themePlaceholderAvatar(avatarView, R.drawable.ic_avatar_team_small)
                )

            else -> avatarView.loadUserAvatar(currentUser, item.objectId!!, requestBigSize = true, ignoreCache = false)
        }
    }

    private fun drawStatus(holder: ViewHolder, item: MentionAutocompleteItem) {
        val size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, context)

        holder.binding.userStatusImage.setImageDrawable(
            StatusDrawable(
                item.status,
                NO_ICON,
                size,
                context.resources.getColor(R.color.bg_default),
                context
            )
        )

        val statusMessage = StatusType.getDescription(item.statusMessage, context)
        holder.binding.conversationInfoStatusMessage.text = statusMessage
        alignUsernameVertical(holder, if (statusMessage.isEmpty()) NO_USER_STATUS_DP_FROM_TOP else 0f)

        holder.binding.participantStatusEmoji.run {
            if (!item.statusIcon.isNullOrEmpty()) {
                setText(item.statusIcon)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun alignUsernameVertical(holder: ViewHolder, densityPixelsFromTop: Float) {
        val layoutParams = holder.binding.nameText.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.topMargin = DisplayUtils.convertDpToPixel(densityPixelsFromTop, context).toInt()
        holder.binding.nameText.layoutParams = layoutParams
    }

    companion object {
        private const val STATUS_SIZE_IN_DP = 9f
        private const val NO_ICON = ""
        private const val NO_USER_STATUS_DP_FROM_TOP: Float = 10f
    }
}
