/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
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
package com.nextcloud.talk.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.security.KeyChain
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Checkable
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.setAppTheme
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivitySettingsBinding
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.checkPermission
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.deleteAll
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.profile.ProfileActivity
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.LoggingUtils.sendMailWithAttachment
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.NotificationUtils.getCallRingtoneUri
import com.nextcloud.talk.utils.NotificationUtils.getMessageRingtoneUri
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.MagicUserInputModule
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.net.URI
import java.net.URISyntaxException
import java.util.Arrays
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    private var currentUser: User? = null
    private var credentials: String? = null
    private var proxyTypeChangeListener: OnPreferenceValueChangedListener<String>? = null
    private var proxyCredentialsChangeListener: OnPreferenceValueChangedListener<Boolean>? = null
    private var screenSecurityChangeListener: OnPreferenceValueChangedListener<Boolean>? = null
    private var screenLockChangeListener: OnPreferenceValueChangedListener<Boolean>? = null
    private var screenLockTimeoutChangeListener: OnPreferenceValueChangedListener<String?>? = null
    private var themeChangeListener: OnPreferenceValueChangedListener<String?>? = null
    private var readPrivacyChangeListener: OnPreferenceValueChangedListener<Boolean>? = null
    private var typingStatusChangeListener: OnPreferenceValueChangedListener<Boolean>? = null
    private var phoneBookIntegrationChangeListener: OnPreferenceValueChangedListener<Boolean>? = null
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

        registerChangeListeners()

        setupSettingsScreen()
        setupLicenceSetting()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            binding.settingsIncognitoKeyboard.visibility = View.GONE
        }

        binding.settingsScreenLock.setSummary(
            String.format(
                Locale.getDefault(),
                resources!!.getString(R.string.nc_settings_screen_lock_desc),
                resources!!.getString(R.string.nc_app_product_name)
            )
        )

        setupPrivacyUrl()
        setupSourceCodeUrl()
        binding.settingsVersion.setSummary("v" + BuildConfig.VERSION_NAME)

        setupSoundSettings()

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
            binding.settingsClientCert.setTitle(R.string.nc_client_cert_change)
        } else {
            binding.settingsClientCert.setTitle(R.string.nc_client_cert_setup)
        }

        setupCheckables()
        setupScreenLockSetting()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.settingsNotificationsCategory.setTitle(
                resources!!.getString(R.string.nc_settings_notification_sounds_post_oreo)
            )
        }

        val callRingtoneUri = getCallRingtoneUri(context, (appPreferences))
        binding.settingsCallSound.setSummary(getRingtoneName(context, callRingtoneUri))
        val messageRingtoneUri = getMessageRingtoneUri(context, (appPreferences))
        binding.settingsMessageSound.setSummary(getRingtoneName(context, messageRingtoneUri))

        setupProxyTypeSettings()
        setupProxyCredentialSettings()

        if (currentUser != null) {
            binding.baseUrlText.text = Uri.parse(currentUser!!.baseUrl).host
            setupServerAgeWarning()
            if (currentUser!!.displayName != null) {
                binding.displayNameText.text = currentUser!!.displayName
            }
            DisplayUtils.loadAvatarImage(currentUser, binding.avatarImage, false)

            setupProfileQueryDisposable()

            binding.settingsRemoveAccount.addPreferenceClickListener {
                showRemoveAccountWarning()
            }
        }
        setupMessageView()

        binding.avatarContainer.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        themeCategories()
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
            onBackPressed()
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
        if (CapabilitiesUtilNew.isPhoneBookIntegrationAvailable(currentUser!!)) {
            binding.settingsPhoneBookIntegration.visibility = View.VISIBLE
        } else {
            binding.settingsPhoneBookIntegration.visibility = View.GONE
        }
    }

    private fun setupSoundSettings() {
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
            Log.e(TAG, "setupSoundSettings currently not supported for versions < Build.VERSION_CODES.O")
        }
    }

    private fun setupSourceCodeUrl() {
        if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_source_code_url))) {
            binding.settingsSourceCode.addPreferenceClickListener {
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

    private fun setupPrivacyUrl() {
        if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_privacy_url))) {
            binding.settingsPrivacy.addPreferenceClickListener {
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
            binding.settingsLicence.addPreferenceClickListener {
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

    private fun setupSettingsScreen() {
        val listWithIntFields: MutableList<String> = ArrayList()
        listWithIntFields.add("proxy_port")
        binding.settingsScreen.setUserInputModule(MagicUserInputModule(this, listWithIntFields))
        binding.settingsScreen.setVisibilityController(
            R.id.settings_proxy_use_credentials,
            Arrays.asList(R.id.settings_proxy_username_edit, R.id.settings_proxy_password_edit),
            true
        )
    }

    private fun setupClientCertView() {
        var host: String? = null
        var port = -1
        val uri: URI
        try {
            uri = URI(currentUser!!.baseUrl)
            host = uri.host
            port = uri.port
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to create uri")
        }

        binding.settingsClientCert.addPreferenceClickListener {
            KeyChain.choosePrivateKeyAlias(
                this,
                { alias: String? ->
                    var finalAlias: String? = alias

                    runOnUiThread {
                        if (finalAlias != null) {
                            binding.settingsClientCert.setTitle(R.string.nc_client_cert_change)
                        } else {
                            binding.settingsClientCert.setTitle(R.string.nc_client_cert_setup)
                        }
                    }

                    if (finalAlias == null) {
                        finalAlias = ""
                    }

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

    private fun registerChangeListeners() {
        appPreferences.registerProxyTypeListener(ProxyTypeChangeListener().also { proxyTypeChangeListener = it })
        appPreferences.registerProxyCredentialsListener(
            ProxyCredentialsChangeListener().also {
                proxyCredentialsChangeListener = it
            }
        )
        appPreferences.registerScreenSecurityListener(
            ScreenSecurityChangeListener().also {
                screenSecurityChangeListener = it
            }
        )
        appPreferences.registerScreenLockListener(ScreenLockListener().also { screenLockChangeListener = it })
        appPreferences.registerScreenLockTimeoutListener(
            ScreenLockTimeoutListener().also {
                screenLockTimeoutChangeListener = it
            }
        )
        appPreferences.registerThemeChangeListener(ThemeChangeListener().also { themeChangeListener = it })
        appPreferences.registerPhoneBookIntegrationChangeListener(
            PhoneBookIntegrationChangeListener(this).also {
                phoneBookIntegrationChangeListener = it
            }
        )
        appPreferences.registerReadPrivacyChangeListener(
            ReadPrivacyChangeListener().also {
                readPrivacyChangeListener = it
            }
        )
        appPreferences.registerTypingStatusChangeListener(
            TypingStatusChangeListener().also {
                typingStatusChangeListener = it
            }
        )
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

    private fun removeCurrentAccount() {
        val otherUserExists = userManager.scheduleUserForDeletionWithId(currentUser!!.id!!).blockingGet()
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance().enqueue(accountRemovalWork)
        if (otherUserExists) {
            // TODO: find better solution once Conductor is removed
            finish()
            startActivity(intent)
        } else if (!otherUserExists) {
            Log.d(TAG, "No other users found. AccountRemovalWorker will restart the app.")
        }
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
                settingsScreenLock,
                settingsScreenSecurity,
                settingsIncognitoKeyboard,
                settingsPhoneBookIntegration,
                settingsReadPrivacy,
                settingsTypingStatus,
                settingsProxyUseCredentials
            ).forEach(viewThemeUtils.talk::colorSwitchPreference)
        }
    }

    private fun themeCategories() {
        binding.run {
            listOf(
                settingsNotificationsCategory,
                settingsAboutCategory,
                settingsAdvancedCategory,
                settingsAppearanceCategory,
                settingsPrivacyCategory
            ).forEach(viewThemeUtils.talk::colorPreferenceCategory)
        }
    }

    private fun setupProxyTypeSettings() {
        if (("No proxy" == appPreferences.proxyType) || appPreferences.proxyType == null) {
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
                        binding.messageView.visibility = View.VISIBLE
                    }
                }

                ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK -> {
                    binding.messageText.let {
                        it.setTextColor(resources!!.getColor(R.color.nc_darkRed, null))
                        it.text = resources!!.getString(R.string.nc_settings_wrong_account)
                        binding.messageView.visibility = View.VISIBLE
                        viewThemeUtils.platform.colorTextView(it, ColorRole.PRIMARY)
                        it.text = resources!!.getString(R.string.nc_Server_account_imported)
                        binding.messageView.visibility = View.VISIBLE
                    }
                }

                ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED -> {
                    binding.messageText.let {
                        viewThemeUtils.platform.colorTextView(it, ColorRole.PRIMARY)
                        it.text = resources!!.getString(R.string.nc_Server_account_imported)
                        binding.messageView.visibility = View.VISIBLE
                    }
                }

                ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT -> {
                    binding.messageText.let {
                        it.setTextColor(resources!!.getColor(R.color.nc_darkRed, null))
                        it.text = resources!!.getString(R.string.nc_server_failed_to_import_account)
                        binding.messageView.visibility = View.VISIBLE
                    }
                }

                else -> binding.messageView.visibility = View.GONE
            }
            ApplicationWideMessageHolder.getInstance().messageType = null
            binding.messageView.animate()
                ?.translationY(0f)
                ?.alpha(0.0f)
                ?.setDuration(DURATION)
                ?.setStartDelay(START_DELAY)
                ?.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        binding.messageView.visibility = View.GONE
                    }
                })
        } else {
            binding.messageView.visibility = View.GONE
        }
    }

    private fun setupProfileQueryDisposable() {
        profileQueryDisposable = ncApi.getUserProfile(
            credentials,
            ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl)
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
                        binding.displayNameText.text = currentUser!!.displayName
                    }
                },
                { dispose(profileQueryDisposable) },
                { dispose(profileQueryDisposable) }
            )
    }

    private fun setupServerAgeWarning() {
        when {
            CapabilitiesUtilNew.isServerEOL(currentUser!!) -> {
                binding.serverAgeWarningText.setTextColor(ContextCompat.getColor((context), R.color.nc_darkRed))
                binding.serverAgeWarningText.setText(R.string.nc_settings_server_eol)
                binding.serverAgeWarningIcon.setColorFilter(
                    ContextCompat.getColor((context), R.color.nc_darkRed),
                    PorterDuff.Mode.SRC_IN
                )
            }
            CapabilitiesUtilNew.isServerAlmostEOL(currentUser!!) -> {
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
        (binding.settingsScreenSecurity.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
            appPreferences.isScreenSecured

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (binding.settingsIncognitoKeyboard.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                appPreferences.isKeyboardIncognito
        }

        (binding.settingsIncognitoKeyboard.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
            appPreferences.isKeyboardIncognito

        if (CapabilitiesUtilNew.isReadStatusAvailable(currentUser!!)) {
            (binding.settingsReadPrivacy.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                !CapabilitiesUtilNew.isReadStatusPrivate(currentUser!!)
        } else {
            binding.settingsReadPrivacy.visibility = View.GONE
        }

        if (CapabilitiesUtilNew.isTypingStatusAvailable(currentUser!!)) {
            (binding.settingsTypingStatus.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                !CapabilitiesUtilNew.isTypingStatusPrivate(currentUser!!)
        } else {
            binding.settingsTypingStatus.visibility = View.GONE
        }

        (binding.settingsPhoneBookIntegration.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
            appPreferences.isPhoneBookIntegrationEnabled
    }

    private fun setupScreenLockSetting() {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure) {
            binding.settingsScreenLock.isEnabled = true
            binding.settingsScreenLockTimeout.isEnabled = true
            (binding.settingsScreenLock.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                appPreferences.isScreenLocked
            binding.settingsScreenLockTimeout.isEnabled = appPreferences.isScreenLocked
            if (appPreferences.isScreenLocked) {
                binding.settingsScreenLockTimeout.alpha = ENABLED_ALPHA
            } else {
                binding.settingsScreenLockTimeout.alpha = DISABLED_ALPHA
            }
            binding.settingsScreenLock.alpha = ENABLED_ALPHA
        } else {
            binding.settingsScreenLock.isEnabled = false
            binding.settingsScreenLockTimeout.isEnabled = false
            appPreferences.removeScreenLock()
            appPreferences.removeScreenLockTimeout()
            (binding.settingsScreenLock.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked = false
            binding.settingsScreenLock.alpha = DISABLED_ALPHA
            binding.settingsScreenLockTimeout.alpha = DISABLED_ALPHA
        }
    }

    public override fun onDestroy() {
        appPreferences.unregisterProxyTypeListener(proxyTypeChangeListener)
        appPreferences.unregisterProxyCredentialsListener(proxyCredentialsChangeListener)
        appPreferences.unregisterScreenSecurityListener(screenSecurityChangeListener)
        appPreferences.unregisterScreenLockListener(screenLockChangeListener)
        appPreferences.unregisterScreenLockTimeoutListener(screenLockTimeoutChangeListener)
        appPreferences.unregisterThemeChangeListener(themeChangeListener)
        appPreferences.unregisterReadPrivacyChangeListener(readPrivacyChangeListener)
        appPreferences.unregisterTypingStatusChangeListener(typingStatusChangeListener)
        appPreferences.unregisterPhoneBookIntegrationChangeListener(phoneBookIntegrationChangeListener)

        super.onDestroy()
    }

    private fun hideProxySettings() {
        appPreferences.removeProxyHost()
        appPreferences.removeProxyPort()
        appPreferences.removeProxyCredentials()
        appPreferences.removeProxyUsername()
        appPreferences.removeProxyPassword()
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_host_edit)?.visibility = View.GONE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_port_edit)?.visibility = View.GONE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_use_credentials)?.visibility =
            View.GONE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_username_edit)?.visibility = View.GONE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_password_edit)?.visibility = View.GONE
    }

    private fun showProxySettings() {
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_host_edit)?.visibility =
            View.VISIBLE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_port_edit)?.visibility =
            View.VISIBLE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_use_credentials)?.visibility =
            View.VISIBLE
    }

    private fun showProxyCredentials() {
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_username_edit)?.visibility =
            View.VISIBLE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_password_edit)?.visibility =
            View.VISIBLE
    }

    private fun hideProxyCredentials() {
        appPreferences.removeProxyUsername()
        appPreferences.removeProxyPassword()
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_username_edit)?.visibility = View.GONE
        binding.settingsScreen.findViewById<View>(R.id.settings_proxy_password_edit)?.visibility = View.GONE
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
        if (requestCode == ContactAddressBookWorker.REQUEST_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            WorkManager
                .getInstance()
                .enqueue(OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java).build())
            checkForPhoneNumber()
        } else {
            appPreferences.setPhoneBookIntegration(false)
            (binding.settingsPhoneBookIntegration.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                appPreferences.isPhoneBookIntegrationEnabled
            Toast.makeText(
                context,
                context.resources.getString(R.string.no_phone_book_integration_due_to_permissions),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private inner class ScreenLockTimeoutListener : OnPreferenceValueChangedListener<String?> {
        override fun onChanged(newValue: String?) {
            SecurityUtils.createKey(appPreferences.screenLockTimeout)
        }
    }

    private inner class ScreenLockListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            binding.settingsScreenLockTimeout.isEnabled = newValue
            if (newValue) {
                binding.settingsScreenLockTimeout.alpha = ENABLED_ALPHA
            } else {
                binding.settingsScreenLockTimeout.alpha = DISABLED_ALPHA
            }
        }
    }

    private inner class ScreenSecurityChangeListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            if (newValue) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    private inner class ProxyCredentialsChangeListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            if (newValue) {
                showProxyCredentials()
            } else {
                hideProxyCredentials()
            }
        }
    }

    private inner class ProxyTypeChangeListener : OnPreferenceValueChangedListener<String> {
        @Suppress("Detekt.TooGenericExceptionCaught")
        override fun onChanged(newValue: String) {
            if (("No proxy" == newValue)) {
                hideProxySettings()
            } else {
                when (newValue) {
                    "HTTP" ->
                        binding.settingsProxyPortEdit.value = "3128"
                    "DIRECT" ->
                        binding.settingsProxyPortEdit.value = "8080"
                    "SOCKS" ->
                        binding.settingsProxyPortEdit.value = "1080"
                    else -> {
                    }
                }
                showProxySettings()
            }
        }
    }

    private inner class ThemeChangeListener : OnPreferenceValueChangedListener<String?> {
        override fun onChanged(newValue: String?) {
            setAppTheme((newValue)!!)
        }
    }

    private inner class PhoneBookIntegrationChangeListener(private val activity: Activity) :
        OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(isEnabled: Boolean) {
            if (isEnabled) {
                if (checkPermission(activity, (context))) {
                    checkForPhoneNumber()
                }
            } else {
                deleteAll()
            }
        }
    }

    private fun checkForPhoneNumber() {
        ncApi.getUserData(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl)
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
        val phoneNumberLayoutWrapper = LinearLayout(context)
        phoneNumberLayoutWrapper.orientation = LinearLayout.VERTICAL
        phoneNumberLayoutWrapper.setPadding(PHONE_NUMBER_SIDE_PADDING, 0, PHONE_NUMBER_SIDE_PADDING, 0)
        val phoneNumberInputLayout = TextInputLayout(context)
        val phoneNumberField = EditText(context)
        phoneNumberInputLayout.setHelperTextColor(
            ColorStateList.valueOf(resources!!.getColor(R.color.nc_darkRed, null))
        )
        phoneNumberField.inputType = InputType.TYPE_CLASS_PHONE
        phoneNumberField.setText("+")
        phoneNumberField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                // unused atm
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // unused atm
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                phoneNumberInputLayout.helperText = ""
            }
        })
        phoneNumberInputLayout.addView(phoneNumberField)
        phoneNumberLayoutWrapper.addView(phoneNumberInputLayout)
        val dialogBuilder = MaterialAlertDialogBuilder(phoneNumberInputLayout.context)
            .setTitle(R.string.nc_settings_phone_book_integration_phone_number_dialog_title)
            .setMessage(R.string.nc_settings_phone_book_integration_phone_number_dialog_description)
            .setView(phoneNumberLayoutWrapper)
            .setPositiveButton(context.resources.getString(R.string.nc_common_set), null)
            .setNegativeButton(context.resources.getString(R.string.nc_common_skip), null)

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(phoneNumberInputLayout.context, dialogBuilder)

        val dialog = dialogBuilder.create()
        dialog.setOnShowListener(object : OnShowListener {
            override fun onShow(dialogInterface: DialogInterface) {
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(view: View) {
                        setPhoneNumber(phoneNumberInputLayout, dialog)
                    }
                })
            }
        })

        dialog.show()

        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun setPhoneNumber(textInputLayout: TextInputLayout, dialog: AlertDialog) {
        val phoneNumber = textInputLayout.editText!!.text.toString()
        ncApi.setUserData(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForUserData(currentUser!!.baseUrl, currentUser!!.userId),
            "phone",
            phoneNumber
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    val statusCode = genericOverall.ocs?.meta?.statusCode
                    if (statusCode == HTTP_CODE) {
                        dialog.dismiss()
                        Toast.makeText(
                            context,
                            context.resources.getString(
                                R.string.nc_settings_phone_book_integration_phone_number_dialog_success
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        textInputLayout.helperText = context.resources.getString(
                            R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid
                        )
                        Log.d(TAG, "failed to set phoneNumber. statusCode=$statusCode")
                    }
                }

                override fun onError(e: Throwable) {
                    textInputLayout.helperText = context.resources.getString(
                        R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid
                    )
                    Log.e(TAG, "setPhoneNumber error", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private inner class ReadPrivacyChangeListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            val booleanValue = if (newValue) "0" else "1"
            val json = "{\"key\": \"read_status_privacy\", \"value\" : $booleanValue}"
            ncApi.setReadStatusPrivacy(
                ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                ApiUtils.getUrlForUserSettings(currentUser!!.baseUrl),
                RequestBody.create("application/json".toMediaTypeOrNull(), json)
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
                        appPreferences.setReadPrivacy(!newValue)
                        (binding.settingsReadPrivacy.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                            !newValue
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    private inner class TypingStatusChangeListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            val booleanValue = if (newValue) "0" else "1"
            val json = "{\"key\": \"typing_privacy\", \"value\" : $booleanValue}"
            ncApi.setTypingStatusPrivacy(
                ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                ApiUtils.getUrlForUserSettings(currentUser!!.baseUrl),
                RequestBody.create("application/json".toMediaTypeOrNull(), json)
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
                        appPreferences.setTypingStatus(!newValue)
                        (binding.settingsTypingStatus.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                            !newValue
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    companion object {
        private const val TAG = "SettingsController"
        private const val DURATION: Long = 2500
        private const val START_DELAY: Long = 5000
        private const val DISABLED_ALPHA: Float = 0.38f
        private const val ENABLED_ALPHA: Float = 1.0f
        private const val HTTP_CODE: Int = 200
        private const val PHONE_NUMBER_SIDE_PADDING: Int = 50
    }
}
