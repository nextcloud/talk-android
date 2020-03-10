/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import androidx.emoji.widget.EmojiTextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.api.load
import com.google.android.material.imageview.ShapeableImageView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import eu.davidea.viewholders.FlexibleViewHolder
import java.util.regex.Pattern

class UserItem(
        /**
         * @return the model object
         */

        val model: Participant,
        val entity: UserNgEntity,
        private var header: GenericTextHeaderItem?,
        private val activityContext: Context
) : AbstractFlexibleItem<UserItem.UserItemViewHolder>(),
        ISectionable<UserItem.UserItemViewHolder, GenericTextHeaderItem>,
        IFilterable<String> {

    var isOnline = true

    override fun equals(other: Any?): Boolean {
        if (other is UserItem) {
            val inItem = other as UserItem?
            return model.userId == inItem!!.model.userId
        }
        return false
    }

    override fun hashCode(): Int {
        return model.hashCode()
    }

    override fun getLayoutRes(): Int {
        return if (header != null) {
            R.layout.rv_item_contact
        } else {
            R.layout.rv_item_conversation_info_participant
        }
    }

    override fun createViewHolder(
            view: View,
            adapter: FlexibleAdapter<IFlexible<ViewHolder>>
    ): UserItemViewHolder? {
        return UserItemViewHolder(view, adapter)
    }

    override fun bindViewHolder(
            adapter: FlexibleAdapter<IFlexible<ViewHolder>>,
            holder: UserItemViewHolder,
            position: Int,
            payloads: MutableList<Any>
    ) {

        if (holder.checkedImageView != null) {
            if (model.selected) {
                holder.checkedImageView!!.visibility = View.VISIBLE
            } else {
                holder.checkedImageView!!.visibility = View.GONE
            }
        }

        if (!isOnline) {
            if (holder.contactMentionId != null) {
                holder.contactMentionId!!.alpha = 0.38f
            }
            holder.contactDisplayName!!.alpha = 0.38f
            holder.avatarImageView!!.alpha = 0.38f
        } else {
            if (holder.contactMentionId != null) {
                holder.contactMentionId!!.alpha = 1.0f
            }
            holder.contactDisplayName!!.alpha = 1.0f
            holder.avatarImageView!!.alpha = 1.0f
        }

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder.contactDisplayName!!, model.displayName,
                    adapter.getFilter(String::class.java).toString(),
                    activityContext.resources.getColor(R.color.colorPrimary)
            )
        }

        holder.contactDisplayName!!.text = model.displayName

        if (TextUtils.isEmpty(
                        model.displayName
                ) && (model.type == Participant.ParticipantType.GUEST || model.type == Participant
                        .ParticipantType.USER_FOLLOWING_LINK)
        ) {
            holder.contactDisplayName!!.text =
                    NextcloudTalkApplication.sharedApplication!!.getString(R.string.nc_guest)
        }

        if (TextUtils.isEmpty(model.source) || model.source == "users") {
            if (Participant.ParticipantType.GUEST == model.type || Participant.ParticipantType
                            .USER_FOLLOWING_LINK == model.type) {
                var displayName = NextcloudTalkApplication.sharedApplication!!
                        .resources
                        .getString(R.string.nc_guest)

                if (!TextUtils.isEmpty(model.displayName)) {
                    displayName = model.displayName!!
                }

                holder.avatarImageView!!.load(ApiUtils.getUrlForAvatarWithNameForGuests(
                        entity.baseUrl,
                        displayName, R.dimen.avatar_size
                ))

            } else {

                holder.avatarImageView!!.load(ApiUtils.getUrlForAvatarWithNameForGuests(
                        entity.baseUrl,
                        model.userId, R.dimen.avatar_size
                ))

            }
        } else if ("groups" == model.source || "circles" == model.source) {
            holder.avatarImageView?.load(Images().getImageWithBackground(activityContext, R.drawable.ic_people_group_white_24px))
        } else if ("emails" == model.source) {
            holder.avatarImageView?.load(Images().getImageWithBackground(activityContext, R.drawable.ic_baseline_email_24))
        }

        val resources = activityContext.resources

        if (header == null) {
            val participantFlags = model.participantFlags
            when (participantFlags) {
                Participant.ParticipantFlags.NOT_IN_CALL -> {
                    holder.voiceOrSimpleCallImageView!!.visibility = View.GONE
                    holder.videoCallImageView!!.visibility = View.GONE
                }
                Participant.ParticipantFlags.IN_CALL -> {
                    holder.voiceOrSimpleCallImageView!!.background =
                            resources.getDrawable(R.drawable.shape_call_bubble)
                    holder.voiceOrSimpleCallImageView!!.visibility = View.VISIBLE
                    holder.videoCallImageView!!.visibility = View.GONE
                }
                Participant.ParticipantFlags.IN_CALL_WITH_AUDIO -> {
                    holder.voiceOrSimpleCallImageView!!.background =
                            resources.getDrawable(R.drawable.shape_voice_bubble)
                    holder.voiceOrSimpleCallImageView!!.visibility = View.VISIBLE
                    holder.videoCallImageView!!.visibility = View.GONE
                }
                Participant.ParticipantFlags.IN_CALL_WITH_VIDEO -> {
                    holder.voiceOrSimpleCallImageView!!.background =
                            resources.getDrawable(R.drawable.shape_call_bubble)
                    holder.videoCallImageView!!.background =
                            resources.getDrawable(R.drawable.shape_video_bubble)
                    holder.voiceOrSimpleCallImageView!!.visibility = View.VISIBLE
                    holder.videoCallImageView!!.visibility = View.VISIBLE
                }
                Participant.ParticipantFlags.IN_CALL_WITH_AUDIO_AND_VIDEO -> {
                    holder.voiceOrSimpleCallImageView!!.background =
                            resources.getDrawable(R.drawable.shape_voice_bubble)
                    holder.videoCallImageView!!.background =
                            resources.getDrawable(R.drawable.shape_video_bubble)
                    holder.voiceOrSimpleCallImageView!!.visibility = View.VISIBLE
                    holder.videoCallImageView!!.visibility = View.VISIBLE
                }
                else -> {
                    holder.voiceOrSimpleCallImageView!!.visibility = View.GONE
                    holder.videoCallImageView!!.visibility = View.GONE
                }
            }

            if (holder.contactMentionId != null) {
                var userType = ""

                when (EnumParticipantTypeConverter().convertToInt(model.type)) {
                    1, 2 -> userType = NextcloudTalkApplication.sharedApplication!!
                            .getString(R.string.nc_moderator)
                    3 -> userType = NextcloudTalkApplication.sharedApplication!!
                            .getString(R.string.nc_user)
                    4 -> userType = NextcloudTalkApplication.sharedApplication!!
                            .getString(R.string.nc_guest)
                    5 -> userType = NextcloudTalkApplication.sharedApplication!!
                            .getString(R.string.nc_following_link)
                    else -> {
                    }
                }

                if (holder.contactMentionId!!.text != userType) {
                    holder.contactMentionId!!.text = userType
                    holder.contactMentionId!!.setTextColor(
                            activityContext.resources.getColor(R.color.colorPrimary)
                    )
                }
            }
        }
    }

    override fun filter(constraint: String): Boolean {
        return model.displayName != null && (Pattern.compile(
                        constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL
                )
                .matcher(model.displayName!!.trim(' '))
                .find() || Pattern.compile(constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(model.userId!!.trim(' '))
                .find())
    }

    override fun getHeader(): GenericTextHeaderItem? {
        return header
    }

    override fun setHeader(header: GenericTextHeaderItem) {
        this.header = header
    }

    class UserItemViewHolder
    /**
     * Default constructor.
     */
    (
            view: View,
            adapter: FlexibleAdapter<*>
    ) : FlexibleViewHolder(view, adapter) {

        var contactDisplayName: EmojiTextView? = null
        var avatarImageView: ShapeableImageView? = null
        var contactMentionId: EmojiTextView? = null
        var voiceOrSimpleCallImageView: ImageView? = null
        var videoCallImageView: ImageView? = null
        var checkedImageView: ImageView? = null

        init {
            contactDisplayName = view.findViewById(R.id.participantNameTextView)
            avatarImageView = view.findViewById(R.id.avatarImageView)
            contactMentionId = view.findViewById(R.id.secondary_text)
            voiceOrSimpleCallImageView = view.findViewById(R.id.voiceOrSimpleCallImageView)
            videoCallImageView = view.findViewById(R.id.videoCallImageView)
            checkedImageView = view.findViewById(R.id.checkedImageView)
        }
    }
}
