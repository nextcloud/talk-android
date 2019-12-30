/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.newarch.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.request.LoadRequest
import coil.target.Target
import coil.transform.Transformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.DisplayUtils


class Images {
    fun getRequestForUrl(
            imageLoader: ImageLoader,
            context: Context,
            url: String,
            userEntity:
            UserNgEntity?,
            target: Target?,
            lifecycleOwner: LifecycleOwner?,
            vararg transformations: Transformation
    ): LoadRequest {
        return LoadRequest(context, imageLoader.defaults) {
            data(url)
            transformations(*transformations)
            lifecycleOwner?.let {
                lifecycle(lifecycleOwner)
            }

            target?.let {
                target(target)
            }

            if (userEntity != null && url.startsWith(userEntity.baseUrl) && (url.contains(
                            "index.php/core/preview?fileId=") || url.contains("index.php/avatar/"))
            ) {
                addHeader("Authorization", userEntity.getCredentials())
            }
        }
    }

    // returns null if it's one-to-one that you need to fetch yourself
    fun getImageForConversation(context: Context, conversation: Conversation): Drawable? {
        conversation.objectType?.let { objectType ->
            when (objectType) {
                "share:password" -> {
                    return DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_file_password_request))
                }
                "file" -> {
                    return DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_file_icon))
                }
                else -> {
                } // do nothing
            }
        }

        when (conversation.type) {
            Conversation.ConversationType.ONE_TO_ONE_CONVERSATION -> {
                return null
            }
            Conversation.ConversationType.GROUP_CONVERSATION -> {
                return DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_people_group_white_24px))
            }
            Conversation.ConversationType.PUBLIC_CONVERSATION -> {
                return DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_link_white_24px))
            }
            else -> {
                // we handle else as Conversation.ConversationType.SYSTEM_CONVERSATION for now
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = context.getDrawable(R.drawable.ic_launcher_background)
                layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground)
                return DisplayUtils.getRoundedDrawable(LayerDrawable(layers))
            }
        }
    }
}
