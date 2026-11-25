/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.loadNoteToSelfAvatar
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.AvatarImageLoader

private const val PREVIEW_ICON_FRACTION = 0.66f

/**
 * Draws a conversation's avatar the same way everywhere it is shown, as resolved by [buildAvatarContent].
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun ConversationAvatarImage(
    model: ConversationModel,
    currentUser: User,
    modifier: Modifier = Modifier,
    requestBigSize: Boolean = false
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val isDark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val credentials = remember(currentUser.id) {
        ApiUtils.getCredentials(currentUser.username, currentUser.token) ?: ""
    }
    val avatarDescription = stringResource(R.string.avatar)
    val avatarContent = buildAvatarContent(
        model = model,
        currentUser = currentUser,
        isDark = isDark,
        requestBigSize = requestBigSize
    )

    when (avatarContent) {
        is AvatarContent.Url -> {
            if (isInPreview) {
                Box(modifier = modifier.background(Color.LightGray))
            } else {
                val imageLoader = remember(avatarContent.versioned) {
                    if (avatarContent.versioned) AvatarImageLoader.get(context) else context.imageLoader
                }
                val aliasKey = remember(currentUser.id, model.token, isDark) {
                    MemoryCache.Key("avatar-${currentUser.id}-${model.token}-" + if (isDark) "dark" else "light")
                }
                val request = remember(avatarContent.url, credentials) {
                    ImageRequest.Builder(context)
                        .data(avatarContent.url)
                        .diskCacheKey(avatarContent.diskCacheKey)
                        .addHeader("Authorization", credentials)
                        .crossfade(true)
                        .listener(
                            onSuccess = { _, result ->
                                (result.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
                                    imageLoader.memoryCache?.set(aliasKey, MemoryCache.Value(bitmap))
                                }
                            }
                        )
                        .build()
                }
                val lastShownAvatar = remember(request) {
                    imageLoader.memoryCache?.get(aliasKey)?.bitmap?.let { BitmapPainter(it.asImageBitmap()) }
                }
                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = avatarDescription,
                    contentScale = ContentScale.Crop,
                    placeholder = lastShownAvatar,
                    error = lastShownAvatar ?: painterResource(R.drawable.account_circle_96dp),
                    modifier = modifier
                )
            }
        }

        is AvatarContent.Res -> {
            if (isInPreview) {
                Box(modifier = modifier.background(Color.LightGray))
            } else {
                AsyncImage(
                    model = avatarContent.resId,
                    contentDescription = avatarDescription,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                )
            }
        }

        AvatarContent.System -> {
            if (isInPreview) {
                Box(
                    modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxSize(PREVIEW_ICON_FRACTION)
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { loadSystemAvatar() }
                    },
                    modifier = modifier.semantics { contentDescription = avatarDescription }
                )
            }
        }

        AvatarContent.NoteToSelf -> {
            if (isInPreview) {
                Box(
                    modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_note_to_self),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxSize(PREVIEW_ICON_FRACTION)
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { loadNoteToSelfAvatar() }
                    },
                    modifier = modifier.semantics { contentDescription = avatarDescription }
                )
            }
        }
    }
}
