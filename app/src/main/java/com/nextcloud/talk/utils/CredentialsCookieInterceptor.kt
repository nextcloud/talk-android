/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Keeps server session cookies out of requests that authenticate with their own credentials.
 *
 * All accounts share one HTTP client with one cookie store, which keeps cookies per server only. A session cookie of
 * one account would be sent along with the requests of another account on the same server, and the server would
 * treat those requests as the first account. Requests with an Authorization header therefore neither send nor store
 * cookies, so they are authenticated by their credentials alone.
 *
 * Must be added as a network interceptor, as OkHttp adds the cookies of the cookie jar and stores received cookies
 * around the network interceptors.
 */
class CredentialsCookieInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) == null) {
            return chain.proceed(request)
        }
        val response = chain.proceed(request.newBuilder().removeHeader(COOKIE).build())
        return response.newBuilder().removeHeader(SET_COOKIE).build()
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val COOKIE = "Cookie"
        const val SET_COOKIE = "Set-Cookie"
    }
}
