/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityAccountVerificationBinding
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.jobs.WebsocketConnectionsWorker
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
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PASSWORD
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.MaybeObserver
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AccountVerificationActivity : BaseActivity() {

    private lateinit var binding: ActivityAccountVerificationBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var cookieManager: CookieManager

    private var internalAccountId: Long = -1
    private val disposables: MutableList<Disposable> = ArrayList()
    private var baseUrl: String? = null
    private var username: String? = null
    private var token: String? = null
    private var isAccountImport = false
    private var originalProtocol: String? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = ActivityAccountVerificationBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        actionBar?.hide()
        setupSystemColors()

        handleIntent()
    }

    private fun handleIntent() {
        val extras = intent.extras!!
        baseUrl = extras.getString(KEY_BASE_URL)
        username = extras.getString(KEY_USERNAME)
        token = extras.getString(KEY_TOKEN)
        if (extras.containsKey(KEY_IS_ACCOUNT_IMPORT)) {
            isAccountImport = true
        }
        if (extras.containsKey(KEY_ORIGINAL_PROTOCOL)) {
            originalProtocol = extras.getString(KEY_ORIGINAL_PROTOCOL)
        }
    }

    override fun onResume() {
        super.onResume()

        if (
            isAccountImport &&
            !UriUtils.hasHttpProtocolPrefixed(baseUrl!!) ||
            isNotSameProtocol(baseUrl!!, originalProtocol)
        ) {
            determineBaseUrlProtocol(true)
        } else {
            findServerTalkApp()
        }
    }

    private fun isNotSameProtocol(baseUrl: String, originalProtocol: String?): Boolean {
        if (originalProtocol == null) {
            return true
        }
        return !TextUtils.isEmpty(originalProtocol) && !baseUrl.startsWith(originalProtocol)
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
                        val bundle = Bundle()
                        bundle.putString(KEY_BASE_URL, baseUrl)
                        bundle.putString(KEY_USERNAME, username)
                        bundle.putString(KEY_PASSWORD, "")

                        val intent = Intent(context, WebViewLoginActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    } else {
                        findServerTalkApp()
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

    private fun findServerTalkApp() {
        val credentials = ApiUtils.getCredentials(username, token)
        cookieManager.cookieStore.removeAll()

        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl!!))
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
                        fetchProfile(credentials!!, capabilitiesOverall)
                    } else {
                        if (resources != null) {
                            runOnUiThread {
                                binding.progressText.text = String.format(
                                    resources!!.getString(R.string.nc_nextcloud_talk_app_not_installed),
                                    resources!!.getString(R.string.nc_app_product_name)
                                )
                            }
                        }
                        ApplicationWideMessageHolder.getInstance().messageType =
                            ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
                        abortVerification()
                    }
                }

                override fun onError(e: Throwable) {
                    if (resources != null) {
                        runOnUiThread {
                            binding.progressText.text = String.format(
                                resources!!.getString(R.string.nc_nextcloud_talk_app_not_installed),
                                resources!!.getString(R.string.nc_app_product_name)
                            )
                        }
                    }
                    ApplicationWideMessageHolder.getInstance().messageType =
                        ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
                    abortVerification()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun storeProfile(displayName: String?, userId: String, capabilitiesOverall: CapabilitiesOverall) {
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
                capabilities = LoganSquare.serialize(capabilitiesOverall.ocs!!.data!!.capabilities),
                serverVersion = LoganSquare.serialize(capabilitiesOverall.ocs!!.data!!.serverVersion),
                certificateAlias = appPreferences.temporaryClientCertAlias,
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
                        ClosedInterfaceImpl().setUpPushTokenRegistration()
                    } else {
                        Log.w(TAG, "Skipping push registration.")
                        runOnUiThread {
                            binding.progressText.text =
                                """ ${binding.progressText.text}
                                    ${resources!!.getString(R.string.nc_push_disabled)}
                                """.trimIndent()
                        }
                        fetchAndStoreCapabilities()
                    }
                }

                @SuppressLint("SetTextI18n")
                override fun onError(e: Throwable) {
                    binding.progressText.text = """ ${binding.progressText.text}""".trimIndent() +
                        resources!!.getString(R.string.nc_display_name_not_stored)
                    abortVerification()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun fetchProfile(credentials: String, capabilitiesOverall: CapabilitiesOverall) {
        ncApi.getUserProfile(
            credentials,
            ApiUtils.getUrlForUserProfile(baseUrl!!)
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
                        storeProfile(
                            displayName,
                            userProfileOverall.ocs!!.data!!.userId!!,
                            capabilitiesOverall
                        )
                    } else {
                        runOnUiThread {
                            binding.progressText.text =
                                """
                                    ${binding.progressText.text}
                                    ${resources!!.getString(R.string.nc_display_name_not_fetched)}
                                """.trimIndent()
                        }
                        abortVerification()
                    }
                }

                @SuppressLint("SetTextI18n")
                override fun onError(e: Throwable) {
                    runOnUiThread {
                        binding.progressText.text =
                            """
                                ${binding.progressText.text}
                                ${resources!!.getString(R.string.nc_display_name_not_fetched)}
                            """.trimIndent()
                    }
                    abortVerification()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(eventStatus: EventStatus) {
        Log.d(TAG, "caught EventStatus of type " + eventStatus.eventType.toString())
        if (eventStatus.eventType == EventStatus.EventType.PUSH_REGISTRATION) {
            if (internalAccountId == eventStatus.userId && !eventStatus.isAllGood) {
                runOnUiThread {
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
                runOnUiThread {
                    binding.progressText.text =
                        """
                            ${binding.progressText.text}
                            ${resources!!.getString(R.string.nc_capabilities_failed)}
                        """.trimIndent()
                }
                abortVerification()
            } else if (internalAccountId == eventStatus.userId && eventStatus.isAllGood) {
                fetchAndStoreExternalSignalingSettings()
            }
        } else if (eventStatus.eventType == EventStatus.EventType.SIGNALING_SETTINGS) {
            if (internalAccountId == eventStatus.userId && !eventStatus.isAllGood) {
                runOnUiThread {
                    binding.progressText.text =
                        """
                            ${binding.progressText.text}
                            ${resources!!.getString(R.string.nc_external_server_failed)}
                        """.trimIndent()
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
        val capabilitiesWork =
            OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java)
                .setInputData(userData)
                .build()
        WorkManager.getInstance().enqueue(capabilitiesWork)
    }

    private fun fetchAndStoreExternalSignalingSettings() {
        val userData =
            Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, internalAccountId)
                .build()
        val signalingSettingsWorker = OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java)
            .setInputData(userData)
            .build()
        val websocketConnectionsWorker = OneTimeWorkRequest.Builder(WebsocketConnectionsWorker::class.java).build()

        WorkManager.getInstance(applicationContext!!)
            .beginWith(signalingSettingsWorker)
            .then(websocketConnectionsWorker)
            .enqueue()
    }

    private fun proceedWithLogin() {
        cookieManager.cookieStore.removeAll()

        if (userManager.users.blockingGet().size == 1 ||
            userManager.currentUser.blockingGet().id != internalAccountId
        ) {
            val userToSetAsActive = userManager.getUserWithId(internalAccountId).blockingGet()
            Log.d(TAG, "userToSetAsActive: " + userToSetAsActive.username)

            if (userManager.setUserAsActive(userToSetAsActive).blockingGet()) {
                runOnUiThread {
                    if (userManager.users.blockingGet().size == 1) {
                        val intent = Intent(context, ConversationsListActivity::class.java)
                        startActivity(intent)
                    } else {
                        if (isAccountImport) {
                            ApplicationWideMessageHolder.getInstance().messageType =
                                ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED
                        }
                        val intent = Intent(context, ConversationsListActivity::class.java)
                        startActivity(intent)
                    }
                }
            } else {
                Log.e(TAG, "failed to set active user")
                Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            }
        } else {
            Log.d(TAG, "continuing proceedWithLogin was skipped for this user")
        }
    }

    private fun dispose() {
        for (i in disposables.indices) {
            if (!disposables[i].isDisposed) {
                disposables[i].dispose()
            }
        }
    }

    public override fun onDestroy() {
        dispose()
        super.onDestroy()
    }

    private fun abortVerification() {
        if (isAccountImport) {
            ApplicationWideMessageHolder.getInstance().messageType = ApplicationWideMessageHolder.MessageType
                .FAILED_TO_IMPORT_ACCOUNT
            runOnUiThread {
                Handler().postDelayed({
                    val intent = Intent(this, ServerSelectionActivity::class.java)
                    startActivity(intent)
                }, DELAY_IN_MILLIS)
            }
        } else {
            if (internalAccountId != -1L) {
                runOnUiThread {
                    deleteUserAndStartServerSelection(internalAccountId)
                }
            } else {
                runOnUiThread {
                    Handler().postDelayed({
                        val intent = Intent(this, ServerSelectionActivity::class.java)
                        startActivity(intent)
                    }, DELAY_IN_MILLIS)
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun deleteUserAndStartServerSelection(userId: Long) {
        userManager.scheduleUserForDeletionWithId(userId).blockingGet()
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
            .observeForever { workInfo: WorkInfo ->

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val intent = Intent(this, ServerSelectionActivity::class.java)
                        startActivity(intent)
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "something went wrong when deleting user with id $userId")
                        val intent = Intent(this, ServerSelectionActivity::class.java)
                        startActivity(intent)
                    }

                    else -> {}
                }
            }
    }

    companion object {
        private val TAG = AccountVerificationActivity::class.java.simpleName
        const val DELAY_IN_MILLIS: Long = 7500
    }
}
