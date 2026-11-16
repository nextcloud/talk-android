/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.security.KeyChain
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.setAppTheme
import com.nextcloud.talk.dagger.modules.assistedViewModels
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.diagnosis.DiagnosisActivity
import com.nextcloud.talk.diagnosis.buildDiagnosisReportText
import com.nextcloud.talk.diagnosis.showShareReportDialog
import com.nextcloud.talk.errorhandling.saveLogsAsZip
import com.nextcloud.talk.logger.LogsRepository
import com.nextcloud.talk.logger.ui.LogsActivity
import com.nextcloud.talk.profile.ProfileActivity
import com.nextcloud.talk.settings.ui.ProxyCallbacks
import com.nextcloud.talk.settings.ui.SettingsScreen
import com.nextcloud.talk.settings.ui.SettingsScreenCallbacks
import com.nextcloud.talk.settings.ui.SystemSettingsScreen
import com.nextcloud.talk.utils.LoggingUtils.sendMailWithAttachment
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SCROLL_TO_NOTIFICATION_CATEGORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SettingsActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: SettingsViewModel.Factory

    @Inject
    lateinit var logsRepository: LogsRepository

    private lateinit var user: User

    private val viewModel: SettingsViewModel by assistedViewModels {
        viewModelFactory.build(user, intent.getBooleanExtra(KEY_SCROLL_TO_NOTIFICATION_CATEGORY, false))
    }

    private val snackbarHostState = SnackbarHostState()

    private val saveZipLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                val diagnosisText = buildDiagnosisReportText(this, userManager, appPreferences, logsRepository)
                lifecycleScope.launch(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        saveLogsAsZip(this@SettingsActivity, os, diagnosisText)
                    }
                }
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.refreshDeviceState()
            } else {
                openSystemSettings(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                )
            }
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.isNotEmpty() && results.values.all { it }) {
                viewModel.enablePhoneBookIntegration()
            } else {
                viewModel.onContactsPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        user = setUpBoundUserOrFinish() ?: return

        val colorScheme = viewThemeUtils.getColorScheme(this)
        val callbacks = createCallbacks()
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                SettingsScreen(state = state, callbacks = callbacks, snackbarHostState = snackbarHostState)
            }
        }

        collectEvents()
        applySecureWindowFlag()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    @Suppress("LongMethod")
    private fun createCallbacks() =
        SettingsScreenCallbacks(
            onProfileClick = { startActivity(intentForAccount(ProfileActivity::class.java)) },
            onAccountMessageShown = viewModel::dismissAccountMessage,
            onRemoveAccount = viewModel::removeAccount,
            onThemeChange = { theme ->
                viewModel.setTheme(theme)
                setAppTheme(theme)
            },
            onShowEcosystemChange = viewModel::setShowEcosystem,
            onUnifiedPushChange = ::setUseUnifiedPush,
            onPickDistributor = {
                UnifiedPushUtils.pickDistributor(this) { distributor ->
                    distributor?.let { runOnUiThread { viewModel.onDistributorChanged(it) } }
                }
            },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenSystemSettings = ::openSystemSettings,
            onOpenDiagnosis = { startActivity(intentForAccount(DiagnosisActivity::class.java)) },
            onShowNotificationWarningChange = viewModel::setShowNotificationWarning,
            onScreenLockChange = viewModel::setScreenLock,
            onScreenLockTimeoutChange = viewModel::setScreenLockTimeout,
            onScreenSecurityChange = viewModel::setScreenSecurity,
            onIncognitoKeyboardChange = viewModel::setIncognitoKeyboard,
            onPhoneBookIntegrationChange = { enabled ->
                if (enabled) {
                    contactsPermissionLauncher.launch(CONTACTS_PERMISSIONS)
                } else {
                    viewModel.disablePhoneBookIntegration()
                }
            },
            onReadStatusChange = viewModel::setReadStatusShared,
            onTypingStatusChange = viewModel::setTypingStatusShared,
            onPhoneNumberChange = viewModel::clearPhoneNumberError,
            onSubmitPhoneNumber = viewModel::submitPhoneNumber,
            onDismissPhoneNumberDialog = viewModel::dismissPhoneNumberDialog,
            onOpenLogs = ::openLogs,
            onShareReport = {
                showShareReportDialog(this, userManager, appPreferences, logsRepository, saveZipLauncher)
            },
            onMediaQualityChange = viewModel::setMediaQualityReduced,
            onClientCertificateClick = ::chooseClientCertificate,
            proxy = ProxyCallbacks(
                onTypeChange = viewModel::setProxyType,
                onHostChange = viewModel::setProxyHost,
                onPortChange = viewModel::setProxyPort,
                onUseCredentialsChange = viewModel::setProxyUseCredentials,
                onUsernameChange = viewModel::setProxyUsername,
                onPasswordChange = viewModel::setProxyPassword
            ),
            onVersionClick = {
                if (resources.getBoolean(R.bool.nc_is_debug)) {
                    sendMailWithAttachment(this)
                }
            }
        )

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            // shown in its own coroutine, so that later events don't wait until the snackbar is gone
            is SettingsEvent.ShowMessage -> lifecycleScope.launch {
                snackbarHostState.showSnackbar(getString(event.messageRes))
            }
            SettingsEvent.AccountUnavailable -> finish()
        }
    }

    /** Keeps screenshots and the preview in the recent apps off while screen security or the app lock is on. */
    private fun applySecureWindowFlag() {
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.privacy.screenSecurity || it.privacy.screenLock }
                .distinctUntilChanged()
                .collect { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }
    }

    private fun setUseUnifiedPush(enabled: Boolean) {
        viewModel.setUseUnifiedPush(enabled)
        if (enabled) {
            UnifiedPushUtils.useDefaultDistributor(this) { distributor ->
                Log.d(TAG, "Registered to $distributor")
                distributor?.let { runOnUiThread { viewModel.onDistributorChanged(it) } }
            }
        } else {
            UnifiedPushUtils.disableExternalUnifiedPush(this)
        }
    }

    private fun chooseClientCertificate() {
        val uri = try {
            URI(user.baseUrl!!)
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to parse the server address", e)
            null
        }
        KeyChain.choosePrivateKeyAlias(
            this,
            { alias -> runOnUiThread { viewModel.onClientCertificateChosen(alias) } },
            arrayOf("RSA", "EC"),
            null,
            uri?.host,
            uri?.port ?: -1,
            viewModel.clientCertificateAlias
        )
    }

    private fun openSystemSettings(screen: SystemSettingsScreen) {
        val intent = when (screen) {
            SystemSettingsScreen.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            SystemSettingsScreen.FULL_SCREEN_INTENT -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri())
            }
            SystemSettingsScreen.CALL_NOTIFICATIONS ->
                notificationChannelSettingsIntent(NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V5)
            SystemSettingsScreen.MESSAGE_NOTIFICATIONS ->
                notificationChannelSettingsIntent(
                    NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4
                )
        }
        openSystemSettings(intent)
    }

    private fun openSystemSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Failed to open the system settings", e)
        }
    }

    private fun notificationChannelSettingsIntent(channel: NotificationUtils.NotificationChannels): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.name)

    private fun openLogs() {
        startActivity(Intent(this, LogsActivity::class.java))
    }

    private fun intentForAccount(activity: Class<*>): Intent =
        Intent(this, activity).putExtra(BundleKeys.KEY_INTERNAL_USER_ID, user.id)

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
        private val CONTACTS_PERMISSIONS =
            arrayOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
    }
}
