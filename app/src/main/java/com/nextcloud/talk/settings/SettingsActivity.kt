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
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.security.KeyChain
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.setAppTheme
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivitySettingsBinding
import com.nextcloud.talk.diagnose.DiagnoseActivity
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.checkPermission
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.deleteAll
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.profile.ProfileActivity
import com.nextcloud.talk.ui.dialog.SetPhoneNumberDialogFragment
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.LoggingUtils.sendMailWithAttachment
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.NotificationUtils.getCallRingtoneUri
import com.nextcloud.talk.utils.NotificationUtils.getMessageRingtoneUri
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.utils.preferences.AppPreferencesImpl
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import javax.inject.Inject

@Suppress("LargeClass", "TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class SettingsActivity : BaseActivity(), SetPhoneNumberDialogFragment.SetPhoneNumberDialogClickListener {
    private lateinit var binding: ActivitySettingsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    private var currentUser: User? = null
    private var credentials: String? = null
    private lateinit var proxyTypeFlow: Flow<String>
    private lateinit var proxyCredentialFlow: Flow<Boolean>
    private lateinit var screenSecurityFlow: Flow<Boolean>
    private lateinit var screenLockFlow: Flow<Boolean>
    private lateinit var screenLockTimeoutFlow: Flow<String>
    private lateinit var themeFlow: Flow<String>
    private lateinit var readPrivacyFlow: Flow<Boolean>
    private lateinit var typingStatusFlow: Flow<Boolean>
    private lateinit var phoneBookIntegrationFlow: Flow<Boolean>
    private var profileQueryDisposable: Disposable? = null
    private var dbQueryDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        binding.avatarImage.let { ViewCompat.setTransitionName(it, "userAvatar.transitionTag") }

        getCurrentUser()

        setupLicenceSetting()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            binding.settingsIncognitoKeyboard.visibility = View.GONE
        }

        binding.settingsScreenLockSummary.text = String.format(
            Locale.getDefault(),
            resources!!.getString(R.string.nc_settings_screen_lock_desc),
            resources!!.getString(R.string.nc_app_product_name)
        )

        setupDiagnose()
        setupPrivacyUrl()
        setupSourceCodeUrl()
        binding.settingsVersionSummary.text = String.format("v" + BuildConfig.VERSION_NAME)

        setupPhoneBookIntegration()

        setupClientCertView()
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()
        dispose(null)

        loadCapabilitiesAndUpdateSettings()

        binding.settingsVersion.setOnClickListener {
            sendLogs()
        }

        if (!TextUtils.isEmpty(currentUser!!.clientCertificate)) {
            binding.settingsClientCertTitle.setText(R.string.nc_client_cert_change)
        } else {
            binding.settingsClientCertTitle.setText(R.string.nc_client_cert_setup)
        }

        setupCheckables()
        setupScreenLockSetting()
        setupNotificationSettings()
        setupProxyTypeSettings()
        setupProxyCredentialSettings()
        registerChangeListeners()

        if (currentUser != null) {
            binding.domainText.text = Uri.parse(currentUser!!.baseUrl).host
            setupServerAgeWarning()
            if (currentUser!!.displayName != null) {
                binding.nameText.text = currentUser!!.displayName
            }
            DisplayUtils.loadAvatarImage(currentUser, binding.avatarImage, false)

            setupProfileQueryDisposable()

            binding.settingsRemoveAccount.setOnClickListener {
                showRemoveAccountWarning()
            }
        }
        setupMessageView()

        binding.settingsName.visibility = View.VISIBLE
        binding.settingsName.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        themeTitles()
        themeSwitchPreferences()
    }

    private fun loadCapabilitiesAndUpdateSettings() {
        val capabilitiesWork = OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java).build()
        WorkManager.getInstance(context).enqueue(capabilitiesWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(capabilitiesWork.id)
            .observe(this) { workInfo ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    getCurrentUser()
                    setupCheckables()
                }
            }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.settingsToolbar)
        binding.settingsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent, null)))
        supportActionBar?.title = context.getString(R.string.nc_settings)
        viewThemeUtils.material.themeToolbar(binding.settingsToolbar)
    }

    private fun getCurrentUser() {
        currentUser = currentUserProvider.currentUser.blockingGet()
        credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
    }

    private fun setupPhoneBookIntegration() {
        if (CapabilitiesUtil.hasSpreedFeatureCapability(
                currentUser?.capabilities?.spreedCapability!!,
                SpreedFeatures.PHONEBOOK_SEARCH
            )
        ) {
            binding.settingsPhoneBookIntegration.visibility = View.VISIBLE
        } else {
            binding.settingsPhoneBookIntegration.visibility = View.GONE
        }
    }

    private fun setupNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.settingsNotificationsTitle.text = resources!!.getString(
                R.string.nc_settings_notification_sounds_post_oreo
            )
        }
        setupNotificationSoundsSettings()
        setupNotificationPermissionSettings()
    }

    @Suppress("LongMethod")
    private fun setupNotificationPermissionSettings() {
        if (ClosedInterfaceImpl().isGooglePlayServicesAvailable) {
            binding.settingsGplayOnlyWrapper.visibility = View.VISIBLE

            setTroubleshootingClickListenersIfNecessary()

            if (PowerManagerUtils().isIgnoringBatteryOptimizations()) {
                binding.batteryOptimizationIgnored.text =
                    resources!!.getString(R.string.nc_diagnose_battery_optimization_ignored)
                binding.batteryOptimizationIgnored.setTextColor(
                    resources.getColor(R.color.high_emphasis_text, null)
                )
            } else {
                binding.batteryOptimizationIgnored.text =
                    resources!!.getString(R.string.nc_diagnose_battery_optimization_not_ignored)
                binding.batteryOptimizationIgnored.setTextColor(resources.getColor(R.color.nc_darkRed, null))

                binding.settingsBatteryOptimizationWrapper.setOnClickListener {
                    val dialogText = String.format(
                        context.resources.getString(R.string.nc_ignore_battery_optimization_dialog_text),
                        context.resources.getString(R.string.nc_app_name)
                    )

                    val dialogBuilder = MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.nc_ignore_battery_optimization_dialog_title)
                        .setMessage(dialogText)
                        .setPositiveButton(R.string.nc_ok) { _, _ ->
                            startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        }
                        .setNegativeButton(R.string.nc_common_dismiss, null)
                    viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
                    val dialog = dialogBuilder.show()
                    viewThemeUtils.platform.colorTextButtons(
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    )
                }
            }

            // handle notification permission on API level >= 33
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (platformPermissionUtil.isPostNotificationsPermissionGranted()) {
                    binding.ncDiagnoseNotificationPermissionSubtitle.text =
                        resources.getString(R.string.nc_settings_notifications_granted)
                    binding.ncDiagnoseNotificationPermissionSubtitle.setTextColor(
                        resources.getColor(R.color.high_emphasis_text, null)
                    )
                } else {
                    binding.ncDiagnoseNotificationPermissionSubtitle.text =
                        resources.getString(R.string.nc_settings_notifications_declined)
                    binding.ncDiagnoseNotificationPermissionSubtitle.setTextColor(
                        resources.getColor(R.color.nc_darkRed, null)
                    )
                    binding.settingsNotificationsPermissionWrapper.setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            ConversationsListActivity.REQUEST_POST_NOTIFICATIONS_PERMISSION
                        )
                    }
                }
            } else {
                binding.settingsNotificationsPermissionWrapper.visibility = View.GONE
            }
        } else {
            binding.settingsGplayOnlyWrapper.visibility = View.GONE
            binding.settingsGplayNotAvailable.visibility = View.VISIBLE
        }
    }

    private fun setupNotificationSoundsSettings() {
        if (NotificationUtils.isCallsNotificationChannelEnabled(this)) {
            val callRingtoneUri = getCallRingtoneUri(context, (appPreferences))

            binding.callsRingtone.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.callsRingtone.text = getRingtoneName(context, callRingtoneUri)
        } else {
            binding.callsRingtone.setTextColor(
                ResourcesCompat.getColor(context.resources, R.color.nc_darkRed, null)
            )
            binding.callsRingtone.text = resources!!.getString(R.string.nc_common_disabled)
        }

        if (NotificationUtils.isMessagesNotificationChannelEnabled(this)) {
            val messageRingtoneUri = getMessageRingtoneUri(context, (appPreferences))
            binding.messagesRingtone.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.messagesRingtone.text = getRingtoneName(context, messageRingtoneUri)
        } else {
            binding.messagesRingtone.setTextColor(
                ResourcesCompat.getColor(context.resources, R.color.nc_darkRed, null)
            )
            binding.messagesRingtone.text = resources!!.getString(R.string.nc_common_disabled)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.settingsCallSound.setOnClickListener {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                intent.putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name
                )

                startActivity(intent)
            }
            binding.settingsMessageSound.setOnClickListener {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                intent.putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
                )
                startActivity(intent)
            }
        } else {
            Log.w(TAG, "setupSoundSettings currently not supported for versions < Build.VERSION_CODES.O")
        }
    }

    private fun setTroubleshootingClickListenersIfNecessary() {
        fun click() {
            val dialogBuilder = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nc_notifications_troubleshooting_dialog_title)
                .setMessage(R.string.nc_notifications_troubleshooting_dialog_text)
                .setNegativeButton(R.string.nc_diagnose_dialog_open_checklist) { _, _ ->
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(resources.getString(R.string.notification_checklist_url))
                        )
                    )
                }
                .setPositiveButton(R.string.nc_diagnose_dialog_open_dontkillmyapp_website) { _, _ ->
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(resources.getString(R.string.dontkillmyapp_url))
                        )
                    )
                }
                .setNeutralButton(R.string.nc_diagnose_dialog_open_diagnose) { _, _ ->
                    val intent = Intent(context, DiagnoseActivity::class.java)
                    startActivity(intent)
                }
            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (platformPermissionUtil.isPostNotificationsPermissionGranted() &&
                PowerManagerUtils().isIgnoringBatteryOptimizations()
            ) {
                binding.settingsNotificationsPermissionWrapper.setOnClickListener { click() }
                binding.settingsBatteryOptimizationWrapper.setOnClickListener { click() }
            }
        } else if (PowerManagerUtils().isIgnoringBatteryOptimizations()) {
            binding.settingsBatteryOptimizationWrapper.setOnClickListener { click() }
        }
    }

    private fun setupSourceCodeUrl() {
        if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_source_code_url))) {
            binding.settingsSourceCode.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources!!.getString(R.string.nc_source_code_url))
                    )
                )
            }
        } else {
            binding.settingsSourceCode.visibility = View.GONE
        }
    }

    private fun setupDiagnose() {
        binding.diagnoseWrapper.setOnClickListener {
            val intent = Intent(context, DiagnoseActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupPrivacyUrl() {
        if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_privacy_url))) {
            binding.settingsPrivacy.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources!!.getString(R.string.nc_privacy_url))
                    )
                )
            }
        } else {
            binding.settingsPrivacy.visibility = View.GONE
        }
    }

    private fun setupLicenceSetting() {
        if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_gpl3_url))) {
            binding.settingsLicence.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources!!.getString(R.string.nc_gpl3_url))
                    )
                )
            }
        } else {
            binding.settingsLicence.visibility = View.GONE
        }
    }

    private fun setupClientCertView() {
        var host: String? = null
        var port = -1
        val uri: URI
        try {
            uri = URI(currentUser!!.baseUrl!!)
            host = uri.host
            port = uri.port
            Log.d(TAG, "uri is $uri")
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to create uri")
        }

        binding.settingsClientCert.setOnClickListener {
            KeyChain.choosePrivateKeyAlias(
                this,
                { alias: String? ->
                    var finalAlias: String? = alias

                    runOnUiThread {
                        if (finalAlias != null) {
                            binding.settingsClientCertTitle.setText(R.string.nc_client_cert_change)
                        } else {
                            binding.settingsClientCertTitle.setText(R.string.nc_client_cert_setup)
                        }
                    }

                    if (finalAlias == null) {
                        finalAlias = ""
                    }
                    Log.d(TAG, "host: $host and port: $port")
                    currentUser!!.clientCertificate = finalAlias
                    userManager.updateOrCreateUser(currentUser!!)
                },
                arrayOf("RSA", "EC"),
                null,
                host,
                port,
                currentUser!!.clientCertificate
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun registerChangeListeners() {
        val appPreferences = AppPreferencesImpl(context)
        proxyTypeFlow = appPreferences.readString(AppPreferencesImpl.PROXY_TYPE)
        proxyCredentialFlow = appPreferences.readBoolean(AppPreferencesImpl.PROXY_CRED)
        screenSecurityFlow = appPreferences.readBoolean(AppPreferencesImpl.SCREEN_SECURITY)
        screenLockFlow = appPreferences.readBoolean(AppPreferencesImpl.SCREEN_LOCK)
        screenLockTimeoutFlow = appPreferences.readString(AppPreferencesImpl.SCREEN_LOCK_TIMEOUT)

        val themeKey = context.resources.getString(R.string.nc_settings_theme_key)
        themeFlow = appPreferences.readString(themeKey)

        val privacyKey = context.resources.getString(R.string.nc_settings_read_privacy_key)
        readPrivacyFlow = appPreferences.readBoolean(privacyKey)

        typingStatusFlow = appPreferences.readBoolean(AppPreferencesImpl.TYPING_STATUS)
        phoneBookIntegrationFlow = appPreferences.readBoolean(AppPreferencesImpl.PHONE_BOOK_INTEGRATION)

        var pos = resources.getStringArray(R.array.screen_lock_timeout_entry_values).indexOf(
            appPreferences.screenLockTimeout
        )
        binding.settingsScreenLockTimeoutLayoutDropdown.setText(
            resources.getStringArray(R.array.screen_lock_timeout_descriptions)[pos]
        )

        binding.settingsScreenLockTimeoutLayoutDropdown.setSimpleItems(R.array.screen_lock_timeout_descriptions)
        binding.settingsScreenLockTimeoutLayoutDropdown.setOnItemClickListener { _, _, position, _ ->
            val entryVal: String = resources.getStringArray(R.array.screen_lock_timeout_entry_values)[position]
            appPreferences.screenLockTimeout = entryVal
            SecurityUtils.createKey(entryVal)
        }
        pos = resources.getStringArray(R.array.theme_entry_values).indexOf(appPreferences.theme)
        binding.settingsTheme.setText(resources.getStringArray(R.array.theme_descriptions)[pos])

        binding.settingsTheme.setSimpleItems(R.array.theme_descriptions)
        binding.settingsTheme.setOnItemClickListener { _, _, position, _ ->
            val entryVal: String = resources.getStringArray(R.array.theme_entry_values)[position]
            appPreferences.theme = entryVal
        }

        observeProxyType()
        observeProxyCredential()
        observeScreenSecurity()
        observeScreenLock()
        observeTheme()
        observeReadPrivacy()
        observeTypingStatus()
    }

    fun sendLogs() {
        if (resources!!.getBoolean(R.bool.nc_is_debug)) {
            sendMailWithAttachment((context))
        }
    }

    private fun showRemoveAccountWarning() {
        binding.messageText.context?.let {
            val materialAlertDialogBuilder = MaterialAlertDialogBuilder(it)
                .setTitle(R.string.nc_settings_remove_account)
                .setMessage(R.string.nc_settings_remove_confirmation)
                .setPositiveButton(R.string.nc_settings_remove) { _, _ ->
                    removeCurrentAccount()
                }
                .setNegativeButton(R.string.nc_cancel) { _, _ ->
                    // unused atm
                }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(
                it,
                materialAlertDialogBuilder
            )

            val dialog = materialAlertDialogBuilder.show()

            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    @SuppressLint("CheckResult")
    private fun removeCurrentAccount() {
        userManager.scheduleUserForDeletionWithId(currentUser!!.id!!).blockingGet()
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
            .observeForever { workInfo: WorkInfo ->

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val text = String.format(
                            context.resources.getString(R.string.nc_deleted_user),
                            currentUser!!.displayName
                        )
                        Toast.makeText(
                            context,
                            text,
                            Toast.LENGTH_LONG
                        ).show()
                        restartApp()
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "something went wrong when deleting user with id " + currentUser!!.userId)
                        restartApp()
                    }

                    else -> {}
                }
            }
    }

    private fun restartApp() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun getRingtoneName(context: Context, ringtoneUri: Uri?): String {
        return if (ringtoneUri == null) {
            resources!!.getString(R.string.nc_settings_no_ringtone)
        } else if ((NotificationUtils.DEFAULT_CALL_RINGTONE_URI == ringtoneUri.toString()) ||
            (NotificationUtils.DEFAULT_MESSAGE_RINGTONE_URI == ringtoneUri.toString())
        ) {
            resources!!.getString(R.string.nc_settings_default_ringtone)
        } else {
            val r = RingtoneManager.getRingtone(context, ringtoneUri)
            r.getTitle(context)
        }
    }

    private fun themeSwitchPreferences() {
        binding.run {
            listOf(
                settingsScreenLockSwitch,
                settingsScreenSecuritySwitch,
                settingsIncognitoKeyboardSwitch,
                settingsPhoneBookIntegrationSwitch,
                settingsReadPrivacySwitch,
                settingsTypingStatusSwitch,
                settingsProxyUseCredentialsSwitch
            ).forEach(viewThemeUtils.talk::colorSwitch)
        }
    }

    private fun themeTitles() {
        binding.run {
            listOf(
                settingsNotificationsTitle,
                settingsAboutTitle,
                settingsAdvancedTitle,
                settingsAppearanceTitle,
                settingsPrivacyTitle
            ).forEach(viewThemeUtils.platform::colorTextView)
        }
    }

    private fun setupProxyTypeSettings() {
        if (appPreferences.proxyType == null) {
            appPreferences.proxyType = resources.getString(R.string.nc_no_proxy)
        }
        binding.settingsProxyChoice.setText(appPreferences.proxyType)
        binding.settingsProxyChoice.setSimpleItems(R.array.proxy_type_descriptions)
        binding.settingsProxyChoice.setOnItemClickListener { _, _, position, _ ->
            val entryVal = resources.getStringArray(R.array.proxy_type_descriptions)[position]
            appPreferences.proxyType = entryVal
        }

        binding.settingsProxyHostEdit.setText(appPreferences.proxyHost)
        binding.settingsProxyHostEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                appPreferences.proxyHost = binding.settingsProxyHostEdit.text.toString()
            }
        }

        binding.settingsProxyPortEdit.setText(appPreferences.proxyPort)
        binding.settingsProxyPortEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                appPreferences.proxyPort = binding.settingsProxyPortEdit.text.toString()
            }
        }
        binding.settingsProxyUsernameEdit.setText(appPreferences.proxyUsername)
        binding.settingsProxyUsernameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                appPreferences.proxyUsername = binding.settingsProxyUsernameEdit.text.toString()
            }
        }
        binding.settingsProxyPasswordEdit.setText(appPreferences.proxyPassword)
        binding.settingsProxyPasswordEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                appPreferences.proxyPassword = binding.settingsProxyPasswordEdit.text.toString()
            }
        }

        if (((context.resources.getString(R.string.nc_no_proxy)) == appPreferences.proxyType) ||
            appPreferences.proxyType == null
        ) {
            hideProxySettings()
        } else {
            showProxySettings()
        }
    }

    private fun setupProxyCredentialSettings() {
        if (appPreferences.proxyCredentials) {
            showProxyCredentials()
        } else {
            hideProxyCredentials()
        }
    }

    private fun setupMessageView() {
        if (ApplicationWideMessageHolder.getInstance().messageType != null) {
            when (ApplicationWideMessageHolder.getInstance().messageType) {
                ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED -> {
                    binding.messageText.let {
                        viewThemeUtils.platform.colorTextView(it, ColorRole.PRIMARY)
                        it.text = resources!!.getString(R.string.nc_settings_account_updated)
                        binding.messageText.visibility = View.VISIBLE
                    }
                }

                ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK -> {
                    binding.messageText.let {
                        it.setTextColor(resources!!.getColor(R.color.nc_darkRed, null))
                        it.text = resources!!.getString(R.string.nc_settings_wrong_account)
                        binding.messageText.visibility = View.VISIBLE
                        viewThemeUtils.platform.colorTextView(it, ColorRole.PRIMARY)
                        it.text = resources!!.getString(R.string.nc_Server_account_imported)
                        binding.messageText.visibility = View.VISIBLE
                    }
                }

                ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED -> {
                    binding.messageText.let {
                        viewThemeUtils.platform.colorTextView(it, ColorRole.PRIMARY)
                        it.text = resources!!.getString(R.string.nc_Server_account_imported)
                        binding.messageText.visibility = View.VISIBLE
                    }
                }

                ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT -> {
                    binding.messageText.let {
                        it.setTextColor(resources!!.getColor(R.color.nc_darkRed, null))
                        it.text = resources!!.getString(R.string.nc_server_failed_to_import_account)
                        binding.messageText.visibility = View.VISIBLE
                    }
                }

                else -> binding.messageText.visibility = View.GONE
            }
            ApplicationWideMessageHolder.getInstance().messageType = null
            binding.messageText.animate()
                ?.translationY(0f)
                ?.alpha(0.0f)
                ?.setDuration(DURATION)
                ?.setStartDelay(START_DELAY)
                ?.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        binding.messageText.visibility = View.GONE
                    }
                })
        } else {
            binding.messageText.visibility = View.GONE
        }
    }

    private fun setupProfileQueryDisposable() {
        profileQueryDisposable = ncApi.getUserProfile(
            credentials,
            ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl!!)
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { userProfileOverall: UserProfileOverall ->
                    var displayName: String? = null
                    if (!TextUtils.isEmpty(
                            userProfileOverall.ocs!!.data!!.displayName
                        )
                    ) {
                        displayName = userProfileOverall.ocs!!.data!!.displayName
                    } else if (!TextUtils.isEmpty(
                            userProfileOverall.ocs!!.data!!.displayNameAlt
                        )
                    ) {
                        displayName = userProfileOverall.ocs!!.data!!.displayNameAlt
                    }
                    if ((!TextUtils.isEmpty(displayName) && !(displayName == currentUser!!.displayName))) {
                        currentUser!!.displayName = displayName
                        userManager.updateOrCreateUser(currentUser!!)
                        binding.nameText.text = currentUser!!.displayName
                    }
                },
                { dispose(profileQueryDisposable) },
                { dispose(profileQueryDisposable) }
            )
    }

    private fun setupServerAgeWarning() {
        when {
            CapabilitiesUtil.isServerEOL(currentUser!!.serverVersion?.major) -> {
                binding.serverAgeWarningText.setTextColor(ContextCompat.getColor((context), R.color.nc_darkRed))
                binding.serverAgeWarningText.setText(R.string.nc_settings_server_eol)
                binding.serverAgeWarningIcon.setColorFilter(
                    ContextCompat.getColor((context), R.color.nc_darkRed),
                    PorterDuff.Mode.SRC_IN
                )
            }

            CapabilitiesUtil.isServerAlmostEOL(currentUser!!.serverVersion?.major) -> {
                binding.serverAgeWarningText.setTextColor(
                    ContextCompat.getColor((context), R.color.nc_darkYellow)
                )
                binding.serverAgeWarningText.setText(R.string.nc_settings_server_almost_eol)
                binding.serverAgeWarningIcon.setColorFilter(
                    ContextCompat.getColor((context), R.color.nc_darkYellow),
                    PorterDuff.Mode.SRC_IN
                )
            }

            else -> {
                binding.serverAgeWarningTextCard.visibility = View.GONE
            }
        }
    }

    private fun setupCheckables() {
        binding.settingsScreenSecuritySwitch.isChecked = appPreferences.isScreenSecured

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.settingsIncognitoKeyboardSwitch.isChecked = appPreferences.isKeyboardIncognito
        } else {
            binding.settingsIncognitoKeyboardSwitch.visibility = View.GONE
        }

        if (CapabilitiesUtil.isReadStatusAvailable(currentUser!!.capabilities!!.spreedCapability!!)) {
            binding.settingsReadPrivacySwitch.isChecked = !CapabilitiesUtil.isReadStatusPrivate(currentUser!!)
        } else {
            binding.settingsReadPrivacy.visibility = View.GONE
        }

        setupTypingStatusSetting()

        binding.settingsPhoneBookIntegrationSwitch.isChecked = appPreferences.isPhoneBookIntegrationEnabled

        binding.settingsProxyUseCredentialsSwitch.isChecked = appPreferences.proxyCredentials
        binding.settingsProxyUseCredentials.setOnClickListener {
            val isChecked = binding.settingsProxyUseCredentialsSwitch.isChecked
            binding.settingsProxyUseCredentialsSwitch.isChecked = !isChecked
            appPreferences.setProxyNeedsCredentials(!isChecked)
        }

        binding.settingsScreenLockSwitch.isChecked = appPreferences.isScreenLocked
        binding.settingsScreenLock.setOnClickListener {
            val isChecked = binding.settingsScreenLockSwitch.isChecked
            binding.settingsScreenLockSwitch.isChecked = !isChecked
            appPreferences.setScreenLock(!isChecked)
        }

        binding.settingsReadPrivacy.setOnClickListener {
            val isChecked = binding.settingsReadPrivacySwitch.isChecked
            binding.settingsReadPrivacySwitch.isChecked = !isChecked
            appPreferences.setReadPrivacy(!isChecked)
        }

        binding.settingsIncognitoKeyboard.setOnClickListener {
            val isChecked = binding.settingsIncognitoKeyboardSwitch.isChecked
            binding.settingsIncognitoKeyboardSwitch.isChecked = !isChecked
            appPreferences.setIncognitoKeyboard(!isChecked)
        }

        binding.settingsPhoneBookIntegration.setOnClickListener {
            val isChecked = binding.settingsPhoneBookIntegrationSwitch.isChecked
            binding.settingsPhoneBookIntegrationSwitch.isChecked = !isChecked
            appPreferences.setPhoneBookIntegration(!isChecked)
            if (!isChecked) {
                if (checkPermission(this@SettingsActivity, (context))) {
                    checkForPhoneNumber()
                }
            } else {
                deleteAll()
            }
        }

        binding.settingsScreenSecurity.setOnClickListener {
            val isChecked = binding.settingsScreenSecuritySwitch.isChecked
            binding.settingsScreenSecuritySwitch.isChecked = !isChecked
            appPreferences.setScreenSecurity(!isChecked)
        }

        binding.settingsTypingStatus.setOnClickListener {
            val isChecked = binding.settingsTypingStatusSwitch.isChecked
            binding.settingsTypingStatusSwitch.isChecked = !isChecked
            appPreferences.setTypingStatus(!isChecked)
        }
    }

    private fun setupTypingStatusSetting() {
        if (currentUser!!.externalSignalingServer?.externalSignalingServer?.isNotEmpty() == true) {
            binding.settingsTypingStatusOnlyWithHpb.visibility = View.GONE
            Log.i(TAG, "Typing Status Available: ${CapabilitiesUtil.isTypingStatusAvailable(currentUser!!)}")

            if (CapabilitiesUtil.isTypingStatusAvailable(currentUser!!)) {
                binding.settingsTypingStatusSwitch.isChecked = !CapabilitiesUtil.isTypingStatusPrivate(currentUser!!)
            } else {
                binding.settingsTypingStatus.visibility = View.GONE
            }
        } else {
            Log.i(TAG, "Typing Status not Available")
            binding.settingsTypingStatusSwitch.isChecked = false
            binding.settingsTypingStatusOnlyWithHpb.visibility = View.VISIBLE
            binding.settingsTypingStatus.isEnabled = false
            binding.settingsTypingStatusOnlyWithHpb.alpha = DISABLED_ALPHA
            binding.settingsTypingStatus.alpha = DISABLED_ALPHA
        }
    }

    private fun setupScreenLockSetting() {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure) {
            binding.settingsScreenLock.isEnabled = true
            binding.settingsScreenLockTimeout.isEnabled = true
            binding.settingsScreenLockSwitch.isChecked = appPreferences.isScreenLocked
            binding.settingsScreenLockTimeoutLayoutDropdown.isEnabled = appPreferences.isScreenLocked
            if (appPreferences.isScreenLocked) {
                binding.settingsScreenLockTimeout.alpha = ENABLED_ALPHA
            } else {
                binding.settingsScreenLockTimeout.alpha = DISABLED_ALPHA
            }
            binding.settingsScreenLock.alpha = ENABLED_ALPHA
        } else {
            binding.settingsScreenLock.isEnabled = false
            binding.settingsScreenLockTimeoutLayoutDropdown.isEnabled = false
            appPreferences.removeScreenLock()
            appPreferences.removeScreenLockTimeout()
            binding.settingsScreenLockSwitch.isChecked = false
            binding.settingsScreenLock.alpha = DISABLED_ALPHA
            binding.settingsScreenLockTimeout.alpha = DISABLED_ALPHA
        }
    }

    public override fun onDestroy() {
        // appPreferences.unregisterProxyTypeListener(proxyTypeChangeListener)
        // appPreferences.unregisterProxyCredentialsListener(proxyCredentialsChangeListener)
        // appPreferences.unregisterScreenSecurityListener(screenSecurityChangeListener)
        // appPreferences.unregisterScreenLockListener(screenLockChangeListener)
        // appPreferences.unregisterScreenLockTimeoutListener(screenLockTimeoutChangeListener)
        // appPreferences.unregisterThemeChangeListener(themeChangeListener)
        // appPreferences.unregisterReadPrivacyChangeListener(readPrivacyChangeListener)
        // appPreferences.unregisterTypingStatusChangeListener(typingStatusChangeListener)
        // appPreferences.unregisterPhoneBookIntegrationChangeListener(phoneBookIntegrationChangeListener)

        super.onDestroy()
    }

    private fun hideProxySettings() {
        appPreferences.removeProxyHost()
        appPreferences.removeProxyPort()
        appPreferences.removeProxyCredentials()
        appPreferences.removeProxyUsername()
        appPreferences.removeProxyPassword()
        binding.settingsProxyHostLayout.visibility = View.GONE
        binding.settingsProxyPortLayout.visibility = View.GONE
        binding.settingsProxyUseCredentials.visibility = View.GONE
        hideProxyCredentials()
    }

    private fun showProxySettings() {
        binding.settingsProxyHostLayout.visibility =
            View.VISIBLE
        binding.settingsProxyPortLayout.visibility =
            View.VISIBLE
        binding.settingsProxyUseCredentials.visibility =
            View.VISIBLE
        if (binding.settingsProxyUseCredentialsSwitch.isChecked) showProxyCredentials()
    }

    private fun showProxyCredentials() {
        binding.settingsProxyUsernameLayout.visibility =
            View.VISIBLE
        binding.settingsProxyPasswordLayout.visibility =
            View.VISIBLE
    }

    private fun hideProxyCredentials() {
        appPreferences.removeProxyUsername()
        appPreferences.removeProxyPassword()
        binding.settingsProxyUsernameLayout.visibility = View.GONE
        binding.settingsProxyPasswordLayout.visibility = View.GONE
    }

    private fun dispose(disposable: Disposable?) {
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
        } else if (disposable == null) {
            disposeProfileQueryDisposable()
            disposeDbQueryDisposable()
        }
    }

    private fun disposeDbQueryDisposable() {
        if (dbQueryDisposable != null && !dbQueryDisposable!!.isDisposed) {
            dbQueryDisposable!!.dispose()
            dbQueryDisposable = null
        } else if (dbQueryDisposable != null) {
            dbQueryDisposable = null
        }
    }

    private fun disposeProfileQueryDisposable() {
        if (profileQueryDisposable != null && !profileQueryDisposable!!.isDisposed) {
            profileQueryDisposable!!.dispose()
            profileQueryDisposable = null
        } else if (profileQueryDisposable != null) {
            profileQueryDisposable = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            ContactAddressBookWorker.REQUEST_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    WorkManager
                        .getInstance(this)
                        .enqueue(OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java).build())
                    checkForPhoneNumber()
                } else {
                    appPreferences.setPhoneBookIntegration(false)
                    binding.settingsPhoneBookIntegrationSwitch.isChecked = appPreferences.isPhoneBookIntegrationEnabled
                    Snackbar.make(
                        binding.root,
                        context.resources.getString(R.string.no_phone_book_integration_due_to_permissions),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }

            ConversationsListActivity.REQUEST_POST_NOTIFICATIONS_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Snackbar.make(
                        binding.root,
                        context.resources.getString(R.string.nc_settings_notifications_declined_hint),
                        Snackbar.LENGTH_LONG
                    ).show()
                    Log.d(
                        TAG,
                        "Notification permission is denied. Either because user denied it when being asked. " +
                            "Or permission is already denied and android decided to not offer the dialog."
                    )
                }
            }
        }
    }

    private fun observeScreenLock() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.isScreenLocked
            screenLockFlow.collect { newBoolean ->
                if (newBoolean != state) {
                    state = newBoolean
                    binding.settingsScreenLockTimeout.isEnabled = newBoolean
                    if (newBoolean) {
                        binding.settingsScreenLockTimeout.alpha = ENABLED_ALPHA
                    } else {
                        binding.settingsScreenLockTimeout.alpha = DISABLED_ALPHA
                    }
                }
            }
        }
    }

    private fun observeScreenSecurity() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.isScreenSecured
            screenSecurityFlow.collect { newBoolean ->
                if (newBoolean != state) {
                    state = newBoolean
                    if (newBoolean) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun observeProxyCredential() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.proxyCredentials
            proxyCredentialFlow.collect { newBoolean ->
                if (newBoolean != state) {
                    state = newBoolean
                    if (newBoolean) {
                        showProxyCredentials()
                    } else {
                        hideProxyCredentials()
                    }
                }
            }
        }
    }

    private fun observeProxyType() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.proxyType
            proxyTypeFlow.collect { newString ->
                if (newString != state) {
                    state = newString
                    if (((context.resources.getString(R.string.nc_no_proxy)) == newString) || newString.isEmpty()) {
                        hideProxySettings()
                    } else {
                        when (newString) {
                            "HTTP" -> {
                                binding.settingsProxyPortEdit.setText(getString(R.string.nc_settings_http_value))
                                appPreferences.proxyPort = getString(R.string.nc_settings_http_value)
                            }

                            "DIRECT" -> {
                                binding.settingsProxyPortEdit.setText(getString(R.string.nc_settings_direct_value))
                                appPreferences.proxyPort = getString(R.string.nc_settings_direct_value)
                            }

                            "SOCKS" -> {
                                binding.settingsProxyPortEdit.setText(getString(R.string.nc_settings_socks_value))
                                appPreferences.proxyPort = getString(R.string.nc_settings_socks_value)
                            }

                            else -> {
                            }
                        }
                        showProxySettings()
                    }
                }
            }
        }
    }

    private fun observeTheme() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.theme
            themeFlow.collect { newString ->
                if (newString != state) {
                    state = newString
                    setAppTheme(newString)
                }
            }
        }
    }

    private fun checkForPhoneNumber() {
        ncApi.getUserData(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl!!)
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<UserProfileOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(userProfileOverall: UserProfileOverall) {
                    if (userProfileOverall.ocs!!.data!!.phone?.isEmpty() == true) {
                        askForPhoneNumber()
                    } else {
                        Log.d(TAG, "phone number already set")
                    }
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun askForPhoneNumber() {
        val dialog = SetPhoneNumberDialogFragment.newInstance()
        dialog.show(supportFragmentManager, SetPhoneNumberDialogFragment.TAG)
    }

    override fun onSubmitClick(textInputLayout: TextInputLayout, dialog: DialogInterface) {
        setPhoneNumber(textInputLayout, dialog)
    }

    private fun setPhoneNumber(textInputLayout: TextInputLayout, dialog: DialogInterface) {
        val phoneNumber = textInputLayout.editText!!.text.toString()
        ncApi.setUserData(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForUserData(currentUser!!.baseUrl!!, currentUser!!.userId!!),
            "phone",
            phoneNumber
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    when (val statusCode = genericOverall.ocs?.meta?.statusCode) {
                        HTTP_CODE_OK -> {
                            dialog.dismiss()
                            Snackbar.make(
                                binding.root,
                                getString(
                                    R.string.nc_settings_phone_book_integration_phone_number_dialog_success
                                ),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        else -> {
                            textInputLayout.helperText = getString(
                                R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid
                            )
                            Log.d(TAG, "failed to set phoneNumber. statusCode=$statusCode")
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    textInputLayout.helperText = getString(
                        R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid
                    )
                    Log.e(SetPhoneNumberDialogFragment.TAG, "setPhoneNumber error", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun observeReadPrivacy() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.readPrivacy
            readPrivacyFlow.collect { newBoolean ->
                if (state != newBoolean) {
                    state = newBoolean
                    val booleanValue = if (newBoolean) "0" else "1"
                    val json = "{\"key\": \"read_status_privacy\", \"value\" : $booleanValue}"
                    ncApi.setReadStatusPrivacy(
                        ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                        ApiUtils.getUrlForUserSettings(currentUser!!.baseUrl!!),
                        json.toRequestBody("application/json".toMediaTypeOrNull())
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<GenericOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }

                            override fun onNext(genericOverall: GenericOverall) {
                                // unused atm
                            }

                            override fun onError(e: Throwable) {
                                appPreferences.setReadPrivacy(!newBoolean)
                                binding.settingsReadPrivacySwitch.isChecked = !newBoolean
                            }

                            override fun onComplete() {
                                // unused atm
                            }
                        })
                }
            }
        }
    }

    private fun observeTypingStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            var state = appPreferences.typingStatus
            typingStatusFlow.collect { newBoolean ->
                if (state != newBoolean) {
                    state = newBoolean
                    val booleanValue = if (newBoolean) "0" else "1"
                    val json = "{\"key\": \"typing_privacy\", \"value\" : $booleanValue}"
                    ncApi.setTypingStatusPrivacy(
                        ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                        ApiUtils.getUrlForUserSettings(currentUser!!.baseUrl!!),
                        json.toRequestBody("application/json".toMediaTypeOrNull())
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<GenericOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }

                            override fun onNext(genericOverall: GenericOverall) {
                                loadCapabilitiesAndUpdateSettings()
                                Log.i(TAG, "onNext called typing status set")
                            }

                            override fun onError(e: Throwable) {
                                appPreferences.typingStatus = !newBoolean
                                binding.settingsTypingStatusSwitch.isChecked = !newBoolean
                            }

                            override fun onComplete() {
                                // unused atm
                            }
                        })
                }
            }
        }
    }

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
        private const val DURATION: Long = 2500
        private const val START_DELAY: Long = 5000
        private const val DISABLED_ALPHA: Float = 0.38f
        private const val ENABLED_ALPHA: Float = 1.0f
        const val HTTP_CODE_OK: Int = 200
    }
}
