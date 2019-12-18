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
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.request.LoadRequest
import coil.target.Target
import coil.transform.Transformation
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials

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
}