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
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import com.nextcloud.talk.utils.setExpeditedIfSupported
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.databinding.ActivityAccountVerificationBinding
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesFetchWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.jobs.WebsocketConnectionsWorker
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.ui.dialog.IntroduceUnifiedPushDialog
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PASSWORD
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitSingle
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.net.CookieManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Suppress("TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class AccountVerificationActivity : BaseActivity() {

    private lateinit var binding: ActivityAccountVerificationBinding

    @Inject
    lateinit var ncApiCoroutines: NcApiCoroutines

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var cookieManager: CookieManager

    private var internalAccountId: Long = -1
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
        initSystemBars()

        handleIntent()

        lifecycleScope.launch {
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

    private fun isNotSameProtocol(baseUrl: String, originalProtocol: String?): Boolean {
        if (originalProtocol == null) {
            return true
        }
        return !TextUtils.isEmpty(originalProtocol) && !baseUrl.startsWith(originalProtocol)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun determineBaseUrlProtocol(checkForcedHttps: Boolean) {
        cookieManager.cookieStore.removeAll()
        baseUrl = baseUrl!!.replace("http://", "").replace("https://", "")
        val queryUrl: String = if (checkForcedHttps) {
            "https://" + baseUrl + ApiUtils.getUrlPostfixForStatus()
        } else {
            "http://" + baseUrl + ApiUtils.getUrlPostfixForStatus()
        }

        try {
            ncApiCoroutines.getServerStatus(queryUrl)
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

                val intent = Intent(context, BrowserLoginActivity::class.java)
                intent.putExtras(bundle)
                startActivity(intent)
            } else {
                findServerTalkApp()
            }
        } catch (e: Exception) {
            if (checkForcedHttps) {
                determineBaseUrlProtocol(false)
            } else {
                abortVerification()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun findServerTalkApp() {
        val credentials = ApiUtils.getCredentials(username, token)
        cookieManager.cookieStore.removeAll()

        try {
            val capabilitiesOverall =
                ncApiCoroutines.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl!!))
            val hasTalk =
                capabilitiesOverall.ocs!!.data!!.capabilities != null &&
                    capabilitiesOverall.ocs!!.data!!.capabilities!!.spreedCapability != null &&
                    capabilitiesOverall.ocs!!.data!!.capabilities!!.spreedCapability!!.features != null &&
                    !capabilitiesOverall.ocs!!.data!!.capabilities!!.spreedCapability!!.features!!.isEmpty()
            if (hasTalk) {
                fetchProfile(credentials!!, capabilitiesOverall)
            } else {
                if (resources != null) {
                    binding.progressText.text = String.format(
                        resources!!.getString(R.string.nc_nextcloud_talk_app_not_installed),
                        resources!!.getString(R.string.nc_app_product_name)
                    )
                }
                ApplicationWideMessageHolder.getInstance().messageType =
                    ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
                abortVerification()
            }
        } catch (e: Exception) {
            if (resources != null) {
                binding.progressText.text = String.format(
                    resources!!.getString(R.string.nc_nextcloud_talk_app_not_installed),
                    resources!!.getString(R.string.nc_app_product_name)
                )
            }
            ApplicationWideMessageHolder.getInstance().messageType =
                ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK
            abortVerification()
        }
    }

    @SuppressLint("SetTextI18n")
    @Suppress("TooGenericExceptionCaught")
    private suspend fun storeProfile(displayName: String?, userId: String, capabilitiesOverall: CapabilitiesOverall) {
        try {
            val user = withContext(Dispatchers.IO) {
                userManager.storeProfile(
                    username,
                    UserManager.UserAttributes(
                        id = null,
                        serverUrl = baseUrl,
                        currentUser = false,
                        userId = userId,
                        token = token,
                        displayName = displayName,
                        pushConfigurationState = null,
                        capabilities = LoganSquare.serialize(capabilitiesOverall.ocs!!.data!!.capabilities),
                        serverVersion = LoganSquare.serialize(capabilitiesOverall.ocs!!.data!!.serverVersion),
                        certificateAlias = appPreferences.temporaryClientCertAlias,
                        externalSignalingServer = null
                    )
                ).awaitSingle()
            }
            internalAccountId = user.id!!
            fetchAndStoreCapabilities()
        } catch (e: Exception) {
            binding.progressText.text = """ ${binding.progressText.text}""".trimIndent() +
                resources!!.getString(R.string.nc_display_name_not_stored)
            abortVerification()
        }
    }

    @SuppressLint("SetTextI18n")
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchProfile(credentials: String, capabilitiesOverall: CapabilitiesOverall) {
        try {
            val userProfileOverall =
                ncApiCoroutines.getUserProfile(credentials, ApiUtils.getUrlForUserProfile(baseUrl!!))
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
                binding.progressText.text =
                    """
                        ${binding.progressText.text}
                        ${resources!!.getString(R.string.nc_display_name_not_fetched)}
                    """.trimIndent()
                abortVerification()
            }
        } catch (e: Exception) {
            binding.progressText.text =
                """
                    ${binding.progressText.text}
                    ${resources!!.getString(R.string.nc_display_name_not_fetched)}
                """.trimIndent()
            abortVerification()
        }
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(eventStatus: EventStatus) {
        Log.d(TAG, "caught EventStatus of type " + eventStatus.eventType.toString())
        if (internalAccountId != eventStatus.userId) {
            Log.d(TAG, "Event isn't for us. Aborting.")
            return
        }
        // Verification runs: storeProfile -> CAPABILITIES_FETCH -> push registration -> SIGNALING_SETTINGS
        // -> proceedWithLogin. Only the two steps below are backed by a Worker, so only those report
        // back through the event bus; storeProfile() and push registration call the next step directly.
        lifecycleScope.launch {
            when (eventStatus.eventType) {
                EventStatus.EventType.CAPABILITIES_FETCH -> {
                    // Capabilities were already stored together with the profile in storeProfile(), so
                    // a failed refresh here is reported but must not delete the account that was just
                    // created - it does not mean the account is broken, only that this redundant
                    // refresh could not complete.
                    if (!eventStatus.isAllGood) {
                        binding.progressText.text =
                            """
                            ${binding.progressText.text}
                            ${resources!!.getString(R.string.nc_capabilities_failed)}
                            """.trimIndent()
                    }
                    setupPushNotifications()
                }
                EventStatus.EventType.SIGNALING_SETTINGS -> {
                    if (!eventStatus.isAllGood) {
                        binding.progressText.text =
                            """
                            ${binding.progressText.text}
                            ${resources!!.getString(R.string.nc_external_server_failed)}
                            """.trimIndent()
                    }
                    proceedWithLogin()
                }
                else -> {}
            }
        }
    }

    private suspend fun setupPushNotifications() {
        // This isn't a first account, and UnifiedPush is enabled.
        if (appPreferences.useUnifiedPush) {
            val hasWebPushCapability = withContext(Dispatchers.IO) {
                userManager.getUserWithId(internalAccountId).awaitSingle()
            }.hasWebPushCapability
            if (hasWebPushCapability) {
                UnifiedPushUtils.registerWithCurrentDistributor(context)
                onPushRegistrationFinished(success = true)
                return
            } else {
                Log.w(TAG, "Warning: disabling UnifiedPush, user server doesn't support web push.")
                appPreferences.useUnifiedPush = false
            }
        }

        // - By default, use the Play Services if available
        // - If this is a first user, and we have an External UnifiedPush distributor,
        //    and the server supports it: we use it
        // - Else if there is an embedded distributor (so this is a generic flavor, and the
        //    Play services are installed) => we use it for all accounts that support web push
        // - Else we skip push registrations
        if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
            ClosedInterfaceImpl().setUpPushTokenRegistration()
            onPushRegistrationFinished(success = true)
        } else if (withContext(Dispatchers.IO) { userManager.users.await() }.size == 1 &&
            UnifiedPushUtils.getExternalDistributors(context).isNotEmpty() &&
            withContext(Dispatchers.IO) {
                userManager.getUserWithId(internalAccountId).awaitSingle()
            }.hasWebPushCapability
        ) {
            useUnifiedPushIntroduced()
        } else if (UnifiedPushUtils.hasEmbeddedDistributor(context) &&
            withContext(Dispatchers.IO) { userManager.users.await() }.any { it.hasWebPushCapability }
        ) {
            useEmbeddedUnifiedPush()
        } else {
            Log.w(TAG, "Skipping push registration.")
            onPushRegistrationFinished(success = false)
        }
    }

    /**
     * The next step after push registration, whether it succeeded or not - called directly since
     * nothing here runs as a Worker, so there is no need to round-trip through the event bus.
     */
    @SuppressLint("SetTextI18n")
    private suspend fun onPushRegistrationFinished(success: Boolean) {
        if (!success) {
            binding.progressText.text =
                """
                ${binding.progressText.text}
                ${resources!!.getString(R.string.nc_push_disabled)}
                """.trimIndent()
        }
        fetchAndStoreExternalSignalingSettings()
    }

    /**
     * Show a dialog if the user has to select their distributor
     *
     * Most of the time, nothing will be shown, as most users have
     * a single distributor, or already selected their default one
     */
    private fun useUnifiedPushIntroduced() {
        if (UnifiedPushUtils.usingDefaultDistributorNeedsIntro(context)) {
            dialogForUnifiedPush { res ->
                lifecycleScope.launch {
                    if (res) {
                        useUnifiedPush()
                    } else {
                        fallbackToEmbeddedUnifiedPush()
                    }
                }
            }
        } else {
            useUnifiedPush()
        }
    }

    /**
     * Check if there is an embedded distributor, and use it if present,
     * else, finish push registration with success=false
     */
    private suspend fun fallbackToEmbeddedUnifiedPush() {
        if (UnifiedPushUtils.hasEmbeddedDistributor(context)) {
            useEmbeddedUnifiedPush()
        } else {
            onPushRegistrationFinished(success = false)
        }
    }

    private suspend fun useEmbeddedUnifiedPush() {
        UnifiedPushUtils.useEmbeddedDistributor(context)
        UnifiedPushUtils.registerWithCurrentDistributor(context)
        onPushRegistrationFinished(success = true)
    }

    private fun useUnifiedPush() {
        UnifiedPushUtils.useDefaultDistributor(this) { distrib ->
            lifecycleScope.launch {
                distrib?.let {
                    Log.d(TAG, "UnifiedPush registered with $distrib")
                    appPreferences.useUnifiedPush = true
                    onPushRegistrationFinished(success = true)
                } ?: run {
                    Log.d(TAG, "No UnifiedPush distrib selected")
                    fallbackToEmbeddedUnifiedPush()
                }
            }
        }
    }

    private fun dialogForUnifiedPush(onResponse: (Boolean) -> Unit) {
        binding.genericComposeView.apply {
            setContent {
                IntroduceUnifiedPushDialog { res ->
                    onResponse(res)
                }
            }
        }
    }

    private fun fetchAndStoreCapabilities() {
        val userData =
            Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, internalAccountId)
                .build()
        val capabilitiesWork =
            OneTimeWorkRequest.Builder(CapabilitiesFetchWorker::class.java)
                .setInputData(userData)
                .setExpeditedIfSupported()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance().enqueueUniqueWork(
            CAPABILITIES_WORK_NAME_PREFIX + internalAccountId,
            ExistingWorkPolicy.REPLACE,
            capabilitiesWork
        )
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

    private suspend fun proceedWithLogin() {
        cookieManager.cookieStore.removeAll()

        val userToSetAsActive = withContext(Dispatchers.IO) {
            userManager.getUserWithId(internalAccountId).awaitSingle()
        }
        Log.d(TAG, "userToSetAsActive: " + userToSetAsActive.username)

        if (withContext(Dispatchers.IO) { userManager.setUserAsActive(userToSetAsActive).await() }) {
            if (withContext(Dispatchers.IO) { userManager.users.await() }.size > 1 && isAccountImport) {
                ApplicationWideMessageHolder.getInstance().messageType =
                    ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED
            }
            val intent = Intent(context, ConversationsListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } else {
            Log.e(TAG, "failed to set active user")
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
        }
    }

    private suspend fun abortVerification() {
        if (isAccountImport) {
            ApplicationWideMessageHolder.getInstance().messageType = ApplicationWideMessageHolder.MessageType
                .FAILED_TO_IMPORT_ACCOUNT
            Handler().postDelayed({
                val intent = Intent(this, ServerSelectionActivity::class.java)
                startActivity(intent)
            }, DELAY_IN_MILLIS)
        } else {
            if (internalAccountId != -1L) {
                deleteUserAndStartServerSelection(internalAccountId)
            } else {
                Handler().postDelayed({
                    val intent = Intent(this, ServerSelectionActivity::class.java)
                    startActivity(intent)
                }, DELAY_IN_MILLIS)
            }
        }
    }

    @SuppressLint("CheckResult")
    private suspend fun deleteUserAndStartServerSelection(userId: Long) {
        withContext(Dispatchers.IO) { userManager.scheduleUserForDeletionWithId(userId).await() }
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
            .setExpeditedIfSupported()
            .build()
        WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
            .observeForever { workInfo: WorkInfo? ->

                when (workInfo?.state) {
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
        const val CAPABILITIES_WORK_NAME_PREFIX = "CapabilitiesFetch_"
    }
}
