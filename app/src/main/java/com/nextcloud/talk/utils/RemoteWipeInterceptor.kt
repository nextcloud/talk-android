/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.RemoteWipeEvent
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.RemoteWipeSuccessWorker
import com.nextcloud.talk.models.json.wipe.WipeCheckResponse
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.tls.OkHostnameVerifier
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RemoteWipeInterceptor(
    private val userManager: UserManager,
    private val context: Context,
    private val sslSocketFactory: SSLSocketFactoryCompat,
    private val trustManager: TrustManager
) : Interceptor {

    private val handledUserIds = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    private val wipeCheckClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier(trustManager.getHostnameVerifier(OkHostnameVerifier))
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HTTP_UNAUTHORIZED) return response

        // The request that got the 401. After a redirect, it differs from chain.request(): e.g. OkHttp drops the
        // credentials when redirecting to another host, so that 401 says nothing about the original credentials.
        val rejectedRequest = response.request
        Log.d(TAG, "Received 401 for ${rejectedRequest.url}")

        if (WIPE_PATH in rejectedRequest.url.encodedPath) {
            Log.d(TAG, "401 was from the wipe endpoint itself, ignoring to avoid recursion")
            return response
        }

        val candidate = resolveWipeCandidate(rejectedRequest) ?: return response
        if (!handledUserIds.add(candidate.userId)) {
            Log.d(TAG, "User ${candidate.userId} was already handled, ignoring")
            return response
        }

        val wipeRequestedByServer = isWipeRequestedByServer(candidate)
        performWipe(candidate, wipeRequestedByServer)

        return response
    }

    private fun resolveWipeCandidate(requestUrl: String): WipeCandidate? {
        val user = userManager.users.blockingGet()
            .firstOrNull { it.baseUrl != null && requestUrl.startsWith(it.baseUrl!!) }
        if (user == null) {
            Log.d(TAG, "No known user matches base URL of $requestUrl, ignoring")
            return null
        }

        return if (user != null && token != null && userId != null) {
            WipeCandidate(user, token, userId)
        } else {
            Log.d(TAG, "401 for a request without the credentials of a known account, ignoring: ${request.url}")
            null
        }
    }

    /**
     * The stored account on the server of [request] whose credentials the request carries, if any.
     */
    private fun findUserWithCredentialsOf(request: Request): User? {
        val credentials = request.header(AUTHORIZATION) ?: return null
        val requestUrl = request.url.toString()

        return runBlocking { userManager.getUsers() }.firstOrNull { user ->
            val isOnServerOfRequest = user.baseUrl?.let { UriUtils.isOnServer(requestUrl, it) } == true
            isOnServerOfRequest && ApiUtils.getCredentials(user.username, user.token) == credentials
        }
    }

    private fun isWipeRequestedByServer(candidate: WipeCandidate): Boolean {
        Log.d(
            TAG,
            "Asking server whether it requested a wipe for user ${candidate.userId} at ${candidate.user.baseUrl}"
        )

        val wipeRequestedByServer = try {
            val wipeCheckRequest = Request.Builder()
                .url(ApiUtils.getUrlForRemoteWipeCheck(candidate.user.baseUrl!!))
                .post(FormBody.Builder().add("token", candidate.token).build())
                .build()
            wipeCheckClient.newCall(wipeCheckRequest).execute().use { wipeResponse ->
                val body = wipeResponse.body?.string()
                wipeResponse.isSuccessful &&
                    body != null &&
                    LoganSquare.parse(body, WipeCheckResponse::class.java)?.wipe == true
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to check remote wipe status", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to check remote wipe status", e)
            false
        }

        Log.d(TAG, "Server response for user ${candidate.userId}: wipe requested = $wipeRequestedByServer")
        return wipeRequestedByServer
    }

    @SuppressLint("CheckResult")
    private fun performWipe(candidate: WipeCandidate, wipeRequestedByServer: Boolean) {
        Log.d(TAG, "Scheduling user ${candidate.userId} for deletion")
        userManager.scheduleUserForDeletionWithId(candidate.userId).blockingGet()

        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        var workContinuation = WorkManager.getInstance(context).beginWith(accountRemovalWork)

        if (wipeRequestedByServer) {
            Log.d(TAG, "Server had requested this wipe, will report success after account removal")
            val remoteWipeSuccessWork = OneTimeWorkRequest.Builder(RemoteWipeSuccessWorker::class.java)
                .setInputData(
                    Data.Builder()
                        .putString(RemoteWipeSuccessWorker.KEY_BASE_URL, candidate.user.baseUrl)
                        .putString(RemoteWipeSuccessWorker.KEY_TOKEN, candidate.token)
                        .build()
                )
                .build()
            workContinuation = workContinuation.then(remoteWipeSuccessWork)
        } else {
            Log.d(TAG, "Server did not request this wipe, account is still removed but no success report is sent")
        }
        workContinuation.enqueue()

        EventBus.getDefault().post(RemoteWipeEvent())
    }

    private data class WipeCandidate(val user: User, val token: String, val userId: Long)

    companion object {
        private const val TAG = "RemoteWipeInterceptor"
        private const val HTTP_UNAUTHORIZED = 401
        private const val AUTHORIZATION = "Authorization"
        private const val WIPE_PATH = "wipe"
    }
}
