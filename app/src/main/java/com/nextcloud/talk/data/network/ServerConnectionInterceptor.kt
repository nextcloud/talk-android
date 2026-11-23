/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.network

import androidx.annotation.VisibleForTesting
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports to [ServerConnectionMonitor] whether each request reached its server.
 *
 * Only answers that came from the network count, not those served from the cache. A failure only counts while
 * connecting: once connected, a timeout e.g. of a slow upload says nothing about the server being reachable. WebSocket
 * requests are left out, as they go to the signaling server, which may fail on its own behind the same host.
 *
 * A [ConnectionCheck] only counts as connected with the exact answer of Nextcloud, so e.g. the login page of a captive
 * portal is not taken for the server.
 *
 * Must also be added as network interceptor with [connectionMarker].
 */
@Singleton
class ServerConnectionInterceptor @Inject constructor(private val serverConnectionMonitor: ServerConnectionMonitor) :
    Interceptor {

    @VisibleForTesting
    internal val connectedCalls: MutableSet<Call> = ConcurrentHashMap.newKeySet()

    /** Network interceptor that marks the calls which got a connection to the server. */
    val connectionMarker = Interceptor { chain ->
        // A WebSocket call is not unmarked by intercept(), so marking it would keep it forever.
        if (!isWebSocket(chain.request())) {
            connectedCalls.add(chain.call())
        }
        chain.proceed(chain.request())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isWebSocket(request)) {
            return chain.proceed(request)
        }
        val call = chain.call()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (!call.isCanceled() && isConnectFailure(e, call)) {
                serverConnectionMonitor.report(request.url, ServerConnectionStatus.SERVER_UNREACHABLE)
            }
            throw e
        } finally {
            connectedCalls.remove(call)
        }
        if (response.networkResponse != null) {
            val isConnectionCheck = request.tag(ConnectionCheck::class.java) != null
            serverConnectionMonitor.report(request.url, statusOf(response, isConnectionCheck))
        }
        return response
    }

    private fun isWebSocket(request: Request) = request.header(UPGRADE_HEADER) != null

    private fun isConnectFailure(e: IOException, call: Call) =
        e is UnknownHostException ||
            e is ConnectException ||
            e is NoRouteToHostException ||
            (e is SocketTimeoutException && call !in connectedCalls)

    private fun statusOf(response: Response, isConnectionCheck: Boolean) =
        when {
            isMaintenanceMode(response.code, response.headers) -> ServerConnectionStatus.MAINTENANCE_MODE

            // A proxy in front of the server answers, but can't reach the server itself.
            response.code == HTTP_BAD_GATEWAY || response.code == HTTP_GATEWAY_TIMEOUT ->
                ServerConnectionStatus.SERVER_UNREACHABLE

            isConnectionCheck && response.code != HTTP_NO_CONTENT -> ServerConnectionStatus.SERVER_UNREACHABLE

            else -> ServerConnectionStatus.CONNECTED
        }

    /** Tag of a request to [com.nextcloud.talk.utils.ApiUtils.getUrlForConnectionCheck]. */
    object ConnectionCheck

    companion object {
        private const val UPGRADE_HEADER = "Upgrade"
        private const val HTTP_NO_CONTENT = 204
        private const val HTTP_BAD_GATEWAY = 502
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private const val HTTP_GATEWAY_TIMEOUT = 504
        private const val MAINTENANCE_MODE_HEADER = "X-Nextcloud-Maintenance-Mode"

        /** Whether an answer with [code] and [headers] says that the server is in maintenance mode. */
        fun isMaintenanceMode(code: Int, headers: Headers?): Boolean =
            code == HTTP_SERVICE_UNAVAILABLE && headers?.get(MAINTENANCE_MODE_HEADER) == "1"
    }
}
