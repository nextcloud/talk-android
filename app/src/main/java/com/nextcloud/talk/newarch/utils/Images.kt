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

package com.nextcloud.talk.newarch.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.view.Gravity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.request.LoadRequest
import coil.target.Target
import coil.transform.Transformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.DisplayUtils

class Images {
    fun getRequestForUrl(
            imageLoader: ImageLoader,
            context: Context,
            url: String,
            userEntity:
            User?,
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

    fun getImageWithBackground(context: Context, drawableId: Int, backgroundDrawableId: Int = R.color.bg_message_list_incoming_bubble, foregroundColorTint: Int? = null): Bitmap {
        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = context.getDrawable(backgroundDrawableId)
        var scale = 0.25f
        if (drawableId == R.drawable.ic_baseline_email_24 || drawableId == R.drawable.ic_link_white_24px || drawableId == R.drawable.ic_add_white_24px) {
            scale = 0.5f
        } else if (drawableId == R.drawable.ic_user) {
            scale = 0.625f
        }

        layers[1] = ScaleDrawable(context.getDrawable(drawableId), Gravity.CENTER, scale, scale)
        if (foregroundColorTint != null) {
            layers[1]?.setTint(context.resources.getColor(foregroundColorTint))
        }
        layers[0]?.level = 0
        layers[1]?.level = 1

        return LayerDrawable(layers).toBitmap()
    }

    // returns null if it's one-to-one that you need to fetch yourself
    fun getImageForConversation(context: Context, conversation: Conversation, fallback: Boolean = false): Drawable? {
        conversation.objectType?.let { objectType ->
            when (objectType) {
                "share:password" -> {
                    return DisplayUtils.getRoundedDrawableFromBitmap(getImageWithBackground(context, R.drawable.ic_file_password_request))
                }
                "file" -> {
                    return DisplayUtils.getRoundedDrawableFromBitmap(getImageWithBackground(context, R.drawable.ic_file_icon))
                }
                else -> {
                } // do nothing
            }
        }

        when (conversation.type) {
            Conversation.ConversationType.ONE_TO_ONE_CONVERSATION -> {
                if (fallback) {
                    return DisplayUtils.getRoundedDrawableFromBitmap(getImageWithBackground(context, R.drawable.ic_user))
                }

                return null
            }
            Conversation.ConversationType.GROUP_CONVERSATION -> {
                return DisplayUtils.getRoundedDrawableFromBitmap(getImageWithBackground(context, R.drawable.ic_people_group_white_24px))
            }
            Conversation.ConversationType.PUBLIC_CONVERSATION -> {
                return DisplayUtils.getRoundedDrawableFromBitmap(getImageWithBackground(context, R.drawable.ic_link_white_24px))
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
