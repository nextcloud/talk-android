/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.controllers

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ControllerAccountVerificationBinding
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.generic.Status
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.MaybeObserver
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AccountVerificationController(args: Bundle? = null) :
    NewBaseController(
        R.layout.controller_account_verification,
        args
    ) {
    private val binding: ControllerAccountVerificationBinding by viewBinding(ControllerAccountVerificationBinding::bind)

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var cookieManager: CookieManager

    @Inject
    lateinit var eventBus: EventBus

    private var internalAccountId: Long = -1
    private val disposables: MutableList<Disposable> = ArrayList()
    private var baseUrl: String? = null
    private var username: String? = null
    private var token: String? = null
    private var isAccountImport = false
    private var originalProtocol: String? = null

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        eventBus.unregister(this)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        actionBar?.hide()

        if (
            isAccountImport &&
            !UriUtils.hasHttpProtocollPrefixed(baseUrl!!) ||
            isSameProtocol(baseUrl!!, originalProtocol!!)
        ) {
            determineBaseUrlProtocol(true)
        } else {
            checkEverything()
        }
    }

    private fun isSameProtocol(baseUrl: String, originalProtocol: String): Boolean {
        return !TextUtils.isEmpty(originalProtocol) && !baseUrl.startsWith(originalProtocol)
    }

    private fun checkEverything() {
        val credentials = ApiUtils.getCredentials(username, token)
        cookieManager.cookieStore.removeAll()
        findServerTalkApp(credentials)
    }

    private fun determineBaseUrlProtocol(checkForcedHttps: Boolean) {
        cookieManager.cookieStore.removeAll()
        baseUrl = baseUrl!!.replace("http://", "").replace("https://", "")
        val queryUrl: String = if (checkForcedHttps) {
            "https://" + baseUrl + ApiUtils.getUrlPostfixForStatus()
        } else {
            "http://" + baseUrl + ApiUtils.getUrlPostfixForStatus()
        }
        ncApi.getServerStatus(queryUrl)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Status?> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                override fun onNext(status: Status) {
                    baseUrl = if (checkForcedHttps) {
                        "https://$baseUrl"
                    } else {
                        "http://$baseUrl"
                    }
                    if (isAccountImport) {
                        router.replaceTopController(
                            RouterTransaction.with(
                                WebViewLoginController(
                                    baseUrl,
                                    false,
                                    username,
                                    ""
                                )
                            )
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler())
                        )
                    } else {
                        checkEverything()
                    }
                }

                override fun onError(e: Throwable) {
                    if (checkForcedHttps) {
                        determineBaseUrlProtocol(false)
                    } else {
                        abortVerification()
                    }
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun findServerTalkApp(credentials: String) {
        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<CapabilitiesOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                    val hasTalk =
                        capabilitiesOverall.ocs!!.data!!.capabilities != null &&
                            capabilitiesOverall.ocs!!.data!!.capabilities!!.spreedCapability != null &&
                            capabilitiesOverall.ocs!!.data!!.capabilities!!.spreedCapability!!.features != null &&
                            !capabilitiesOverall.ocs!!.data!!.capabilities!!.spreedCapability!!.features!!.isEmpty()
                    if (hasTalk) {
                        fetchProfile(credentials)
                    } else {
                        if (activity != null && resources != null) {
                            activity!!.runOnUiThread {
                                binding.progressText.setText(
                                    String.format(
                                        resources!!.getString(R.string.nc_nextcloud_talk_app_not_installed),
                                        resources!!.getString(R.string.nc_app_product_name)
                                    )
                                )
                            }
                        }
                        ApplicationWideMessageHolder.getInstance().setMessageType(
                            ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
                        )
                        abortVerification()
                    }
                }

                override fun onError(e: Throwable) {
                    if (activity != null && resources != null) {
                        activity!!.runOnUiThread {
                            binding.progressText.setText(
                                String.format(
                                    resources!!.getString(R.string.nc_nextcloud_talk_app_not_installed),
                                    resources!!.getString(R.string.nc_app_product_name)
                                )
                            )
                        }
                    }
                    ApplicationWideMessageHolder.getInstance().setMessageType(
                        ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
                    )
                    abortVerification()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun storeProfile(displayName: String?, userId: String) {
        userManager.storeProfile(
            username,
            UserManager.UserAttributes(
                id = null,
                serverUrl = baseUrl,
                currentUser = true,
                userId = userId,
                token = token,
                displayName = displayName,
                pushConfigurationState = null,
                capabilities = null,
                certificateAlias = appPreferences!!.temporaryClientCertAlias,
                externalSignalingServer = null
            )
        )
            .subscribeOn(Schedulers.io())
            .subscribe(object : MaybeObserver<User> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                @SuppressLint("SetTextI18n")
                override fun onSuccess(user: User) {
                    internalAccountId = user.id!!
                    if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
                        registerForPush()
                    } else {
                        activity!!.runOnUiThread {
                            binding.progressText.text =
                                """
                                    ${binding.progressText.text}
                                    ${resources!!.getString(R.string.nc_push_disabled)}
                                """.trimIndent()
                        }
                        fetchAndStoreCapabilities()
                    }
                }

                @SuppressLint("SetTextI18n")
                override fun onError(e: Throwable) {
                    binding.progressText.text =
                        """
                            ${binding.progressText.text}
                    """.trimIndent() +
                            resources!!.getString(R.string.nc_display_name_not_stored)
                    abortVerification()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun fetchProfile(credentials: String) {
        ncApi.getUserProfile(
            credentials,
            ApiUtils.getUrlForUserProfile(baseUrl)
        )
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<UserProfileOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                @SuppressLint("SetTextI18n")
                override fun onNext(userProfileOverall: UserProfileOverall) {
                    var displayName: String? = null
                    if (!TextUtils.isEmpty(userProfileOverall.ocs!!.data!!.displayName)) {
                        displayName = userProfileOverall.ocs!!.data!!.displayName
                    } else if (!TextUtils.isEmpty(userProfileOverall.ocs!!.data!!.displayNameAlt)) {
                        displayName = userProfileOverall.ocs!!.data!!.displayNameAlt
                    }
                    if (!TextUtils.isEmpty(displayName)) {
                        storeProfile(displayName, userProfileOverall.ocs!!.data!!.userId!!)
                    } else {
                        if (activity != null) {
                            activity!!.runOnUiThread {
                                binding.progressText.text =
                                    """
                                        ${binding.progressText.text}
                                        ${resources!!.getString(R.string.nc_display_name_not_fetched)}
                                    """.trimIndent()
                            }
                        }
                        abortVerification()
                    }
                }

                @SuppressLint("SetTextI18n")
                override fun onError(e: Throwable) {
                    if (activity != null) {
                        activity!!.runOnUiThread {
                            binding.progressText.text =
                                """
                                    ${binding.progressText.text}
                                    ${resources!!.getString(R.string.nc_display_name_not_fetched)}
                                """.trimIndent()
                        }
                    }
                    abortVerification()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun registerForPush() {
        val data =
            Data.Builder()
                .putString(PushRegistrationWorker.ORIGIN, "AccountVerificationController#registerForPush")
                .build()
        val pushRegistrationWork =
            OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
                .setInputData(data)
                .build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(eventStatus: EventStatus) {
        if (eventStatus.eventType == EventStatus.EventType.PUSH_REGISTRATION) {
            if (internalAccountId == eventStatus.userId && !eventStatus.isAllGood && activity != null) {
                activity!!.runOnUiThread {
                    binding.progressText.text =
                        """
                            ${binding.progressText.text}
                            ${resources!!.getString(R.string.nc_push_disabled)}
                        """.trimIndent()
                }
            }
            fetchAndStoreCapabilities()
        } else if (eventStatus.eventType == EventStatus.EventType.CAPABILITIES_FETCH) {
            if (internalAccountId == eventStatus.userId && !eventStatus.isAllGood) {
                if (activity != null) {
                    activity!!.runOnUiThread {
                        binding.progressText.text =
                            """
                                ${binding.progressText.text}
                                ${resources!!.getString(R.string.nc_capabilities_failed)}
                            """.trimIndent()
                    }
                }
                abortVerification()
            } else if (internalAccountId == eventStatus.userId && eventStatus.isAllGood) {
                fetchAndStoreExternalSignalingSettings()
            }
        } else if (eventStatus.eventType == EventStatus.EventType.SIGNALING_SETTINGS) {
            if (internalAccountId == eventStatus.userId && !eventStatus.isAllGood) {
                if (activity != null) {
                    activity!!.runOnUiThread {
                        binding.progressText.text =
                            """
                                ${binding.progressText.text}
                                ${resources!!.getString(R.string.nc_external_server_failed)}
                            """.trimIndent()
                    }
                }
            }
            proceedWithLogin()
        }
    }

    private fun fetchAndStoreCapabilities() {
        val userData =
            Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, internalAccountId)
                .build()
        val pushNotificationWork =
            OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java)
                .setInputData(userData)
                .build()
        WorkManager.getInstance().enqueue(pushNotificationWork)
    }

    private fun fetchAndStoreExternalSignalingSettings() {
        val userData =
            Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, internalAccountId)
                .build()
        val signalingSettings =
            OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java)
                .setInputData(userData)
                .build()
        WorkManager.getInstance().enqueue(signalingSettings)
    }

    private fun proceedWithLogin() {
        cookieManager.cookieStore.removeAll()
        val userDisabledCount = userManager.disableAllUsersWithoutId(internalAccountId).blockingGet()
        Log.d(TAG, "Disabled $userDisabledCount users that had no id")
        if (activity != null) {
            activity!!.runOnUiThread {
                if (userManager.users.blockingGet().size == 1) {
                    router.setRoot(
                        RouterTransaction.with(ConversationsListController(Bundle()))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                } else {
                    if (isAccountImport) {
                        ApplicationWideMessageHolder.getInstance().messageType =
                            ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED
                    }
                    router.popToRoot()
                }
            }
        }
    }

    private fun dispose() {
        for (i in disposables.indices) {
            if (!disposables[i].isDisposed) {
                disposables[i].dispose()
            }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    public override fun onDestroy() {
        dispose()
        super.onDestroy()
    }

    private fun abortVerification() {
        if (!isAccountImport) {
            if (internalAccountId != -1L) {
                val count = userManager.deleteUser(internalAccountId)
                if (count > 0) {
                        activity?.runOnUiThread { Handler().postDelayed({ router.popToRoot() }, DELAY_IN_MILLIS) }
                }
            } else {
                activity?.runOnUiThread { Handler().postDelayed({ router.popToRoot() }, DELAY_IN_MILLIS) }
            }
        } else {
            ApplicationWideMessageHolder.getInstance().setMessageType(
                ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT
            )
            activity?.runOnUiThread {
                Handler().postDelayed({
                    if (router.hasRootController()) {
                        if (activity != null) {
                            router.popToRoot()
                        }
                    } else {
                        if (userManager.users.blockingGet().isNotEmpty()) {
                            router.setRoot(
                                RouterTransaction.with(ConversationsListController(Bundle()))
                                    .pushChangeHandler(HorizontalChangeHandler())
                                    .popChangeHandler(HorizontalChangeHandler())
                            )
                        } else {
                            router.setRoot(
                                RouterTransaction.with(ServerSelectionController())
                                    .pushChangeHandler(HorizontalChangeHandler())
                                    .popChangeHandler(HorizontalChangeHandler())
                            )
                        }
                    }
                }, DELAY_IN_MILLIS)
            }
        }
    }

    companion object {
        const val TAG = "AccountVerification"
        const val DELAY_IN_MILLIS: Long = 7500
    }

    init {
        sharedApplication!!.componentApplication.inject(this)
        if (args != null) {
            baseUrl = args.getString(KEY_BASE_URL)
            username = args.getString(KEY_USERNAME)
            token = args.getString(KEY_TOKEN)
            if (args.containsKey(KEY_IS_ACCOUNT_IMPORT)) {
                isAccountImport = true
            }
            if (args.containsKey(KEY_ORIGINAL_PROTOCOL)) {
                originalProtocol = args.getString(KEY_ORIGINAL_PROTOCOL)
            }
        }
    }
}
