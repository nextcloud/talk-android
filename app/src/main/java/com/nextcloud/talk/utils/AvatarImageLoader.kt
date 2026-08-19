/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import coil.ImageLoader
import coil.imageLoader

/**
 * Image loader for versioned conversation-avatar URLs, which are immutable content: the
 * avatarVersion URL parameter is the invalidation token, and a new version changes the URL and
 * forces the fetch. The server marks avatar responses as cacheable for one day only (private,
 * max-age=86400, immutable); ignoring the cache headers removes that expiry for URLs that cannot
 * change, so cold starts and offline serve avatars straight from the disk cache without a
 * network round-trip.
 *
 * Must only be used for URLs carrying a version parameter. Unversioned avatar URLs - notably a
 * one-to-one room's avatar, which is the peer's user avatar and outside the version scheme -
 * need the default loader's header-driven revalidation to ever pick up changes.
 *
 * Derived from the default loader, so memory and disk caches are shared between both.
 */
object AvatarImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: context.imageLoader.newBuilder()
                .respectCacheHeaders(false)
                .build()
                .also { instance = it }
        }
}
