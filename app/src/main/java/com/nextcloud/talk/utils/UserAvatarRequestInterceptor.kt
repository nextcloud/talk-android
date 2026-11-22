/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import coil.intercept.Interceptor
import coil.request.ImageResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.encodeUtf8

/** Keeps old generated avatars and the other theme out of the avatar cache. */
class UserAvatarRequestInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString().toHttpUrlOrNull()
        if (url == null || !UserAvatarPolicy.appliesTo(url)) return chain.proceed(request)
        val dark = DisplayUtils.isDarkModeOn(request.context)
        val identity = request.headers["Authorization"].orEmpty().encodeUtf8().sha256().hex()
        val policyKey = UserAvatarPolicy.cacheKey(identity, dark)
        val builder = request.newBuilder()
            .tag(UserAvatarAppearance::class.java, UserAvatarAppearance(dark))
            .setParameter("talkPersonAvatar", policyKey, policyKey)
            .diskCacheKey(UserAvatarPolicy.cacheKey("${request.diskCacheKey ?: url}-$identity", dark))
        request.memoryCacheKey?.let { key ->
            builder.memoryCacheKey(key.copy(key = UserAvatarPolicy.cacheKey("${key.key}-$identity", dark)))
        }
        return chain.proceed(builder.build())
    }
}
