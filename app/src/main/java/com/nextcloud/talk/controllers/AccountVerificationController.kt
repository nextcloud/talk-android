/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import butterknife.BindView
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.Status
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListView
import com.nextcloud.talk.newarch.local.dao.UsersDao
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.ObservableSubscribeProxy
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.net.CookieManager

class AccountVerificationController(args: Bundle?) : BaseController(), KoinComponent {

    val ncApi: NcApi by inject()
    val cookieManager: CookieManager by inject()
    val usersRepository: UsersRepository by inject()
    val usersDao: UsersDao by inject()

    @JvmField @BindView(R.id.progress_text)
    internal var progressText: TextView? = null

    private var internalAccountId: Long = -1

    private var baseUrl: String? = null
    private var username: String? = null
    private var token: String? = null
    private var isAccountImport: Boolean = false
    private var originalProtocol: String? = null

    init {
        if (args != null) {
            baseUrl = args.getString(BundleKeys.KEY_BASE_URL)
            username = args.getString(BundleKeys.KEY_USERNAME)
            token = args.getString(BundleKeys.KEY_TOKEN)
            if (args.containsKey(BundleKeys.KEY_IS_ACCOUNT_IMPORT)) {
                isAccountImport = true
            }
            if (args.containsKey(BundleKeys.KEY_ORIGINAL_PROTOCOL)) {
                originalProtocol = args.getString(BundleKeys.KEY_ORIGINAL_PROTOCOL)
            }
        }
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_account_verification, container, false)
    }

    override fun onDetach(view: View) {
        eventBus.unregister(this)
        super.onDetach(view)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        if (actionBar != null) {
            actionBar!!.hide()
        }

        if (isAccountImport && !baseUrl!!.startsWith("http://") && !baseUrl!!.startsWith("https://") || !TextUtils
                        .isEmpty(originalProtocol) && !baseUrl!!.startsWith(originalProtocol!!)) {
            determineBaseUrlProtocol(true)
        } else {
            checkEverything()
        }
    }

    private fun checkEverything() {
        val credentials = ApiUtils.getCredentials(username, token)
        cookieManager.cookieStore.removeAll()

        findServerTalkApp(credentials)
    }

    private fun determineBaseUrlProtocol(checkForcedHttps: Boolean) {
        cookieManager.cookieStore.removeAll()

        val queryUrl: String

        baseUrl = baseUrl!!.replace("http://", "").replace("https://", "")

        if (checkForcedHttps) {
            queryUrl = "https://" + baseUrl + ApiUtils.getUrlPostfixForStatus()
        } else {
            queryUrl = "http://" + baseUrl + ApiUtils.getUrlPostfixForStatus()
        }

        ncApi.getServerStatus(queryUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .`as`<ObservableSubscribeProxy<Status>>(AutoDispose.autoDisposable(scopeProvider))
                .subscribe(object : Observer<Status> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(status: Status) {
                        if (checkForcedHttps) {
                            baseUrl = "https://" + baseUrl!!
                        } else {
                            baseUrl = "http://" + baseUrl!!
                        }

                        if (isAccountImport) {
                            router.replaceTopController(
                                    RouterTransaction.with(WebViewLoginController(baseUrl,
                                            false, username, ""))
                                            .pushChangeHandler(HorizontalChangeHandler())
                                            .popChangeHandler(HorizontalChangeHandler()))
                        } else {
                            checkEverything()
                        }
                    }

                    override fun onError(e: Throwable) {
                        if (checkForcedHttps) {
                            determineBaseUrlProtocol(false)
                        } else {
                            GlobalScope.launch {
                                abortVerification()

                            }
                        }
                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun findServerTalkApp(credentials: String?) {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(baseUrl))
                .subscribeOn(Schedulers.io())
                .`as`<ObservableSubscribeProxy<RoomsOverall>>(AutoDispose.autoDisposable(scopeProvider))
                .subscribe(object : Observer<RoomsOverall> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(roomsOverall: RoomsOverall) {
                        fetchProfile(credentials)
                    }

                    override fun onError(e: Throwable) {
                        if (activity != null && resources != null) {
                            activity!!.runOnUiThread {
                                progressText!!.text = String.format(resources!!.getString(
                                        R.string.nc_nextcloud_talk_app_not_installed),
                                        resources!!.getString(R.string.nc_app_name))
                            }
                        }

                        ApplicationWideMessageHolder.getInstance().setMessageType(
                                ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK)

                        GlobalScope.launch {
                            abortVerification()
                        }
                    }

                    override fun onComplete() {

                    }
                })
    }

    private suspend fun storeProfile(displayName: String?, userId: String) {
        var user = usersRepository.getUserWithUsernameAndServer(username!!, baseUrl!!)
        if (user == null) {
            user = UserNgEntity(null, userId, username!!, baseUrl!!, token, displayName)
            internalAccountId = usersDao.saveUser(user)
        } else {
            user.displayName = displayName
            usersRepository.updateUser(user)
            internalAccountId = user.id!!
        }

        if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
            registerForPush()
        } else {
            activity!!.runOnUiThread {
                progressText!!.text = progressText!!.text.toString() + "\n" +
                        resources!!.getString(R.string.nc_push_disabled)
            }
            fetchAndStoreCapabilities()
        }
    }

    private fun fetchProfile(credentials: String?) {
        ncApi.getUserProfile(credentials,
                ApiUtils.getUrlForUserProfile(baseUrl))
                .subscribeOn(Schedulers.io())
                .`as`<ObservableSubscribeProxy<UserProfileOverall>>(AutoDispose.autoDisposable(scopeProvider))
                .subscribe(object : Observer<UserProfileOverall> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(userProfileOverall: UserProfileOverall) {
                        var displayName: String? = null
                        if (!TextUtils.isEmpty(userProfileOverall.ocs.data.displayName)) {
                            displayName = userProfileOverall.ocs.data.displayName
                        } else if (!TextUtils.isEmpty(userProfileOverall.ocs.data.displayNameAlt)) {
                            displayName = userProfileOverall.ocs.data.displayNameAlt
                        }

                        if (!TextUtils.isEmpty(displayName)) {
                            GlobalScope.launch {
                                storeProfile(displayName, userProfileOverall.ocs.data.userId)
                            }
                        } else {
                            if (activity != null) {
                                activity!!.runOnUiThread {
                                    progressText!!.text = progressText!!.text.toString() + "\n" +
                                            resources!!.getString(R.string.nc_display_name_not_fetched)
                                }
                            }
                            GlobalScope.launch {
                                abortVerification()
                            }
                        }
                    }

                    override fun onError(e: Throwable) {
                        if (activity != null) {
                            activity!!.runOnUiThread {
                                progressText!!.text = progressText!!.text.toString() + "\n" +
                                        resources!!.getString(R.string.nc_display_name_not_fetched)
                            }
                        }
                        GlobalScope.launch {
                            abortVerification()
                        }
                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun registerForPush() {
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(eventStatus: EventStatus) {
        if (EventStatus.EventType.PUSH_REGISTRATION == eventStatus.eventType) {
            if (internalAccountId == eventStatus.userId
                    && !eventStatus.allGood
                    && activity != null) {
                activity!!.runOnUiThread {
                    progressText!!.text = progressText!!.text.toString() + "\n" +
                            resources!!.getString(R.string.nc_push_disabled)
                }
            }
            fetchAndStoreCapabilities()
        } else if (EventStatus.EventType.CAPABILITIES_FETCH == eventStatus.eventType) {
            if (internalAccountId == eventStatus.userId && !eventStatus.allGood) {
                if (activity != null) {
                    activity!!.runOnUiThread {
                        progressText!!.text = progressText!!.text.toString() + "\n" +
                                resources!!.getString(R.string.nc_capabilities_failed)
                    }
                }
                GlobalScope.launch {
                    abortVerification()
                }
            } else if (internalAccountId == eventStatus.userId && eventStatus.allGood) {
                fetchAndStoreExternalSignalingSettings()
            }
        } else if (EventStatus.EventType.SIGNALING_SETTINGS == eventStatus.eventType) {
            if (internalAccountId == eventStatus.userId && !eventStatus.allGood) {
                if (activity != null) {
                    activity!!.runOnUiThread {
                        progressText!!.text = progressText!!.text.toString() + "\n" +
                                resources!!.getString(R.string.nc_external_server_failed)
                    }
                }
            }

            GlobalScope.launch {
                proceedWithLogin()
            }
        }
    }

    private fun fetchAndStoreCapabilities() {
        val userData = Data.Builder()
                .putLong(BundleKeys.KEY_INTERNAL_USER_ID, internalAccountId)
                .build()

        val pushNotificationWork = OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java)
                .setInputData(userData)
                .build()
        WorkManager.getInstance().enqueue(pushNotificationWork)
    }

    private fun fetchAndStoreExternalSignalingSettings() {
        val userData = Data.Builder()
                .putLong(BundleKeys.KEY_INTERNAL_USER_ID, internalAccountId)
                .build()

        val signalingSettings = OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java)
                .setInputData(userData)
                .build()
        WorkManager.getInstance().enqueue(signalingSettings)
    }

    private suspend fun proceedWithLogin() {
        cookieManager.cookieStore.removeAll()
        usersRepository.setUserAsActiveWithId(internalAccountId)

        if (activity != null) {
                if (usersRepository.getUsers().count() == 1) {
                    activity!!.runOnUiThread {
                        router.setRoot(RouterTransaction.with(ConversationsListView())
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler()))
                    }
                } else {
                    if (isAccountImport) {
                        ApplicationWideMessageHolder.getInstance().messageType = ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED
                    }
                    activity!!.runOnUiThread {
                        router.popToRoot()
                    }
                }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        if (activity != null) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private suspend fun abortVerification() {

        if (!isAccountImport) {
            if (internalAccountId != -1L) {
                usersRepository.deleteUserWithId(internalAccountId)
                activity!!.runOnUiThread { Handler().postDelayed({ router.popToRoot() }, 7500) }

            } else {
                if (activity != null) {
                    activity!!.runOnUiThread { Handler().postDelayed({ router.popToRoot() }, 7500) }
                }
            }
        } else {
            ApplicationWideMessageHolder.getInstance().messageType =
                    ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT
            if (activity != null) {
                activity!!.runOnUiThread {
                    Handler().postDelayed({
                        if (router.hasRootController()) {
                            if (activity != null) {
                                router.popToRoot()
                            }
                        } else {
                            if (usersRepository.getUsers().count() > 0) {
                                router.setRoot(RouterTransaction.with(ConversationsListView())
                                        .pushChangeHandler(HorizontalChangeHandler())
                                        .popChangeHandler(HorizontalChangeHandler()))
                            } else {
                                router.setRoot(RouterTransaction.with(ServerSelectionController())
                                        .pushChangeHandler(HorizontalChangeHandler())
                                        .popChangeHandler(HorizontalChangeHandler()))
                            }
                        }
                    }, 7500)
                }
            }
        }
    }

    companion object {

        const val TAG = "AccountVerificationController"
    }
}
