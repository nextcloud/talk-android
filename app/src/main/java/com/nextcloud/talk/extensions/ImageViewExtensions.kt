/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.extensions

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.load
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils

fun ImageView.loadAvatar(user: User, avatar: String): io.reactivex.disposables.Disposable {

    val imageRequestUri = ApiUtils.getUrlForAvatar(
        user.baseUrl,
        avatar,
        true
    )

    return DisposableWrapper(
        load(imageRequestUri) {
            addHeader(
                "Authorization",
                ApiUtils.getCredentials(user.username, user.token)
            )
            transformations(CircleCropTransformation())
        }
    )
}

fun ImageView.loadThumbnail(url: String?, user: User): io.reactivex.disposables.Disposable {
    val requestBuilder = ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .target(this)
        .transformations(CircleCropTransformation())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
        layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        requestBuilder.placeholder(LayerDrawable(layers))
    } else {
        requestBuilder.placeholder(R.mipmap.ic_launcher)
    }

    if (url != null &&
        url.startsWith(user.baseUrl!!) &&
        (url.contains("index.php/core/preview?fileId=") || url.contains("/avatar/"))
    ) {
        requestBuilder.addHeader(
            "Authorization",
            ApiUtils.getCredentials(user.username, user.token)
        )
    }

    return DisposableWrapper(load(requestBuilder.build()))
}

fun ImageView.loadAvatar(any: Any?): io.reactivex.disposables.Disposable {
    return DisposableWrapper(load(any))
}

private class DisposableWrapper(private val disposable: coil.request.Disposable) : io.reactivex.disposables
    .Disposable {

    override fun dispose() {
        disposable.dispose()
    }

    override fun isDisposed(): Boolean {
        return disposable.isDisposed
    }
}
