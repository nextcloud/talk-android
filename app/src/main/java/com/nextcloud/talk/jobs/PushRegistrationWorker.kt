/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.Status
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observable
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.unifiedpush.android.connector.UnifiedPush
import retrofit2.Retrofit
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PushRegistrationWorker(
    context: Context,
    workerParams: WorkerParameters
): Worker(context, workerParams) {
    @Inject
    lateinit var retrofit: Retrofit

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var preferences: AppPreferences

    @Inject
    lateinit var userManager: UserManager

    lateinit var ncApi: NcApi

    private fun inject() {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        ncApi = retrofit
            .newBuilder()
            .client(
                okHttpClient
                    .newBuilder()
                    .cookieJar(CookieJar.NO_COOKIES)
                    .build()
            )
            .build()
            .create(NcApi::class.java)
    }

    @SuppressLint("CheckResult")
    override fun doWork(): Result {
        inject()
        val origin = inputData.getString(ORIGIN)
        val useUnifiedPush = inputData.getBoolean(USE_UNIFIEDPUSH, defaultUseUnifiedPush())
        Log.d(TAG, "PushRegistrationWorker called via $origin (up=$useUnifiedPush)")

        if (useUnifiedPush) {
            registerUnifiedPushForAllAccounts(applicationContext, userManager, ncApi)
                // unregister proxy push for user setting up web push for the first time
                .flatMap { user ->  unregisterProxyPush(user)}
                .toList()
                .subscribe { _, e ->
                    e?.let {
                        Log.d(TAG, "An error occurred while registering for UnifiedPush")
                        e.printStackTrace()
                    }
                }
        } else {
            unregisterUnifiedPushForAllAccounts(applicationContext, userManager, ncApi)
                .toList()
                .subscribe { _,  e ->
                    e?.let {
                        Log.d(TAG, "An error occurred while unregistering from UnifiedPush")
                        e.printStackTrace()
                    } ?: registerProxyPush()
                }
        }
        return Result.success()
    }

    private fun defaultUseUnifiedPush(): Boolean = preferences.useUnifiedPush &&
        // If this is the first registration, we have never called [UnifiedPush.register]
        // because it happens after this function
        // => we can't be acked by the distributor yet, [UnifiedPush.getAckDistributor] == null
        // So we check the SavedDistributor instead
        UnifiedPush.getSavedDistributor(applicationContext).also {
            if (it == null) {
                Log.d(TAG, "No saved distributor found: disabling UnifiedPush")
                preferences.useUnifiedPush = false
            }
        } != null

    /**
     * Register proxy push for all accounts with [User.usesProxyPush], set if
     * the server doesn't support webpush or if UnifiedPush is disabled
     */
    private fun registerProxyPush() {
        if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
            Log.d(TAG, "Registering proxy push")
            val pushUtils = PushUtils()
            pushUtils.generateRsa2048KeyPair()
            pushUtils.pushRegistrationToServer(ncApi)
        }
    }

    private fun unregisterProxyPush(user: User): Observable<Void> {
        return if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
            Log.d(TAG, "Unregistering proxy push for ${user.userId}")
            ncApi.unregisterDeviceForNotificationsWithNextcloud(
                user.getCredentials(),
                ApiUtils.getUrlNextcloudPush(user.baseUrl!!)
            ).flatMap {
                val pushConfig = user.pushConfigurationState!!
                val queryMap = hashMapOf(
                    "deviceIdentifier" to pushConfig.deviceIdentifier,
                    "userPublicKey" to pushConfig.userPublicKey,
                    "deviceIdentifierSignature" to pushConfig.deviceIdentifierSignature
                )
                ncApi.unregisterDeviceForNotificationsWithProxy(ApiUtils.getUrlPushProxy(), queryMap)
            }
        } else {
            Observable.empty()
        }
    }

    fun unregisterUnifiedPushForAllAccounts(
        context: Context,
        userManager: UserManager,
        ncApi: NcApi
    ): Observable<Status> {
        val obs = userManager.users.blockingGet().mapNotNull { user ->
            if (user.userId == null || user.baseUrl == null) {
                Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
                return@mapNotNull null
            }
            UnifiedPush.unregister(context, user.userId!!)
            if (user.usesWebPush) {
                user.usesWebPush = false
                userManager.saveUser(user)
                ncApi.unregisterWebPush(user.getCredentials(), ApiUtils.getUrlForWebPush(user.baseUrl!!))
            } else {
                return@mapNotNull null
            }
        }
        return Observable.merge(obs)
    }

    /**
     * Register UnifiedPush for all accounts with the server VAPID key if the server supports web push
     *
     * Web push is registered on the nc server when the push endpoint is received
     *
     * Proxy push is unregistered for accounts on server with web push support, if a server doesn't support web push, proxy push is re-registered
     *
     * @return Observable<User?> not null if user was using proxy push and now use web push
     */
    fun registerUnifiedPushForAllAccounts(
        context: Context,
        userManager: UserManager,
        ncApi: NcApi
    ): Observable<User> {
        val obs = userManager.users.blockingGet().map { user ->
            registerUnifiedPushForAccount(context, ncApi, user)
        }
        return Observable.merge(obs)
            // We do not update the user push proxy setting on error
            .flatMap { res ->
                val user = res.first
                val wasUsingProxyPush = user.usesProxyPush
                user.usesProxyPush = !res.second
                userManager.saveUser(user)
                Log.d(TAG, "User ${user.userId} updated: wasUsingProxy=$wasUsingProxyPush, now=${user.usesProxyPush}")
                if (wasUsingProxyPush && !user.usesProxyPush) {
                    Observable.just(user)
                } else {
                    Observable.empty()
                }
            }
    }

    /**
     * Register UnifiedPush with the server VAPID key if the server supports web push
     *
     * Web push is registered on the nc server when the push endpoint is received
     *
     * @return `Observable<bool>`, true if registration succeed, false if server doesn't support web push
     */
    private fun registerUnifiedPushForAccount(
        context: Context,
        ncApi: NcApi,
        user: User
    ): Observable<Pair<User, Boolean>> {
        if (user.hasWebPushCapability) {
            Log.d(TAG, "Registering web push for ${user.userId}")
            if (user.userId == null || user.baseUrl == null) {
                Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
                return Observable.empty()
            }
            return ncApi.getVapidKey(user.getCredentials(),ApiUtils.getUrlForVapid(user.baseUrl!!))
                .flatMap { ocs ->
                    ocs.ocs?.data?.vapid?.let { vapid ->
                        UnifiedPush.register(
                            context,
                            instance = user.userId!!,
                            messageForDistributor = user.userId,
                            vapid = vapid
                        )
                        Observable.just(user to true)
                    } ?: let {
                        Log.d(TAG, "No VAPID key found")
                        Observable.just(user to false)
                    }
                }
        } else {
            Log.d(TAG, "${user.userId}'s server doesn't support web push")
            return Observable.just(user to false)
        }
    }

    companion object {
        const val TAG = "PushRegistrationWorker"
        const val ORIGIN = "origin"
        const val USE_UNIFIEDPUSH = "use_unifiedpush"
    }
}
