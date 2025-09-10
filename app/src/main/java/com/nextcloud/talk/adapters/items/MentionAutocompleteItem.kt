/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021-2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import com.nextcloud.talk.PhoneUtils.isPhoneNumber
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ParticipantItem.ParticipantItemViewHolder
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.loadDefaultAvatar
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.extensions.loadGuestAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import java.util.Objects
import java.util.regex.Pattern

class MentionAutocompleteItem(
    mention: Mention,
    private val currentUser: User,
    private val context: Context,
    @JvmField val roomToken: String,
    private val viewThemeUtils: ViewThemeUtils
) : AbstractFlexibleItem<ParticipantItemViewHolder>(),
    IFilterable<String?> {
    @JvmField
    var source: String?

    @JvmField
    val mentionId: String?

    @JvmField
    val objectId: String?

    @JvmField
    val displayName: String?
    private val status: String?
    private val statusIcon: String?
    private val statusMessage: String?

    init {
        mentionId = mention.mentionId
        objectId = mention.id

        displayName = if (!mention.label.isNullOrBlank()) {
            mention.label
        } else if ("guests" == mention.source || "emails" == mention.source) {
            context.resources.getString(R.string.nc_guest)
        } else {
            ""
        }

        source = mention.source
        status = mention.status
        statusIcon = mention.statusIcon
        statusMessage = mention.statusMessage
    }

    override fun equals(o: Any?): Boolean =
        if (o is MentionAutocompleteItem) {
            objectId == o.objectId && displayName == o.displayName
        } else {
            false
        }

    override fun hashCode(): Int = Objects.hash(objectId, displayName)

    override fun getLayoutRes(): Int = R.layout.rv_item_conversation_info_participant

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<*>?>?): ParticipantItemViewHolder =
        ParticipantItemViewHolder(view, adapter)

    @SuppressLint("SetTextI18n")
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<*>?>,
        holder: ParticipantItemViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        holder.binding.nameText.setTextColor(
            ResourcesCompat.getColor(
                context.resources,
                R.color.conversation_item_header,
                null
            )
        )
        if (adapter.hasFilter()) {
            viewThemeUtils.talk.themeAndHighlightText(
                holder.binding.nameText,
                displayName,
                adapter.getFilter(String::class.java).toString()
            )
            viewThemeUtils.talk.themeAndHighlightText(
                holder.binding.secondaryText,
                "@$objectId",
                adapter.getFilter(String::class.java).toString()
            )
        } else {
            holder.binding.nameText.text = displayName
        }
        setAvatar(holder, objectId)
        drawStatus(holder)
    }

    private fun setAvatar(holder: ParticipantItemViewHolder, objectId: String?) {
        when (source) {
            SOURCE_CALLS -> {
                run {
                    if (isPhoneNumber(displayName)) {
                        holder.binding.avatarView.loadUserAvatar(
                            viewThemeUtils.talk.themePlaceholderAvatar(
                                holder.binding.avatarView,
                                R.drawable.ic_phone_small
                            )
                        )
                    } else {
                        holder.binding.avatarView.loadUserAvatar(
                            viewThemeUtils.talk.themePlaceholderAvatar(
                                holder.binding.avatarView,
                                R.drawable.ic_avatar_group_small
                            )
                        )
                    }
                }
            }

            SOURCE_GROUPS -> {
                holder.binding.avatarView.loadUserAvatar(
                    viewThemeUtils.talk.themePlaceholderAvatar(
                        holder.binding.avatarView,
                        R.drawable
                            .ic_avatar_group_small
                    )
                )
            }

            SOURCE_FEDERATION -> {
                val darkTheme = if (DisplayUtils.isDarkModeOn(context)) 1 else 0
                holder.binding.avatarView.loadFederatedUserAvatar(
                    currentUser,
                    currentUser.baseUrl!!,
                    roomToken,
                    objectId!!,
                    darkTheme,
                    requestBigSize = true,
                    ignoreCache = false
                )
            }

            SOURCE_GUESTS, SOURCE_EMAILS -> {
                if (displayName.equals(context.resources.getString(R.string.nc_guest))) {
                    holder.binding.avatarView.loadDefaultAvatar(viewThemeUtils)
                } else {
                    holder.binding.avatarView.loadGuestAvatar(currentUser, displayName!!, false)
                }
            }

            SOURCE_TEAMS -> {
                holder.binding.avatarView.loadUserAvatar(
                    viewThemeUtils.talk.themePlaceholderAvatar(
                        holder.binding.avatarView,
                        R.drawable
                            .ic_avatar_team_small
                    )
                )
            }

            else -> {
                holder.binding.avatarView.loadUserAvatar(
                    currentUser,
                    objectId!!,
                    requestBigSize = true,
                    ignoreCache = false
                )
            }
        }
    }

    private fun drawStatus(holder: ParticipantItemViewHolder) {
        val size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, context)
        holder.binding.userStatusImage.setImageDrawable(
            StatusDrawable(
                status,
                NO_ICON,
                size,
                context.resources.getColor(R.color.bg_default),
                context
            )
        )
        if (statusMessage != null) {
            holder.binding.conversationInfoStatusMessage.text = statusMessage
            alignUsernameVertical(holder, 0f)
        } else {
            holder.binding.conversationInfoStatusMessage.text = ""
            alignUsernameVertical(holder, NO_USER_STATUS_DP_FROM_TOP)
        }
        if (!statusIcon.isNullOrEmpty()) {
            holder.binding.participantStatusEmoji.setText(statusIcon)
        } else {
            holder.binding.participantStatusEmoji.visibility = View.GONE
        }
        if (status != null && status == StatusType.DND.string) {
            if (statusMessage.isNullOrEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.dnd)
            }
        } else if (status != null && status == StatusType.BUSY.string) {
            if (statusMessage.isNullOrEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.busy)
            }
        } else if (status != null && status == StatusType.AWAY.string) {
            if (statusMessage.isNullOrEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.away)
            }
        }
    }

    private fun alignUsernameVertical(holder: ParticipantItemViewHolder, densityPixelsFromTop: Float) {
        val layoutParams = holder.binding.nameText.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.topMargin = DisplayUtils.convertDpToPixel(densityPixelsFromTop, context).toInt()
        holder.binding.nameText.setLayoutParams(layoutParams)
    }

    override fun filter(constraint: String?): Boolean =
        objectId != null &&
            Pattern
                .compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(objectId)
                .find() ||
            displayName != null &&
            Pattern
                .compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(displayName)
                .find()

    companion object {
        private const val STATUS_SIZE_IN_DP = 9f
        private const val NO_ICON = ""
        private const val NO_USER_STATUS_DP_FROM_TOP: Float = 10f
        const val SOURCE_CALLS = "calls"
        const val SOURCE_GUESTS = "guests"
        const val SOURCE_GROUPS = "groups"
        const val SOURCE_EMAILS = "emails"
        const val SOURCE_TEAMS = "teams"
        const val SOURCE_FEDERATION = "federated_users"
    }
}
