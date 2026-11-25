/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.network

import android.util.Log
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ServerConnectionStatus {
    CONNECTED,

    /** The server didn't answer, or the device is offline, so the app doesn't send requests to it. */
    SERVER_UNREACHABLE,
    MAINTENANCE_MODE
}

/**
 * Tracks whether the servers of the accounts can be used, from the outcome of the requests sent to them (see
 * [ServerConnectionInterceptor]). Servers are told apart by scheme, host and port, so all accounts on one server
 * share its status.
 *
 * While [NetworkMonitor] considers the device offline, the server counts as unreachable as well, as the app doesn't
 * send requests then, even if the server could be reached, e.g. on a local network without internet.
 */
@Singleton
class ServerConnectionMonitor @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val okHttpClient: Provider<OkHttpClient>
) {

    private val serverStatuses = MutableStateFlow<Map<String, ServerConnectionStatus>>(emptyMap())

    /**
     * The connection status of the server at [baseUrl]. As long as the flow is collected, the server is checked right
     * away when the connectivity of the device changes, and while it can't be used: right away, as the status may be
     * old (e.g. from a background worker) or come from a single failed request, and then every
     * [RECHECK_INTERVAL_MILLIS], so the status recovers also on screens that send no requests of their own.
     */
    fun statusFlow(baseUrl: String?): Flow<ServerConnectionStatus> {
        val serverKey = baseUrl?.toHttpUrlOrNull()?.let(::serverKeyOf)
        val serverStatus = serverStatuses
            .map { statuses -> serverKey?.let { statuses[it] } ?: ServerConnectionStatus.CONNECTED }
            .distinctUntilChanged()
        val checkedServerStatus = serverStatus.transformLatest { status ->
            emit(status)
            if (baseUrl != null && status != ServerConnectionStatus.CONNECTED) {
                while (true) {
                    check(baseUrl)
                    delay(RECHECK_INTERVAL_MILLIS)
                }
            }
        }
        return channelFlow {
            if (baseUrl != null) {
                launch { networkMonitor.isOnline.drop(1).collectLatest { check(baseUrl) } }
            }
            combine(networkMonitor.isOnline, checkedServerStatus) { isOnline, status ->
                if (isOnline) status else ServerConnectionStatus.SERVER_UNREACHABLE
            }
                .distinctUntilChanged()
                .collect { send(it) }
        }
    }

    fun report(url: HttpUrl, status: ServerConnectionStatus) {
        val serverKey = serverKeyOf(url)
        serverStatuses.update { statuses ->
            if (statuses[serverKey] == status) {
                statuses
            } else {
                Log.d(TAG, "Status of $serverKey changed to $status")
                statuses + (serverKey to status)
            }
        }
    }

    /** Sends a connection check without credentials to the server, whose outcome is reported by the interceptor. */
    private suspend fun check(baseUrl: String) {
        val request = Request.Builder()
            .url(ApiUtils.getUrlForConnectionCheck(baseUrl))
            .cacheControl(CacheControl.FORCE_NETWORK)
            .tag(ServerConnectionInterceptor.ConnectionCheck::class.java, ServerConnectionInterceptor.ConnectionCheck)
            .build()
        try {
            okHttpClient.get().newCall(request).await().close()
        } catch (e: IOException) {
            Log.d(TAG, "Server at $baseUrl is still not reachable", e)
        }
    }

    /** Executes the call, and cancels it if the coroutine is cancelled. */
    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, _, _ -> response.close() }
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
        }

    private fun serverKeyOf(url: HttpUrl) = "${url.scheme}://${url.host}:${url.port}"

    companion object {
        private val TAG = ServerConnectionMonitor::class.java.simpleName
        private val RECHECK_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30)
    }
}
