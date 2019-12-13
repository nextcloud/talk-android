/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
import android.view.View
import coil.api.load
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import java.util.regex.Pattern

class MentionAutocompleteItem(
        val objectId: String?,
        val displayName: String?,
        var source: String?,
        private val currentUser: UserNgEntity
) : AbstractFlexibleItem<UserItem.UserItemViewHolder>(), IFilterable<String> {

    override fun equals(other: Any?): Boolean {
        if (other is MentionAutocompleteItem) {
            val inItem = other as MentionAutocompleteItem?
            return objectId == inItem!!.objectId && displayName == inItem.displayName
        }

        return false
    }

    override fun getLayoutRes(): Int {
        return R.layout.rv_item_mention
    }

    override fun createViewHolder(
            view: View,
            adapter: FlexibleAdapter<IFlexible<*>>
    ): UserItem.UserItemViewHolder {
        return UserItem.UserItemViewHolder(view, adapter)
    }

    @SuppressLint("SetTextI18n")
    override fun bindViewHolder(
            adapter: FlexibleAdapter<IFlexible<*>>,
            holder: UserItem.UserItemViewHolder,
            position: Int,
            payloads: List<Any>
    ) {

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder.contactDisplayName!!, displayName,
                    adapter.getFilter(String::class.java).toString(),
                    NextcloudTalkApplication.sharedApplication!!
                            .resources.getColor(R.color.colorPrimary)
            )
            if (holder.contactMentionId != null) {
                FlexibleUtils.highlightText(
                        holder.contactMentionId!!, "@" + objectId!!,
                        adapter.getFilter(String::class.java).toString(),
                        NextcloudTalkApplication.sharedApplication!!
                                .resources.getColor(R.color.colorPrimary)
                )
            }
        } else {
            holder.contactDisplayName!!.text = displayName
            if (holder.contactMentionId != null) {
                holder.contactMentionId!!.text = "@" + objectId!!
            }
        }

        if (source == "calls") {
            holder.avatarImageView!!.load(R.drawable.ic_people_group_white_24px) {
                transformations(CircleCropTransformation())
            }
        } else {
            var avatarId = objectId
            var avatarUrl = ApiUtils.getUrlForAvatarWithName(
                    currentUser.baseUrl,
                    avatarId, R.dimen.avatar_size_big
            )

            if (source == "guests") {
                avatarId = displayName
                avatarUrl = ApiUtils.getUrlForAvatarWithNameForGuests(
                        currentUser.baseUrl, avatarId,
                        R.dimen.avatar_size_big
                )
            }

            holder.avatarImageView!!.load(avatarUrl) {
                transformations(CircleCropTransformation())
            }
        }
    }

    override fun filter(constraint: String): Boolean {
        return objectId != null && Pattern.compile(
                constraint,
                Pattern.CASE_INSENSITIVE or Pattern.LITERAL
        ).matcher(objectId).find() || displayName != null && Pattern.compile(
                constraint,
                Pattern.CASE_INSENSITIVE or Pattern.LITERAL
        ).matcher(displayName).find()
    }
}
