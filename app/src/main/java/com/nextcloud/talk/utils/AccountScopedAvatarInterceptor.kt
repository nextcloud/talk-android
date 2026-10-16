/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.ImageResult
import okio.ByteString.Companion.encodeUtf8

/**
 * Keeps conversation avatars of different accounts apart in Coil's memory and disk caches.
 *
 * The conversation-avatar endpoint answers depending on the requesting account: for a one-to-one room it returns
 * the other participant's avatar. Accounts on the same server request the very same URL, so caches keyed by the URL
 * alone would show one account's image to another. This interceptor scopes the cache keys of these requests to the
 * account that sends them, identified by the request's Authorization header.
 */
class AccountScopedAvatarInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()
        val authorization = request.headers[AUTHORIZATION_HEADER]
        if (authorization.isNullOrEmpty() || !isConversationAvatarUrl(url)) {
            return chain.proceed(request)
        }

        val scope = accountScope(authorization)
        val scopedRequest = request.newBuilder()
            .setParameter(ACCOUNT_SCOPE_PARAMETER, scope)
            .diskCacheKey(scopedCacheKey(scope, request.diskCacheKey ?: url))
            .apply {
                request.memoryCacheKey?.let {
                    memoryCacheKey(MemoryCache.Key(scopedCacheKey(scope, it.key), it.extras))
                }
            }
            .build()
        return chain.proceed(scopedRequest)
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val ACCOUNT_SCOPE_PARAMETER = "accountScope"
        private const val SCOPE_LENGTH = 16

        private val CONVERSATION_AVATAR_PATH =
            Regex("/apps/spreed/api/v\\d+/room/[^/?#]+/avatar(/dark)?/?([?#].*)?$")

        fun isConversationAvatarUrl(url: String): Boolean = CONVERSATION_AVATAR_PATH.containsMatchIn(url)

        /**
         * Identifies the account behind [authorization] without keeping the credentials themselves in cache keys.
         */
        fun accountScope(authorization: String): String = authorization.encodeUtf8().sha256().hex().take(SCOPE_LENGTH)

        fun scopedCacheKey(scope: String, key: String): String = "$scope:$key"
    }
}
