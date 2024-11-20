/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("TooManyFunctions")

package com.nextcloud.talk.extensions

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.result
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.TextDrawable

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
        ConversationModel.mapToConversationModel(conversation, user),
        ignoreCache,
        viewThemeUtils
    )
}

@Suppress("ReturnCount")
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
            ConversationEnums.ConversationType.ROOM_GROUP_CALL ->
                return loadDefaultGroupCallAvatar(viewThemeUtils)

            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
                return loadDefaultPublicCallAvatar(viewThemeUtils)

            else -> {}
        }
    }

    // these placeholders are only used when the request fails completely. The server also return default avatars
    // when no own images are set. (although these default avatars can not be themed for the android app..)
    val errorPlaceholder =
        when (conversation.type) {
            ConversationEnums.ConversationType.ROOM_GROUP_CALL ->
                ContextCompat.getDrawable(context, R.drawable.ic_circular_group)

            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
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
        user.baseUrl!!,
        avatarId,
        requestBigSize
    )

    return loadAvatarInternal(user, imageRequestUri, ignoreCache, null)
}

fun ImageView.loadFederatedUserAvatar(message: ChatMessage): io.reactivex.disposables.Disposable {
    val cloudId = message.actorId!!
    val darkTheme = if (DisplayUtils.isDarkModeOn(context)) 1 else 0
    val ignoreCache = false
    val requestBigSize = true
    return loadFederatedUserAvatar(
        message.activeUser!!,
        message.activeUser!!.baseUrl!!,
        message.token!!,
        cloudId,
        darkTheme,
        requestBigSize,
        ignoreCache
    )
}

@Suppress("LongParameterList")
fun ImageView.loadFederatedUserAvatar(
    user: User,
    baseUrl: String,
    token: String,
    cloudId: String,
    darkTheme: Int,
    requestBigSize: Boolean = true,
    ignoreCache: Boolean
): io.reactivex.disposables.Disposable {
    val imageRequestUri = ApiUtils.getUrlForFederatedAvatar(
        baseUrl,
        token,
        cloudId,
        darkTheme,
        requestBigSize
    )
    Log.d(TAG, "federated avatar URL: $imageRequestUri")

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
                    ApiUtils.getCredentials(user.username, user.token)!!
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


        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
        layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        requestBuilder.placeholder(LayerDrawable(layers))

    if (url.startsWith(user.baseUrl!!) &&
        (url.contains("index.php/core/preview") || url.contains("/avatar/"))
    ) {
        requestBuilder.addHeader(
            "Authorization",
            ApiUtils.getCredentials(user.username, user.token)!!
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
            ApiUtils.getCredentials(user.username, user.token)!!
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

        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
        layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        val layerDrawable = LayerDrawable(layers)
        val data: Any  = layerDrawable

    return DisposableWrapper(
        load(data) {
            transformations(CircleCropTransformation())
        }
    )
}

fun ImageView.loadNoteToSelfAvatar(): io.reactivex.disposables.Disposable {

        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
        layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_note_to_self)
        val layerDrawable = LayerDrawable(layers)
         val data: Any = layerDrawable


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

        val layers = arrayOfNulls<Drawable>(2)
        layers[0] = ColorDrawable(context.getColor(R.color.black))
        layers[1] = TextDrawable(context, ">")
        val layerDrawable = LayerDrawable(layers)
        val data: Any  = layerDrawable

    return DisposableWrapper(
        load(data) {
            transformations(CircleCropTransformation())
        }
    )
}

fun ImageView.loadDefaultGroupCallAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_group) as Any
    return loadUserAvatar(data)
}

fun ImageView.loadDefaultPublicCallAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_link) as Any
    return loadUserAvatar(data)
}

fun ImageView.loadMailAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_mail) as Any
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
