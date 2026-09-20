/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UserIdUtils
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import retrofit2.Retrofit
import java.net.CookieManager

/**
 * Fetches one user's capabilities from the server and persists them.
 *
 * Shared by [CapabilitiesSyncWorker] (best-effort, all users) and [CapabilitiesFetchWorker]
 * (single user, retried with backoff until it succeeds) so both stay thin wrappers around the
 * same fetch/persist logic while keeping their own, different, retry contracts.
 */
class CapabilitiesFetcher(
    private val userManager: UserManager,
    private val retrofit: Retrofit,
    private val eventBus: EventBus,
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchAndStoreCapabilities(user: User): Boolean {
        val ncApi = buildNcApiCoroutines()
        val url = user.baseUrl?.let { ApiUtils.getUrlForCapabilities(it) } ?: ""

        val capabilitiesOverall = fetchCapabilitiesWithRetries(ncApi, user, url)
        if (capabilitiesOverall == null) {
            postCapabilitiesFetchResult(user, success = false)
            return false
        }

        return updateUser(capabilitiesOverall, user)
    }

    private fun buildNcApiCoroutines(): NcApiCoroutines =
        retrofit
            .newBuilder()
            .client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build())
            .build()
            .create(NcApiCoroutines::class.java)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchCapabilitiesWithRetries(
        ncApi: NcApiCoroutines,
        user: User,
        url: String
    ): CapabilitiesOverall? {
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                return ncApi.getCapabilities(ApiUtils.getCredentials(user.username, user.token), url)
            } catch (e: Exception) {
                Log.w(TAG, "Fetching capabilities for ${user.username} failed (attempt ${attempt + 1})", e)
            }
        }
        return null
    }

    @VisibleForTesting
    @Suppress("TooGenericExceptionCaught")
    fun updateUser(capabilitiesOverall: CapabilitiesOverall, user: User): Boolean {
        val capabilities = capabilitiesOverall.ocs?.data?.capabilities
        if (capabilities == null) {
            Log.w(TAG, "Capabilities response for ${user.username} contained no capabilities data")
            postCapabilitiesFetchResult(user, success = false)
            return false
        }

        user.capabilities = capabilities
        user.serverVersion = capabilitiesOverall.ocs?.data?.serverVersion

        return try {
            val success = userManager.updateOrCreateUser(user).blockingGet() > 0
            if (!success) {
                Log.w(TAG, "Error updating user")
            }
            postCapabilitiesFetchResult(user, success)
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user", e)
            postCapabilitiesFetchResult(user, success = false)
            false
        }
    }

    private fun postCapabilitiesFetchResult(user: User, success: Boolean) {
        eventBus.post(EventStatus(UserIdUtils.getIdForUser(user), EventStatus.EventType.CAPABILITIES_FETCH, success))
    }

    companion object {
        private val TAG = CapabilitiesFetcher::class.java.simpleName
        private const val MAX_FETCH_ATTEMPTS = 3
    }
}
