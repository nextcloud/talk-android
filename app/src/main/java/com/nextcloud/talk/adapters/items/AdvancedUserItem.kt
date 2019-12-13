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

import android.accounts.Account
import android.view.View
import android.widget.*
import androidx.emoji.widget.EmojiTextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import butterknife.BindView
import butterknife.ButterKnife
import coil.api.load
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import eu.davidea.viewholders.FlexibleViewHolder
import java.util.regex.Pattern

class AdvancedUserItem(
        /**
         * @return the model object
         */

        val model: Participant,
        val entity: UserNgEntity?,
        val account: Account?
) : AbstractFlexibleItem<AdvancedUserItem.UserItemViewHolder>(), IFilterable<String> {
    override fun bindViewHolder(
            adapter: FlexibleAdapter<IFlexible<ViewHolder>>?,
            holder: UserItemViewHolder?,
            position: Int,
            payloads: MutableList<Any>?
    ) {

        if (adapter!!.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder!!.contactDisplayName!!, model.name,
                    adapter.getFilter(String::class.java).toString(),
                    NextcloudTalkApplication.sharedApplication!!
                            .resources.getColor(R.color.colorPrimary)
            )
        } else {
            holder!!.contactDisplayName!!.text = model.name
        }

        holder.serverUrl!!.text = entity!!.baseUrl

        if (entity.baseUrl != null && entity.baseUrl
                        .startsWith("http://") || entity.baseUrl.startsWith("https://")
        ) {
            holder.avatarImageView!!.visibility = View.VISIBLE

            holder.avatarImageView?.load(
                    ApiUtils.getUrlForAvatarWithName(
                            entity.baseUrl,
                            model.userId, R.dimen.avatar_size
                    )
            ) {
                transformations(CircleCropTransformation())
            }
        } else {
            holder.avatarImageView!!.visibility = View.GONE
            val layoutParams = holder.linearLayout!!.layoutParams as RelativeLayout.LayoutParams
            layoutParams.marginStart = NextcloudTalkApplication.sharedApplication!!.applicationContext
                    .resources.getDimension(R.dimen.activity_horizontal_margin)
                    .toInt()
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START)
            holder.linearLayout!!.layoutParams = layoutParams
        }

    }

    override fun createViewHolder(
            view: View,
            adapter: FlexibleAdapter<IFlexible<ViewHolder>>
    ): UserItemViewHolder {
        return UserItemViewHolder(view, adapter)
    }

    override fun equals(other: Any?): Boolean {
        if (other is AdvancedUserItem) {
            val inItem = other as AdvancedUserItem?
            return model == inItem!!.model
        }
        return false
    }

    override fun hashCode(): Int {
        return model.hashCode()
    }

    override fun getLayoutRes(): Int {
        return R.layout.rv_item_conversation
    }

    override fun filter(constraint: String): Boolean {
        return model.name != null && Pattern.compile(
                constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL
        )
                .matcher(model.name.trim(' '))
                .find()
    }

    class UserItemViewHolder
    /**
     * Default constructor.
     */
    (
            view: View,
            adapter: FlexibleAdapter<*>
    ) : FlexibleViewHolder(view, adapter) {

        @JvmField
        @BindView(R.id.name_text)
        var contactDisplayName: EmojiTextView? = null
        @JvmField
        @BindView(R.id.secondary_text)
        var serverUrl: TextView? = null
        @JvmField
        @BindView(R.id.avatar_image)
        var avatarImageView: ImageView? = null
        @JvmField
        @BindView(R.id.linear_layout)
        var linearLayout: LinearLayout? = null
        @JvmField
        @BindView(R.id.more_menu)
        var moreMenuButton: ImageButton? = null
        @JvmField
        @BindView(R.id.password_protected_image_view)
        var passwordProtectedImageView: ImageView? = null

        init {
            ButterKnife.bind(this, view)
            moreMenuButton!!.visibility = View.GONE
            passwordProtectedImageView!!.visibility = View.GONE
        }
    }
}
