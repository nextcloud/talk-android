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
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.UnifiedPushUtils.toPushEndpoint
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Can be used for 4 different things:
 * - if inputData contains [USER_ID] and [ACTIVATION_TOKEN]: activate web push for user (on server) and unregister
 * for proxy push (on server) (received from [com.nextcloud.talk.services.UnifiedPushService])
 * - if inputData contains [USER_ID] and [UNIFIEDPUSH_ENDPOINT]: register for web push (on server)
 * (received from [com.nextcloud.talk.services.UnifiedPushService])
 * - if inputData contains [USE_UNIFIEDPUSH] or if [AppPreferences.getUseUnifiedPush]: get the server VAPID key and
 * register for UnifiedPush to the distributor (on device)
 * - if [AppPreferences.getUseUnifiedPush] is false: unregister UnifiedPush (on device) and unregister for web push
 * (on server), then register for proxy push (on server)
 */
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
        val userId = inputData.getLong(USER_ID, -1)
        val activationToken = inputData.getString(ACTIVATION_TOKEN)
        val pushEndpoint = inputData.getByteArray(UNIFIEDPUSH_ENDPOINT)?.toPushEndpoint()
        val unregisterWebPush = inputData.getBoolean(UNREGISTER_WEBPUSH, false)
        // We always check current status of unifiedpush with defaultUseUnifiedPush here
        // If the current distributor is removed, a notification to inform the user is shown
        val useUnifiedPush = inputData.getBoolean(USE_UNIFIEDPUSH, defaultUseUnifiedPush())
        if (userId != -1L && activationToken != null) {
            Log.d(TAG, "PushRegistrationWorker called via $origin (webPushActivationWork)")
            webPushActivationWork(userId, activationToken)
        } else  if (userId != -1L && pushEndpoint != null) {
            Log.d(TAG, "PushRegistrationWorker called via $origin (webPushWork)")
            webPushWork(userId, pushEndpoint)
        } else if (userId != -1L && unregisterWebPush) {
            Log.d(TAG, "PushRegistrationWorker called via $origin (webPushUnregistrationWork)")
            webPushUnregistrationWork(userId)
        } else if (useUnifiedPush) {
            Log.d(TAG, "PushRegistrationWorker called via $origin (unifiedPushWork)")
            unifiedPushWork()
        } else if (UnifiedPushUtils.hasEmbeddedDistributor(applicationContext)) {
            Log.d(TAG, "PushRegistrationWorker called via $origin (unifiedPushWork#embeddedDistrib)")
            UnifiedPushUtils.useEmbeddedDistributor(applicationContext)
            unifiedPushWork()
        } else {
            Log.d(TAG, "PushRegistrationWorker called via $origin (proxyPushWork)")
            proxyPushWork()
        }
        return Result.success()
    }

    /**
     * Activate web push for user (on server) and unregister for proxy push (on server)
     */
    @SuppressLint("CheckResult")
    private fun webPushActivationWork(id: Long, activationToken: String) {
        val user = userManager.getUserWithId(id).blockingGet()
        activateWebPushForAccount(user, activationToken)
            .flatMap { res ->
                if (res) {
                    unregisterProxyPush(user)
                } else {
                    Log.d(TAG, "Couldn't activate web push for user ${user.userId}")
                    Observable.empty()
                }
            }
            .toList()
            .subscribeOn(Schedulers.io())
            .subscribe { _, e ->
                e?.let {
                    Log.e(TAG, "An error occurred while activating web push, or unregistering proxy push", e)
                }
            }
    }

    /**
     * Register for web push (on server)
     */
    @SuppressLint("CheckResult")
    private fun webPushWork(id: Long, pushEndpoint: PushEndpoint) {
        preferences.unifiedPushLatestEndpoint = System.currentTimeMillis()
        val user = userManager.getUserWithId(id).blockingGet()
        registerWebPushForAccount(user, pushEndpoint)
            .map { (user, res) ->
                if (res) {
                    Log.d(TAG, "User ${user.userId} registered for web push.")
                } else {
                    Log.w(TAG, "Couldn't register ${user.userId} for web push.")
                }
            }.toList()
            .subscribeOn(Schedulers.io())
            .subscribe { _, e ->
                e?.let {
                    Log.e(TAG, "An error occurred while registering for web push", e)
                }
            }
    }

    /**
     * Unregister web push for user
     *
     * Disable UnifiedPush if we don't have a distributor anymore
     */
    @SuppressLint("CheckResult")
    private fun webPushUnregistrationWork(id: Long) {
        userManager.getUserWithId(id).map { user ->
            unregisterWebPushForAccount(user)
                .toList()
                .subscribeOn(Schedulers.io())
                .subscribe { _, e ->
                    e?.let {
                        Log.e(TAG, "An error occurred while unregistering for web push", e)
                    } ?: {
                        Log.d(TAG, "${user.userId} unregistered from web push")
                    }
                }
        }
    }

    /**
     * Get VAPID key (on server) and register UnifiedPush to the distributor (on device)
     */
    @SuppressLint("CheckResult")
    private fun unifiedPushWork() {
        val obs = userManager.users.blockingGet().map { user ->
            registerUnifiedPushForAccount(user)
        }
        Observable.merge(obs)
            .toList()
            .subscribeOn(Schedulers.io())
            .subscribe { _, e ->
                e?.let {
                    Log.e(TAG, "An error occurred while registering for UnifiedPush", e)
                }
            }
    }

    /**
     * Unregister for UnifiedPush (on device) and web push (on server), and
     * register for proxy push (on server)
     */
    @SuppressLint("CheckResult")
    private fun proxyPushWork() {
        val obs = userManager.users.blockingGet().mapNotNull { user ->
            if (user.userId == null || user.baseUrl == null) {
                Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
                return@mapNotNull null
            }
            if (user.hasWebPushCapability) {
                UnifiedPush.unregister(applicationContext, UnifiedPushUtils.instanceFor(user))
                unregisterWebPushForAccount(user)
            } else {
                Observable.empty()
            }
        }
        Observable.merge(obs)
            .toList()
            .subscribeOn(Schedulers.io())
            .subscribe { _, e ->
                e?.let {
                    Log.e(TAG, "An error occurred while unregistering for web push", e)
                }
                // Register proxy push for all account, no matter the result of the web push unregistration
                registerProxyPush()
            }
    }

    private fun defaultUseUnifiedPush(): Boolean = preferences.useUnifiedPush &&
        // If this is the first registration, we have never called [UnifiedPush.register]
        // because it happens after this function
        // => we can't be acked by the distributor yet, [UnifiedPush.getAckDistributor] == null
        // So we check the SavedDistributor instead
        UnifiedPush.getSavedDistributor(applicationContext).also {
            // It is null if the distributor has unregistered all the accounts,
            // or if it has been uninstalled from the system
            if (it == null) {
                Log.d(TAG, "No saved distributor found: disabling UnifiedPush")
                preferences.useUnifiedPush = false
                if (inputData.keyValueMap.any { (key, _) ->
                    RESTART_ON_DISTRIB_UNINSTALL.contains(key)
                }) {
                    enqueueWorkerWithoutData("defaultUseDistributor")
                    enqueueNotifUnifiedPushDisabled()
                }
            }
        } != null

    /**
     * Run the default worker, to use FCM if available
     * when the distributor has been uninstalled
     */
    private fun enqueueWorkerWithoutData(origin: String) {
        // Run the default worker, to use FCM if available
        val data = Data.Builder()
            .putString(ORIGIN, "PushRegistrationWorker#$origin")
            .build()
        val periodicPushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueue(periodicPushRegistrationWork)
    }

    /**
     * Show a notification to the user to inform UnifiedPush has been disabled
     */
    @SuppressLint("CheckResult")
    private fun enqueueNotifUnifiedPushDisabled() {
        val user = userManager.users.blockingGet().first()
        Log.d(TAG, "Sending warning notification with ${user.userId}")
        val notif = hashMapOf(
            "subject" to "UnifiedPush disabled",
            "text" to "You have been unregistered from the distributor. Re-enable in the settings if needed",
            "app" to "internal",
            "type" to "admin_notifications"
        )
        val messageData = Data.Builder()
            .putLong(BundleKeys.KEY_NOTIFICATION_USER_ID, user.id!!)
            .putString(BundleKeys.KEY_NOTIFICATION_CLEARTEXT_SUBJECT, Json.encodeToString(notif))
            .build()
        val notificationWork =
            OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData)
                .build()
        WorkManager.getInstance(applicationContext).enqueue(notificationWork)
    }

    /**
     * Register proxy push for all accounts if the devices support the Play Services
     *
     * This must not be called when UnifiedPush is enabled.
     */
    private fun registerProxyPush() {
        if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
            Log.d(TAG, "Registering proxy push")
            val pushUtils = PushUtils()
            pushUtils.generateRsa2048KeyPair()
            pushUtils.pushRegistrationToServer(ncApi)
        }
    }

    /**
     * Unregister on NC server and NC proxy
     */
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

    /**
     * Register web push with the unifiedpush endpoint, if the server supports web push
     *
     * @return `Observable<Pair<User, Boolean>>`, true if registration succeed, false if server doesn't support web push
     */
    private fun registerWebPushForAccount(
        user: User,
        pushEndpoint: PushEndpoint
    ): Observable<Pair<User, Boolean>> {
        if (user.hasWebPushCapability) {
            Log.d(TAG, "Registering web push for ${user.userId}")
            if (user.userId == null || user.baseUrl == null) {
                Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
                return Observable.empty()
            }
            if (pushEndpoint.pubKeySet == null) {
                // Should not happen with default UnifiedPush KeyManager
                Log.w(TAG, "Null web push keys for user ${user.userId}, aborting.")
                return Observable.empty()
            }
            return ncApi.registerWebPush(
                user.getCredentials(),
                ApiUtils.getUrlForWebPush(user.baseUrl!!),
                pushEndpoint.url,
                pushEndpoint.pubKeySet!!.pubKey,
                pushEndpoint.pubKeySet!!.auth,
                "talk"
            ).map { r ->
                return@map when (r.code()) {
                    200 -> {
                        Log.d(TAG, "Web push registration for ${user.userId} was already registered and activated\n")
                        user to true
                    }
                    201 -> {
                        Log.d(TAG, "New web push registration for ${user.userId}")
                        user to true
                    }
                    else -> {
                        Log.d(TAG, "An error occurred while registering web push for ${user.userId} (status=${r.code()})")
                        user to false
                    }
                }
            }
        } else {
            Log.d(TAG, "${user.userId}'s server doesn't support web push")
            return Observable.just(user to false)
        }
    }

    private fun activateWebPushForAccount(
        user: User,
        activationToken: String
    ) : Observable<Boolean> {
        Log.d(TAG, "Activating web push for ${user.userId}")
        if (user.userId == null || user.baseUrl == null) {
            Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
            return Observable.empty()
        }
        return ncApi.activateWebPush(
            user.getCredentials(),
            ApiUtils.getUrlForWebPushActivation(user.baseUrl!!),
            activationToken
        ).map { r ->
            return@map when (r.code()) {
                200 -> {
                    Log.d(TAG, "Web push registration for ${user.userId} was already activated\n")
                    true
                }
                202 -> {
                    Log.d(TAG, "Web push registration for ${user.userId} activated")
                    true
                }
                else -> {
                    Log.d(TAG, "An error occurred while registering web push for ${user.userId} (status=${r.code()})")
                    false
                }
            }
        }
    }

    private fun unregisterWebPushForAccount(
        user: User
    ) : Observable<Boolean> {
        Log.d(TAG, "Unregistering web push for ${user.userId}")
        if (user.userId == null || user.baseUrl == null) {
            Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
            return Observable.empty()
        }
        return ncApi.unregisterWebPush(
            user.getCredentials(),
            ApiUtils.getUrlForWebPush(user.baseUrl!!)
        ).map { true }

    }

    /**
     * Register UnifiedPush with the server VAPID key if the server supports web push
     *
     * Web push is registered on the nc server when the push endpoint is received
     *
     * @return `Observable<Pair<User, Boolean>>`, true if registration succeed, false if server doesn't support web push
     */
    private fun registerUnifiedPushForAccount(
        user: User
    ): Observable<Pair<User, Boolean>> {
        if (user.hasWebPushCapability) {
            Log.d(TAG, "Registering UnifiedPush for ${user.userId} (${user.id})")
            if (user.userId == null || user.baseUrl == null) {
                Log.w(TAG, "Null userId or baseUrl (userId=${user.userId}, baseUrl=${user.baseUrl}")
                return Observable.empty()
            }
            return ncApi.getVapidKey(user.getCredentials(),ApiUtils.getUrlForVapid(user.baseUrl!!))
                .flatMap { ocs ->
                    ocs.ocs?.data?.vapid?.let { vapid ->
                        UnifiedPush.register(
                            applicationContext,
                            instance = UnifiedPushUtils.instanceFor(user),
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
        const val USER_ID = "user_id"
        const val ACTIVATION_TOKEN = "activation_token"
        const val USE_UNIFIEDPUSH = "use_unifiedpush"
        const val UNIFIEDPUSH_ENDPOINT = "unifiedpush_endpoint"
        const val UNREGISTER_WEBPUSH = "unregister_webpush"

        /**
         * If any of these actions are present when we observe the distributor is uninstalled,
         * we enqueue a worker with default settings, to fallback to FCM if needed
         */
        private val RESTART_ON_DISTRIB_UNINSTALL = listOf(
            ACTIVATION_TOKEN,
            USE_UNIFIEDPUSH,
            UNIFIEDPUSH_ENDPOINT,
            UNREGISTER_WEBPUSH
        )
    }
}
