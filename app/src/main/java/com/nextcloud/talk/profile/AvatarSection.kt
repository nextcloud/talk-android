/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun AvatarImage(state: ProfileUiState, avatarSize: Dp) {
    val context = LocalContext.current
    val user = state.currentUser
    val isDark = isSystemInDarkTheme()
    val url = ApiUtils.getUrlForAvatar(user?.baseUrl, user?.userId, requestBigSize = true, darkMode = isDark)
    val cachePolicy = if (state.avatarRefreshKey > 0) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED
    val model = remember(url, state.avatarRefreshKey) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(cachePolicy)
            .diskCachePolicy(cachePolicy)
            .crossfade(true)
            .build()
    }
    val coroutineScope = rememberCoroutineScope()
    AvatarCacheEffect(state, isDark)
    AsyncImage(
        model = model,
        contentDescription = stringResource(R.string.avatar),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.account_circle_96dp),
        error = painterResource(R.drawable.account_circle_96dp),
        onSuccess = { successState ->
            if (state.avatarRefreshKey > 0 && !state.avatarIsDeleted) {
                val otherUrl = ApiUtils.getUrlForAvatar(
                    user?.baseUrl,
                    user?.userId,
                    requestBigSize = true,
                    darkMode = !isDark
                )
                copyAvatarToOtherThemeCache(successState, context, otherUrl, url, coroutineScope)
            }
        },
        modifier = Modifier
            .size(avatarSize)
            .clip(CircleShape)
    )
}

/**
 * Side-effect composable that evicts stale avatar cache entries and, for the delete case,
 * prefetches the other theme's server-generated avatar whenever [ProfileUiState.avatarRefreshKey]
 * changes.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun AvatarCacheEffect(state: ProfileUiState, isDark: Boolean) {
    val context = LocalContext.current
    val user = state.currentUser
    LaunchedEffect(state.avatarRefreshKey) {
        if (state.avatarRefreshKey > 0) {
            val imageLoader = context.imageLoader
            val urlLight =
                ApiUtils.getUrlForAvatar(user?.baseUrl, user?.userId, requestBigSize = true, darkMode = false)
            val urlDark = ApiUtils.getUrlForAvatar(user?.baseUrl, user?.userId, requestBigSize = true, darkMode = true)
            // Evict both theme variants so no stale image survives in either cache layer.
            listOf(urlLight, urlDark).forEach { variantUrl ->
                imageLoader.memoryCache?.let { cache ->
                    cache.keys.filter { it.key == variantUrl }.forEach { cache.remove(it) }
                }
                imageLoader.diskCache?.remove(variantUrl)
            }
            // Delete: server returns different theme-aware avatars per URL, so the other theme
            // must be fetched independently via a dedicated network request.
            if (state.avatarIsDeleted) {
                val otherUrl = if (isDark) urlLight else urlDark
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(otherUrl)
                        .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                        .diskCachePolicy(CachePolicy.WRITE_ONLY)
                        .build()
                )
            }
            // Upload/picker/camera: the other theme's cache is populated via onSuccess once the
            // current theme's image has been fetched, avoiding a redundant network request.
        }
    }
}

/**
 * Copies the freshly loaded avatar into the opposite theme's cache slots (memory + disk)
 * preventing the need for a second network request.
 */
@OptIn(ExperimentalCoilApi::class)
private fun copyAvatarToOtherThemeCache(
    successState: AsyncImagePainter.State.Success,
    context: Context,
    otherUrl: String,
    url: String,
    coroutineScope: CoroutineScope
) {
    val imageLoader = context.imageLoader
    // Copy memory-cache entry, preserving key extras (e.g. resolved image size).
    val currentMemKey = successState.result.memoryCacheKey
    val memValue = currentMemKey?.let { imageLoader.memoryCache?.get(it) }
    if (currentMemKey != null && memValue != null) {
        imageLoader.memoryCache?.set(MemoryCache.Key(otherUrl, currentMemKey.extras), memValue)
    }
    // Copy disk-cache bytes on a background thread.
    val diskKey = successState.result.diskCacheKey ?: url
    coroutineScope.launch(Dispatchers.IO) {
        val diskCache = imageLoader.diskCache ?: return@launch
        diskCache.openSnapshot(diskKey)?.use { snapshot ->
            diskCache.openEditor(otherUrl)?.let { editor ->
                try {
                    java.io.File(snapshot.data.toString())
                        .copyTo(java.io.File(editor.data.toString()), overwrite = true)
                    java.io.File(snapshot.metadata.toString())
                        .copyTo(java.io.File(editor.metadata.toString()), overwrite = true)
                    editor.commitAndOpenSnapshot()?.close()
                } catch (_: Exception) {
                    editor.abort()
                }
            }
        }
    }
}

@Composable
fun AvatarSection(state: ProfileUiState, callbacks: ProfileCallbacks, modifier: Modifier) {
    Column(modifier = modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarImage(state, 96.dp)
        if (state.showAvatarButtons) {
            AvatarButtonsRow(callbacks = callbacks, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
        if (state.displayName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (state.baseUrl.isNotEmpty()) {
            Text(
                text = state.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AvatarButtonsRow(callbacks: ProfileCallbacks, modifier: Modifier = Modifier) {
    val buttonShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = callbacks.onAvatarUploadClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.upload),
                contentDescription = stringResource(R.string.upload_new_avatar_from_device)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarChooseClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = stringResource(R.string.choose_avatar_from_cloud)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarCameraClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_photo_camera_24),
                contentDescription = stringResource(R.string.set_avatar_from_camera)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarDeleteClick,
            modifier = Modifier.size(40.dp),
            shape = buttonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.trashbin),
                contentDescription = stringResource(R.string.delete_avatar)
            )
        }
    }
}
