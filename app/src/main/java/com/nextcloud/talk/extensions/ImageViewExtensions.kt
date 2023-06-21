/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2023 Marcel Hibbe (dev@mhibbe.de)
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

@file:Suppress("TooManyFunctions")

package com.nextcloud.talk.extensions

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.Log
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.result
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

private const val ROUNDING_PIXEL = 16f
private const val TAG = "ImageViewExtensions"

@Deprecated("use other constructor that expects com.nextcloud.talk.models.domain.ConversationModel")
fun ImageView.loadConversationAvatar(
    user: User,
    conversation: Conversation,
    ignoreCache: Boolean,
    viewThemeUtils: ViewThemeUtils?
): io.reactivex.disposables.Disposable {
    return loadConversationAvatar(
        user,
        ConversationModel.mapToConversationModel(conversation),
        ignoreCache,
        viewThemeUtils
    )
}

fun ImageView.loadConversationAvatar(
    user: User,
    conversation: ConversationModel,
    ignoreCache: Boolean,
    viewThemeUtils: ViewThemeUtils?
): io.reactivex.disposables.Disposable {
    val imageRequestUri = ApiUtils.getUrlForConversationAvatarWithVersion(
        1,
        user.baseUrl,
        conversation.token,
        DisplayUtils.isDarkModeOn(this.context),
        conversation.avatarVersion
    )

    if (conversation.avatarVersion.isNullOrEmpty() && viewThemeUtils != null) {
        when (conversation.type) {
            ConversationType.ROOM_GROUP_CALL ->
                return loadDefaultGroupCallAvatar(viewThemeUtils)

            ConversationType.ROOM_PUBLIC_CALL ->
                return loadDefaultPublicCallAvatar(viewThemeUtils)

            else -> {}
        }
    }

    // these placeholders are only used when the request fails completely. The server also return default avatars
    // when no own images are set. (although these default avatars can not be themed for the android app..)
    val errorPlaceholder =
        when (conversation.type) {
            ConversationType.ROOM_GROUP_CALL ->
                ContextCompat.getDrawable(context, R.drawable.ic_circular_group)

            ConversationType.ROOM_PUBLIC_CALL ->
                ContextCompat.getDrawable(context, R.drawable.ic_circular_link)

            else -> ContextCompat.getDrawable(context, R.drawable.account_circle_96dp)
        }

    return loadAvatarInternal(user, imageRequestUri, ignoreCache, errorPlaceholder)
}

fun ImageView.loadUserAvatar(
    user: User,
    avatarId: String,
    requestBigSize: Boolean = true,
    ignoreCache: Boolean
): io.reactivex.disposables.Disposable {
    val imageRequestUri = ApiUtils.getUrlForAvatar(
        user.baseUrl,
        avatarId,
        requestBigSize
    )

    return loadAvatarInternal(user, imageRequestUri, ignoreCache, null)
}

@OptIn(ExperimentalCoilApi::class)
private fun ImageView.loadAvatarInternal(
    user: User?,
    url: String,
    ignoreCache: Boolean,
    errorPlaceholder: Drawable?
): io.reactivex.disposables.Disposable {
    val cachePolicy = if (ignoreCache) {
        CachePolicy.WRITE_ONLY
    } else {
        CachePolicy.ENABLED
    }

    if (ignoreCache && this.result is SuccessResult) {
        val result = this.result as SuccessResult
        val memoryCacheKey = result.memoryCacheKey
        val memoryCache = context.imageLoader.memoryCache
        memoryCacheKey?.let { memoryCache?.remove(it) }

        val diskCacheKey = result.diskCacheKey
        val diskCache = context.imageLoader.diskCache
        diskCacheKey?.let { diskCache?.remove(it) }
    }

    return DisposableWrapper(
        load(url) {
            user?.let {
                addHeader(
                    "Authorization",
                    ApiUtils.getCredentials(user.username, user.token)
                )
            }
            transformations(CircleCropTransformation())
            error(errorPlaceholder ?: ContextCompat.getDrawable(context, R.drawable.account_circle_96dp))
            fallback(errorPlaceholder ?: ContextCompat.getDrawable(context, R.drawable.account_circle_96dp))
            listener(onError = { _, result ->
                Log.w(TAG, "Can't load avatar with URL: $url", result.throwable)
            })
            memoryCachePolicy(cachePolicy)
            diskCachePolicy(cachePolicy)
        }
    )
}

@Deprecated("Use function loadAvatar", level = DeprecationLevel.WARNING)
fun ImageView.loadAvatarWithUrl(user: User? = null, url: String): io.reactivex.disposables.Disposable {
    return loadAvatarInternal(user, url, false, null)
}

fun ImageView.loadThumbnail(url: String, user: User): io.reactivex.disposables.Disposable {
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

    if (url.startsWith(user.baseUrl!!) &&
        (url.contains("index.php/core/preview") || url.contains("/avatar/"))
    ) {
        requestBuilder.addHeader(
            "Authorization",
            ApiUtils.getCredentials(user.username, user.token)
        )
    }

    return DisposableWrapper(context.imageLoader.enqueue(requestBuilder.build()))
}

fun ImageView.loadImage(url: String, user: User, placeholder: Drawable? = null): io.reactivex.disposables.Disposable {
    var finalPlaceholder = placeholder
    if (finalPlaceholder == null) {
        finalPlaceholder = ContextCompat.getDrawable(context!!, R.drawable.ic_mimetype_file)
    }

    val requestBuilder = ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .target(this)
        .placeholder(finalPlaceholder)
        .error(finalPlaceholder)
        .transformations(RoundedCornersTransformation(ROUNDING_PIXEL, ROUNDING_PIXEL, ROUNDING_PIXEL, ROUNDING_PIXEL))

    if (url.startsWith(user.baseUrl!!) &&
        (url.contains("index.php/core/preview") || url.contains("/avatar/"))
    ) {
        requestBuilder.addHeader(
            "Authorization",
            ApiUtils.getCredentials(user.username, user.token)
        )
    }

    return DisposableWrapper(context.imageLoader.enqueue(requestBuilder.build()))
}

fun ImageView.loadAvatarOrImagePreview(
    url: String,
    user: User,
    placeholder: Drawable? = null
): io.reactivex.disposables.Disposable {
    return if (url.contains("/avatar/")) {
        loadAvatarInternal(user, url, false, null)
    } else {
        loadImage(url, user, placeholder)
    }
}

fun ImageView.loadUserAvatar(any: Any?): io.reactivex.disposables.Disposable {
    return DisposableWrapper(
        load(any) {
            transformations(CircleCropTransformation())
        }
    )
}

fun ImageView.loadSystemAvatar(): io.reactivex.disposables.Disposable {
    val data: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
        layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        val layerDrawable = LayerDrawable(layers)
        layerDrawable
    } else {
        R.mipmap.ic_launcher
    }

    return DisposableWrapper(
        load(data) {
            transformations(CircleCropTransformation())
        }
    )
}

fun ImageView.loadChangelogBotAvatar(): io.reactivex.disposables.Disposable {
    return loadSystemAvatar()
}

fun ImageView.loadBotsAvatar(): io.reactivex.disposables.Disposable {
    return loadUserAvatar(
        TextDrawable.builder()
            .beginConfig()
            .bold()
            .endConfig()
            .buildRound(
                ">",
                ResourcesCompat.getColor(context.resources, R.color.black, null)
            )
    )
}

fun ImageView.loadDefaultGroupCallAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_group) as Any
    } else {
        R.drawable.ic_circular_group
    }
    return loadUserAvatar(data)
}

fun ImageView.loadDefaultPublicCallAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_link) as Any
    } else {
        R.drawable.ic_circular_link
    }
    return loadUserAvatar(data)
}

fun ImageView.loadMailAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_mail) as Any
    } else {
        R.drawable.ic_circular_mail
    }
    return loadUserAvatar(data)
}

fun ImageView.loadGuestAvatar(user: User, name: String, big: Boolean): io.reactivex.disposables.Disposable {
    return loadGuestAvatar(user.baseUrl!!, name, big)
}

fun ImageView.loadGuestAvatar(baseUrl: String, name: String, big: Boolean): io.reactivex.disposables.Disposable {
    val imageRequestUri = ApiUtils.getUrlForGuestAvatar(
        baseUrl,
        name,
        big
    )
    return DisposableWrapper(
        load(imageRequestUri) {
            transformations(CircleCropTransformation())
            listener(onError = { _, result ->
                Log.w(TAG, "Can't load guest avatar with URL: $imageRequestUri", result.throwable)
            })
        }
    )
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
