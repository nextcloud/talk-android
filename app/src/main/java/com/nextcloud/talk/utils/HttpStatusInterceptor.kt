/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import com.nextcloud.talk.events.ServerStatus
import com.nextcloud.talk.events.ServerStatusEvent
import com.nextcloud.talk.users.UserManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ConcurrentHashMap

class HttpStatusInterceptor(private val userManager: UserManager) : Interceptor {

    private val lastKnownStatus = ConcurrentHashMap<Long, ServerStatus>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val accountId = resolveAccountId(request) ?: return response
        val newStatus = statusFor(response)
        val previousStatus = lastKnownStatus.put(accountId, newStatus) ?: ServerStatus.OK

        if (newStatus != previousStatus) {
            EventBus.getDefault().post(ServerStatusEvent(accountId, newStatus))
        }

        return response
    }

    private fun statusFor(response: Response): ServerStatus =
        when (response.code) {
            HTTP_UNAUTHORIZED -> ServerStatus.UNAUTHORIZED
            HTTP_UPGRADE_REQUIRED -> ServerStatus.CLIENT_UPDATE_REQUIRED
            HTTP_SERVICE_UNAVAILABLE ->
                if (response.header(MAINTENANCE_MODE_HEADER) == "1") {
                    ServerStatus.MAINTENANCE_MODE
                } else {
                    ServerStatus.OK
                }
            else -> ServerStatus.OK
        }

    private fun resolveAccountId(request: Request): Long? {
        val authorization = request.header("Authorization")
        val users = userManager.users.blockingGet()
        val user = users.firstOrNull { ApiUtils.getCredentials(it.username, it.token) == authorization }
            ?: users.firstOrNull { it.baseUrl != null && request.url.toString().startsWith(it.baseUrl!!) }
        return user?.id
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_UPGRADE_REQUIRED = 426
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private const val MAINTENANCE_MODE_HEADER = "X-Nextcloud-Maintenance-Mode"
    }
}
