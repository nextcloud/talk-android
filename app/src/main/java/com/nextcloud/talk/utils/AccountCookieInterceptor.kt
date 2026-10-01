/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import okhttp3.Interceptor
import okhttp3.Response
import java.net.CookieManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the cookies of requests with credentials separate per account.
 *
 * All accounts share one HTTP client with one cookie store, which keeps cookies per server only. A session cookie of
 * one account would be sent along with the requests of another account on the same server, and the server would
 * treat those requests as the first account. Without any cookies, the server can't keep its session between the
 * requests of an account either, which e.g. joining a call relies on: it finds the room session of the account via
 * the server session.
 *
 * So requests with an Authorization header use a cookie store of their own credentials instead of the shared one.
 * Requests without credentials keep using the shared cookie store.
 *
 * Must be added as a network interceptor, as OkHttp adds the cookies of the shared cookie jar and stores received
 * cookies in it around the network interceptors.
 */
@Singleton
class AccountCookieInterceptor @Inject constructor() : Interceptor {

    // Only in memory, keyed by the credentials of the requests, which are kept in memory anyway.
    private val cookieStores = ConcurrentHashMap<String, CookieManager>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val credentials = request.header(AUTHORIZATION) ?: return chain.proceed(request)

        val cookieStore = cookieStores.getOrPut(credentials) { CookieManager() }
        val uri = request.url.toUri()
        val cookies = cookieStore.get(uri, emptyMap())[COOKIE].orEmpty()

        val requestWithAccountCookies = request.newBuilder()
            .removeHeader(COOKIE)
            .apply { if (cookies.isNotEmpty()) header(COOKIE, cookies.joinToString("; ")) }
            .build()
        val response = chain.proceed(requestWithAccountCookies)

        cookieStore.put(uri, response.headers.toMultimap())
        return response.newBuilder().removeHeader(SET_COOKIE).build()
    }

    /**
     * Drops the cookies of an account, e.g. when it is removed.
     */
    fun removeCookiesOf(credentials: String) {
        cookieStores.remove(credentials)
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val COOKIE = "Cookie"
        const val SET_COOKIE = "Set-Cookie"
    }
}
