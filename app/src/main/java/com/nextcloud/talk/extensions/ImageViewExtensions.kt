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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.annotation.ExperimentalCoilApi
import coil.dispose
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
import com.nextcloud.talk.ui.toDrawable
import com.nextcloud.talk.utils.ActorAvatar
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CharacterAvatarUtils
import com.nextcloud.talk.utils.DisplayUtils
import kotlin.math.min

private const val ROUNDING_PIXEL = 16f
private const val TAG = "ImageViewExtensions"

@Deprecated("use other constructor that expects com.nextcloud.talk.models.domain.ConversationModel")
fun ImageView.loadConversationAvatar(
    user: User,
    conversation: Conversation,
    ignoreCache: Boolean,
    viewThemeUtils: ViewThemeUtils?
): io.reactivex.disposables.Disposable =
    loadConversationAvatar(
        user,
        ConversationModel.mapToConversationModel(conversation, user),
        ignoreCache,
        viewThemeUtils
    )

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
        requestBigSize,
        darkMode = DisplayUtils.isDarkModeOn(this.context)
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
fun ImageView.loadAvatarWithUrl(user: User? = null, url: String): io.reactivex.disposables.Disposable =
    loadAvatarInternal(user, url, false, null)

fun ImageView.loadPhoneAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val drawable = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_phone_small)
    return loadUserAvatar(drawable)
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
        (url.contains("index.php/core/preview") || url.contains("/avatar/") || url.contains("remote.php/dav/"))
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
        (url.contains("index.php/core/preview") || url.contains("/avatar/") || url.contains("remote.php/dav/"))
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
): io.reactivex.disposables.Disposable =
    if (url.contains("/avatar/")) {
        loadAvatarInternal(user, url, false, null)
    } else {
        loadImage(url, user, placeholder)
    }

fun ImageView.loadUserAvatar(any: Any?): io.reactivex.disposables.Disposable =
    DisposableWrapper(
        load(any) {
            transformations(CircleCropTransformation())
        }
    )

fun ImageView.loadSystemAvatar(): io.reactivex.disposables.Disposable {
    val layers = arrayOfNulls<Drawable>(2)
    layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
    layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
    val layerDrawable = LayerDrawable(layers)
    val data: Any = layerDrawable

    return DisposableWrapper(
        load(data) {
            transformations(CircleCropTransformation())
        }
    )
}

fun ImageView.loadNoteToSelfAvatar() {
    val layers = arrayOfNulls<Drawable>(2)
    layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
    layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_note_to_self)
    val layerDrawable = LayerDrawable(layers)
    setImageDrawable(CircularDrawable(layerDrawable))
}

fun ImageView.loadDefaultGroupCallAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_group_small) as Any
    return loadUserAvatar(data)
}

fun ImageView.loadTeamAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.ic_avatar_team_small) as Any
    return loadUserAvatar(data)
}

fun ImageView.loadDefaultAvatar(viewThemeUtils: ViewThemeUtils): io.reactivex.disposables.Disposable {
    val data: Any = viewThemeUtils.talk.themePlaceholderAvatar(this, R.drawable.account_circle_96dp) as Any
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

/**
 * Client-side avatar for guest and email actors, which have no avatar on the server: a guest who
 * gave us their name gets the first character of it drawn on a circle, an unnamed one the generic
 * person icon. Nothing is requested from the server for either, matching the web client.
 *
 * Any avatar request still in flight for the view is cancelled first, so a recycled view cannot be
 * overwritten by its predecessor's avatar.
 */
fun ImageView.loadGuestAvatar(displayName: String?, viewThemeUtils: ViewThemeUtils) {
    when (val avatar = CharacterAvatarUtils.guestAvatar(displayName, context.getString(R.string.nc_guest))) {
        is ActorAvatar.Character -> {
            dispose()
            setImageDrawable(avatar.toDrawable(context))
        }

        ActorAvatar.AppIcon -> loadSystemAvatar()
        ActorAvatar.PersonIcon -> loadDefaultAvatar(viewThemeUtils)
    }
}

private class DisposableWrapper(private val disposable: coil.request.Disposable) :
    io.reactivex.disposables
        .Disposable {

    override fun dispose() {
        disposable.dispose()
    }

    override fun isDisposed(): Boolean = disposable.isDisposed
}

private class CircularDrawable(private val sourceDrawable: Drawable) : Drawable() {

    private val bitmap: Bitmap = drawableToBitmap(sourceDrawable)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    private val rect = RectF()
    private var radius = 0f

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)

        radius = min(rect.width() / 2.0f, rect.height() / 2.0f)

        val matrix = Matrix()
        val scale: Float
        var dx = 0f
        var dy = 0f

        if (bitmap.width * rect.height() > rect.width() * bitmap.height) {
            // Taller than wide, scale to height and center horizontally
            scale = rect.height() / bitmap.height.toFloat()
            dx = (rect.width() - bitmap.width * scale) / 2.0f
        } else {
            // Wider than tall, scale to width and center vertically
            scale = rect.width() / bitmap.width.toFloat()
            dy = (rect.height() - bitmap.height * scale) / 2.0f
        }

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx.toInt().toFloat() + rect.left, dy.toInt().toFloat() + rect.top)
        paint.shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated(
        "This method is no longer used in graphics optimizations",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sourceDrawable.intrinsicWidth

    override fun getIntrinsicHeight(): Int = sourceDrawable.intrinsicHeight

    companion object {

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable) {
                if (drawable.bitmap != null) {
                    return drawable.bitmap
                }
            }

            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
            return drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
