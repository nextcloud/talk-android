/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.diagnose

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.BrandingUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.PushUtils.Companion.LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY
import com.nextcloud.talk.utils.PushUtils.Companion.LATEST_PUSH_REGISTRATION_AT_SERVER
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.power.PowerManagerUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("TooManyFunctions")
class DiagnoseActivity : BaseActivity() {

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    private var isGooglePlayServicesAvailable: Boolean = false

    sealed class DiagnoseElement {
        data class DiagnoseHeadline(val headline: String) : DiagnoseElement()
        data class DiagnoseEntry(val key: String, val value: String) : DiagnoseElement()
    }

    private val diagnoseData = mutableListOf<DiagnoseElement>()
    private val diagnoseDataState = mutableStateOf(emptyList<DiagnoseElement>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val diagnoseViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[DiagnoseViewModel::class.java]

        val colorScheme = viewThemeUtils.getColorScheme(this)
        isGooglePlayServicesAvailable = ClosedInterfaceImpl().isGooglePlayServicesAvailable

        setContent {
            val backgroundColor = colorResource(id = R.color.bg_default)

            val menuItems = mutableListOf(
                stringResource(R.string.nc_common_copy) to { copyToClipboard(diagnoseData.toMarkdownString()) },
                stringResource(R.string.share) to { shareToOtherApps(diagnoseData.toMarkdownString()) },
                stringResource(R.string.send_email) to { composeEmail(diagnoseData.toMarkdownString()) }
            )

            if (BrandingUtils.isOriginalNextcloudClient(applicationContext)) {
                menuItems.add(
                    stringResource(R.string.create_issue) to { createGithubIssue(diagnoseData.toMarkdownString()) }
                )
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ColoredStatusBar()
                Scaffold(
                    modifier = Modifier
                        .statusBarsPadding(),
                    topBar = {
                        StandardAppBar(
                            title = stringResource(R.string.nc_settings_diagnose_title),
                            menuItems
                        )
                    },
                    content = { paddingValues ->
                        val viewState = diagnoseViewModel.notificationViewState.collectAsState().value

                        Column(
                            Modifier
                                .padding(0.dp, paddingValues.calculateTopPadding(), 0.dp, 0.dp)
                                .background(backgroundColor)
                                .fillMaxSize()
                        ) {
                            DiagnoseContentComposable(
                                diagnoseDataState,
                                isLoading = diagnoseViewModel.isLoading.value,
                                showDialog = diagnoseViewModel.showDialog.value,
                                viewState = viewState,
                                onTestPushClick = { diagnoseViewModel.fetchTestPushResult() },
                                onDismissDialog = { diagnoseViewModel.dismissDialog() },
                                isGooglePlayServicesAvailable = isGooglePlayServicesAvailable
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()

        diagnoseData.clear()
        setupMetaValues()
        setupPhoneValues()
        setupAppValues()
        setupAccountValues()

        diagnoseDataState.value = diagnoseData.toList()
    }

    private fun shareToOtherApps(message: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share))
        startActivity(shareIntent)
    }

    private fun composeEmail(text: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            val appName = context.resources.getString(R.string.nc_app_product_name)

            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_SUBJECT, appName)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun createGithubIssue(text: String) {
        copyToClipboard(text)

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                resources!!.getString(R.string.nc_talk_android_issues_url).toUri()
            )
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(
            resources?.getString(R.string.nc_app_product_name),
            text
        )
        clipboardManager.setPrimaryClip(clipData)

        Toast.makeText(
            context,
            context.resources.getString(R.string.nc_common_copy_success),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun setupMetaValues() {
        addHeadline(context.resources.getString(R.string.nc_diagnose_meta_category_title))
        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_meta_system_report_date),
            value = DisplayUtils.unixTimeToHumanReadable(System.currentTimeMillis())
        )
    }

    private fun setupPhoneValues() {
        addHeadline(context.resources.getString(R.string.nc_diagnose_phone_category_title))

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_device_name_title),
            value = getDeviceName()
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_android_version_title),
            value = Build.VERSION.SDK_INT.toString()
        )

        if (isGooglePlayServicesAvailable) {
            addDiagnosisEntry(
                key = context.resources.getString(R.string.nc_diagnose_gplay_available_title),
                value = context.resources.getString(R.string.nc_diagnose_gplay_available_yes)
            )
        } else {
            addDiagnosisEntry(
                key = context.resources.getString(R.string.nc_diagnose_gplay_available_title),
                value = context.resources.getString(R.string.nc_diagnose_gplay_available_no)
            )
        }
    }

    @SuppressLint("SetTextI18n")
    @Suppress("MagicNumber")
    private fun setupAppValues() {
        addHeadline(context.resources.getString(R.string.nc_diagnose_app_category_title))

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_app_name_title),
            value = context.resources.getString(R.string.nc_app_product_name)
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_app_version_title),
            value = String.format("v" + BuildConfig.VERSION_NAME)
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_flavor),
            value = BuildConfig.FLAVOR
        )

        if (isGooglePlayServicesAvailable) {
            setupAppValuesForGooglePlayServices()
        }

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_app_users_amount),
            value = userManager.users.blockingGet().size.toString()
        )
    }

    @Suppress("Detekt.LongMethod")
    private fun setupAppValuesForGooglePlayServices() {
        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_battery_optimization_title),
            value = if (PowerManagerUtils().isIgnoringBatteryOptimizations()) {
                context.resources.getString(R.string.nc_diagnose_battery_optimization_ignored)
            } else {
                context.resources.getString(R.string.nc_diagnose_battery_optimization_not_ignored)
            }
        )

        // handle notification permission on API level >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addDiagnosisEntry(
                key = context.resources.getString(R.string.nc_diagnose_notification_permission),
                value = if (platformPermissionUtil.isPostNotificationsPermissionGranted()) {
                    context.resources.getString(R.string.nc_settings_notifications_granted)
                } else {
                    context.resources.getString(R.string.nc_settings_notifications_declined)
                }
            )
        }

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_notification_calls_channel_permission),
            value =
            translateBoolean(
                NotificationUtils.isCallsNotificationChannelEnabled(this)
            )
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_notification_messages_channel_permission),
            value =
            translateBoolean(
                NotificationUtils.isMessagesNotificationChannelEnabled(this)
            )
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_firebase_push_token_title),
            value = if (appPreferences.pushToken.isNullOrEmpty()) {
                context.resources.getString(R.string.nc_diagnose_firebase_push_token_missing)
            } else {
                "${appPreferences.pushToken.substring(0, PUSH_TOKEN_PREFIX_END)}..."
            }
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_firebase_push_token_latest_generated),
            value = if (appPreferences.pushTokenLatestGeneration != null &&
                appPreferences.pushTokenLatestGeneration != 0L
            ) {
                DisplayUtils.unixTimeToHumanReadable(
                    appPreferences
                        .pushTokenLatestGeneration
                )
            } else {
                context.resources.getString(R.string.nc_common_unknown)
            }
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_firebase_push_token_latest_fetch),
            value = if (appPreferences.pushTokenLatestFetch != null && appPreferences.pushTokenLatestFetch != 0L) {
                DisplayUtils.unixTimeToHumanReadable(appPreferences.pushTokenLatestFetch)
            } else {
                context.resources.getString(R.string.nc_common_unknown)
            }
        )
    }

    private fun setupAccountValues() {
        val currentUser = currentUserProvider.currentUser.blockingGet()

        addHeadline(context.resources.getString(R.string.nc_diagnose_account_category_title))

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_account_server),
            value =
            currentUser.baseUrl!!
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_account_user_name),
            value =
            currentUser.displayName!!
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_account_user_status_enabled),
            value =
            translateBoolean(
                (currentUser.capabilities?.userStatusCapability?.enabled)
            )
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_account_server_notification_app),
            value =
            translateBoolean(currentUser.capabilities?.notificationsCapability?.features?.isNotEmpty())
        )

        if (isGooglePlayServicesAvailable) {
            setupPushRegistrationDiagnose()
        }

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_server_version),
            value =
            currentUser.serverVersion?.versionString!!
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_server_talk_version),
            value =
            currentUser.capabilities?.spreedCapability?.version!!
        )

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_signaling_mode_title),
            value =
            if (currentUser.externalSignalingServer?.externalSignalingServer?.isNotEmpty() == true) {
                context.resources.getString(R.string.nc_diagnose_signaling_mode_extern)
            } else {
                context.resources.getString(R.string.nc_diagnose_signaling_mode_intern)
            }
        )
    }

    private fun setupPushRegistrationDiagnose() {
        val accountId = UserIdUtils.getIdForUser(currentUserProvider.currentUser.blockingGet())

        val latestPushRegistrationAtServer = arbitraryStorageManager.getStorageSetting(
            accountId,
            LATEST_PUSH_REGISTRATION_AT_SERVER,
            ""
        ).blockingGet()?.value

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_server),
            if (latestPushRegistrationAtServer.isNullOrEmpty()) {
                context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_server_fail)
            } else {
                DisplayUtils.unixTimeToHumanReadable(latestPushRegistrationAtServer.toLong())
            }
        )

        val latestPushRegistrationAtPushProxy = arbitraryStorageManager.getStorageSetting(
            accountId,
            LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY,
            ""
        ).blockingGet()?.value

        addDiagnosisEntry(
            key = context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_push_proxy),
            value = if (latestPushRegistrationAtPushProxy.isNullOrEmpty()) {
                context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_push_proxy_fail)
            } else {
                DisplayUtils.unixTimeToHumanReadable(latestPushRegistrationAtPushProxy.toLong())
            }
        )
    }

    private fun getDeviceName(): String =
        if (MODEL.startsWith(MANUFACTURER, ignoreCase = true)) {
            MODEL
        } else {
            "$MANUFACTURER $MODEL"
        }

    private fun translateBoolean(answer: Boolean?): String =
        when (answer) {
            null -> context.resources.getString(R.string.nc_common_unknown)
            true -> context.resources.getString(R.string.nc_yes)
            else -> context.resources.getString(R.string.nc_no)
        }

    private fun List<DiagnoseElement>.toMarkdownString(): String {
        val markdownText = SpannableStringBuilder()

        this.forEach {
            when (it) {
                is DiagnoseElement.DiagnoseHeadline -> {
                    markdownText.append("$MARKDOWN_HEADLINE ${it.headline}")
                    markdownText.append("\n\n")
                }

                is DiagnoseElement.DiagnoseEntry -> {
                    markdownText.append("$MARKDOWN_BOLD${it.key}$MARKDOWN_BOLD")
                    markdownText.append("\n\n")
                    markdownText.append(it.value)
                    markdownText.append("\n\n")
                }
            }
        }
        return markdownText.toString()
    }

    private fun addHeadline(text: String) {
        diagnoseData.add(DiagnoseElement.DiagnoseHeadline(text))
    }

    private fun addDiagnosisEntry(key: String, value: String) {
        diagnoseData.add(DiagnoseElement.DiagnoseEntry(key, value))
    }

    companion object {
        val TAG = DiagnoseActivity::class.java.simpleName
        private const val MARKDOWN_HEADLINE = "###"
        private const val MARKDOWN_BOLD = "**"
        private const val PUSH_TOKEN_PREFIX_END: Int = 5
    }
}
