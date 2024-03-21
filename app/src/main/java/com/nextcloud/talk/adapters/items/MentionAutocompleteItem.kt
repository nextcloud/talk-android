/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.adapters.items

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ParticipantItem.ParticipantItemViewHolder
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
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
) : AbstractFlexibleItem<ParticipantItemViewHolder>(), IFilterable<String?> {
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
        displayName = mention.label
        source = mention.source
        status = mention.status
        statusIcon = mention.statusIcon
        statusMessage = mention.statusMessage
    }

    override fun equals(o: Any?): Boolean {
        return if (o is MentionAutocompleteItem) {
            objectId == o.objectId && displayName == o.displayName
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(objectId, displayName)
    }

    override fun getLayoutRes(): Int {
        return R.layout.rv_item_conversation_info_participant
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<*>?>?): ParticipantItemViewHolder {
        return ParticipantItemViewHolder(view, adapter)
    }

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
            holder.binding.secondaryText.text = "@$objectId"
        }
        var avatarId = objectId
        when (source) {
            SOURCE_CALLS -> {
                run {}
                run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.binding.avatarView.loadUserAvatar(
                            viewThemeUtils.talk.themePlaceholderAvatar(
                                holder.binding.avatarView,
                                R.drawable.ic_avatar_group
                            )
                        )
                    } else {
                        holder.binding.avatarView.loadUserAvatar(R.drawable.ic_circular_group)
                    }
                }
            }

            SOURCE_GROUPS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.binding.avatarView.loadUserAvatar(
                        viewThemeUtils.talk.themePlaceholderAvatar(
                            holder.binding.avatarView,
                            R.drawable.ic_avatar_group
                        )
                    )
                } else {
                    holder.binding.avatarView.loadUserAvatar(R.drawable.ic_circular_group)
                }
            }

            SOURCE_FEDERATION -> {
                val darkTheme = if (DisplayUtils.isDarkModeOn(context)) 1 else 0
                holder.binding.avatarView.loadFederatedUserAvatar(
                    currentUser,
                    currentUser.baseUrl!!,
                    roomToken,
                    avatarId!!,
                    darkTheme,
                    true,
                    false
                )
            }

            SOURCE_GUESTS -> {
                run { avatarId = displayName }
                run { holder.binding.avatarView.loadUserAvatar(currentUser, avatarId!!, true, false) }
            }

            else -> {
                holder.binding.avatarView.loadUserAvatar(currentUser, avatarId!!, true, false)
            }
        }
        drawStatus(holder)
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
            alignUsernameVertical(holder, 10f)
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

    override fun filter(constraint: String?): Boolean {
        return objectId != null &&
            Pattern
                .compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(objectId)
                .find() ||
            displayName != null &&
            Pattern
                .compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(displayName)
                .find()
    }

    companion object {
        private const val STATUS_SIZE_IN_DP = 9f
        private const val NO_ICON = ""
        const val SOURCE_CALLS = "calls"
        const val SOURCE_GUESTS = "guests"
        const val SOURCE_GROUPS = "groups"
        const val SOURCE_FEDERATION = "federated_users"
    }
}
