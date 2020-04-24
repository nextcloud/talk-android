/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.nextcloud.talk.newarch.features.settingsflow.settings

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.api.load
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import kotlinx.android.synthetic.main.user_item.view.*
import kotlinx.android.synthetic.main.user_item.view.avatarImageView
import org.koin.core.KoinComponent

open class SettingsPresenter<T : Any>(context: Context, onElementClick: ((Page, Holder, Element<T>) -> Unit)?, private val onMoreOptionsClick: ((User) -> Unit)?) : Presenter<T>(context, onElementClick), KoinComponent {
    override val elementTypes: Collection<Int>
        get() = listOf(SettingsElementType.USER.ordinal, SettingsElementType.NEW_USER.ordinal)

    override fun onCreate(parent: ViewGroup, elementType: Int): Holder {
        return Holder(getLayoutInflater().inflate(R.layout.user_item, parent, false))
    }

    override fun onBind(page: Page, holder: Holder, element: Element<T>, payloads: List<Any>) {
        super.onBind(page, holder, element, payloads)

        if (element.type == SettingsElementType.USER.ordinal) {
            val user = element.data as User
            holder.itemView.userProgressBar.isVisible = user.status == UserStatus.PENDING_DELETE

            if (user.status == UserStatus.PENDING_DELETE) {
                holder.itemView.setBackgroundColor(context.resources.getColor(R.color.nc_darkRed))
                holder.itemView.background.alpha = 191
                holder.itemView.userMoreOptionsView.visibility = View.INVISIBLE
            } else {
                if (user.status == UserStatus.ACTIVE) {
                    holder.itemView.setBackgroundColor(context.resources.getColor(R.color.colorPrimary))
                    holder.itemView.background.alpha = 191
                } else {
                    holder.itemView.setBackgroundColor(0)
                }
                holder.itemView.userMoreOptionsView.visibility = View.VISIBLE
                holder.itemView.userMoreOptionsView.setOnClickListener {
                    onMoreOptionsClick?.invoke(user)
                }
            }
            val baseUrl = if (user.status == UserStatus.ACTIVE) "" else " (${user.baseUrl.replace("http://", "").replace("https://", "")})"
            val displayName = "${user.displayName}$baseUrl"
            holder.itemView.userDisplayName.text = displayName
            holder.itemView.avatarImageView.load(ApiUtils.getUrlForAvatarWithName(user.baseUrl, user.userId, R.dimen.avatar_size)) {
                addHeader("Authorization", user.getCredentials())
                placeholder(BitmapDrawable(Images().getImageWithBackground(context, R.drawable.ic_user)))
                fallback(BitmapDrawable(Images().getImageWithBackground(context, R.drawable.ic_user)))
            }
        } else {
            holder.itemView.userDisplayName.text = context.resources.getString(R.string.nc_settings_new_account)
            holder.itemView.avatarImageView.load(Images().getImageWithBackground(context = context, drawableId = R.drawable.ic_add_white_24px, foregroundColorTint = R.color.colorPrimary))
            holder.itemView.userProgressBar.isVisible = false
            holder.itemView.userMoreOptionsView.visibility = View.GONE
        }
    }
}