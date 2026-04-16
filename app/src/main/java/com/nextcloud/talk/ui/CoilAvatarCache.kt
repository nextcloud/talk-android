/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Copies a freshly loaded avatar into the opposite theme's cache slots (memory + disk),
 * preventing a second network request when the system theme switches.
 *
 * Both the light URL (e.g. `.../avatar/alice/64`) and the dark URL
 * (`.../avatar/alice/64/dark`) carry the same image bytes — the server just
 * returns a theme-adapted version via the URL suffix.  By writing the just-
 * fetched bytes under the other-theme key we ensure that a theme toggle hits
 * the cache rather than the network.
 *
 * This only applies for uploading an avatar. When fetching avatars there is no indication
 * if it is server-generated (different for dark and light) or else (same for dark and light).
 * So we only do this cache copy when uploading an avatar, which is always server-generated.
 */
@OptIn(ExperimentalCoilApi::class)
internal fun copyAvatarToOtherThemeCache(
    successState: AsyncImagePainter.State.Success,
    context: Context,
    otherUrl: String,
    url: String,
    coroutineScope: CoroutineScope
) {
    val imageLoader = context.imageLoader
    // Copy the memory-cache entry, preserving key extras (e.g. resolved image size).
    val currentMemKey = successState.result.memoryCacheKey
    val memValue = currentMemKey?.let { imageLoader.memoryCache?.get(it) }
    if (currentMemKey != null && memValue != null) {
        imageLoader.memoryCache?.set(MemoryCache.Key(otherUrl, currentMemKey.extras), memValue)
    }
    // Copy the disk-cache bytes on a background thread.
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
