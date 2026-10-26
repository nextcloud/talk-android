/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.os.SystemClock
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Stops sending credentials that the server rejected.
 *
 * The server counts every request with rejected credentials as a failed login, and after some of them it throttles
 * and finally refuses all requests from the same IP with 429, also those of other accounts and devices. So once a
 * request got a 401 for its credentials, further requests with them are answered with a 401 here, without sending
 * them. Other credentials, e.g. after the account was reauthorized, are sent as usual.
 *
 * The rejection might be temporary, so one request with the credentials is sent again every
 * [RETRY_INTERVAL_MILLIS]. Once it is not rejected, the credentials are sent as usual again.
 */
class RejectedCredentialsInterceptor @JvmOverloads constructor(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : Interceptor {

    // Only in memory, keyed by the credentials of the requests, which are kept in memory anyway.
    private val lastRejectionTimes = ConcurrentHashMap<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val credentials = request.header(AUTHORIZATION)
        val now = elapsedRealtime()

        return when {
            credentials == null -> chain.proceed(request)

            !mayBeSent(credentials, now) -> {
                Log.d(TAG, "Not sending request with rejected credentials: ${request.url}")
                rejectedResponse(request)
            }

            else -> proceedAndRememberRejection(chain, request, credentials, sentAt = now)
        }
    }

    private fun proceedAndRememberRejection(
        chain: Interceptor.Chain,
        request: Request,
        credentials: String,
        sentAt: Long
    ): Response {
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            // A retry that got no answer says nothing about the credentials, so the next request may try again.
            lastRejectionTimes.computeIfPresent(credentials) { _, lastRejection ->
                if (lastRejection == sentAt) lastRejection - RETRY_INTERVAL_MILLIS else lastRejection
            }
            throw e
        }
        // After a redirect to another host, OkHttp drops the credentials, so that 401 says nothing about them.
        val credentialsWereRejected = response.code == HTTP_UNAUTHORIZED &&
            response.request.header(AUTHORIZATION) == credentials

        // A 429 or a server error, e.g. in maintenance mode, is answered without checking the credentials.
        val credentialsWereChecked = response.code != HTTP_TOO_MANY_REQUESTS &&
            response.code < HTTP_INTERNAL_SERVER_ERROR

        if (credentialsWereRejected) {
            lastRejectionTimes[credentials] = elapsedRealtime()
        } else if (credentialsWereChecked) {
            // Only a request sent after the rejection shows that the credentials are accepted again, e.g. not a long
            // polling request that was already waiting for its answer.
            lastRejectionTimes.computeIfPresent(credentials) { _, lastRejection ->
                lastRejection.takeIf { sentAt < it }
            }
        }
        return response
    }

    /**
     * Whether a request with [credentials] may be sent [now]: they were not rejected, or long enough ago for one
     * request to try them again. Only one request claims that retry, the following ones wait for the next interval.
     */
    private fun mayBeSent(credentials: String, now: Long): Boolean {
        var maySend = true
        lastRejectionTimes.computeIfPresent(credentials) { _, lastRejection ->
            if (now - lastRejection >= RETRY_INTERVAL_MILLIS) {
                now
            } else {
                maySend = false
                lastRejection
            }
        }
        return maySend
    }

    private fun rejectedResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_UNAUTHORIZED)
            .message("Credentials were rejected before, request not sent")
            .body("".toResponseBody())
            .build()

    companion object {
        private const val TAG = "RejectedCredentials"
        private const val AUTHORIZATION = "Authorization"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
        private val RETRY_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
    }
}
