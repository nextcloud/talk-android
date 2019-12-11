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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.security.KeyChain
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Checkable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.emoji.widget.EmojiTextView
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.OnClick
import coil.api.load
import coil.transform.CircleCropTransformation
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.newarch.utils.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DoNotDisturbUtils
import com.nextcloud.talk.utils.LoggingUtils
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.MagicUserInputModule
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.ObservableSubscribeProxy
import com.yarolegovich.lovelydialog.LovelySaveStateHandler
import com.yarolegovich.lovelydialog.LovelyStandardDialog
import com.yarolegovich.mp.MaterialChoicePreference
import com.yarolegovich.mp.MaterialEditTextPreference
import com.yarolegovich.mp.MaterialPreferenceCategory
import com.yarolegovich.mp.MaterialPreferenceScreen
import com.yarolegovich.mp.MaterialStandardPreference
import com.yarolegovich.mp.MaterialSwitchPreference
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener
import org.koin.android.ext.android.inject
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale
import java.util.Objects
import javax.inject.Inject

class SettingsController : BaseController() {
    @JvmField
    @BindView(R.id.settings_screen)
    var settingsScreen: MaterialPreferenceScreen? = null
    @JvmField
    @BindView(R.id.settings_proxy_choice)
    var proxyChoice: MaterialChoicePreference? = null
    @JvmField
    @BindView(R.id.settings_proxy_port_edit)
    var proxyPortEditText: MaterialEditTextPreference? = null
    @JvmField
    @BindView(R.id.settings_licence)
    var licenceButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_privacy)
    var privacyButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_source_code)
    var sourceCodeButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_version)
    var versionInfo: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.avatar_image)
    var avatarImageView: ImageView? = null
    @JvmField
    @BindView(R.id.display_name_text)
    var displayNameTextView: EmojiTextView? = null
    @JvmField
    @BindView(R.id.base_url_text)
    var baseUrlTextView: TextView? = null
    @JvmField
    @BindView(R.id.settings_call_sound)
    var settingsCallSound: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_message_sound)
    var settingsMessageSound: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_remove_account)
    var removeAccountButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_switch)
    var switchAccountButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_reauthorize)
    var reauthorizeButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_add_account)
    var addAccountButton: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.message_view)
    var messageView: MaterialPreferenceCategory? = null
    @JvmField
    @BindView(R.id.settings_client_cert)
    var certificateSetup: MaterialStandardPreference? = null
    @JvmField
    @BindView(R.id.settings_always_vibrate)
    var shouldVibrateSwitchPreference: MaterialSwitchPreference? = null
    @JvmField
    @BindView(R.id.settings_incognito_keyboard)
    var incognitoKeyboardSwitchPreference: MaterialSwitchPreference? = null
    @JvmField
    @BindView(R.id.settings_screen_security)
    var screenSecuritySwitchPreference: MaterialSwitchPreference? = null
    @JvmField
    @BindView(R.id.settings_link_previews)
    var linkPreviewsSwitchPreference: MaterialSwitchPreference? = null
    @JvmField
    @BindView(R.id.settings_screen_lock)
    var screenLockSwitchPreference: MaterialSwitchPreference? = null
    @JvmField
    @BindView(R.id.settings_screen_lock_timeout)
    var screenLockTimeoutChoicePreference: MaterialChoicePreference? = null

    @JvmField
    @BindView(R.id.message_text)
    var messageText: TextView? = null
    @JvmField
    val ncApi: NcApi by inject()
    val usersRepository: UsersRepository by inject()
    private var saveStateHandler: LovelySaveStateHandler? = null
    private var currentUser: UserNgEntity? = null
    private var credentials: String? = null
    lateinit var proxyTypeChangeListener: OnPreferenceValueChangedListener<String>
    lateinit var proxyCredentialsChangeListener: OnPreferenceValueChangedListener<Boolean>
    lateinit var screenSecurityChangeListener: OnPreferenceValueChangedListener<Boolean>
    lateinit var screenLockChangeListener: OnPreferenceValueChangedListener<Boolean>
    lateinit var screenLockTimeoutChangeListener: OnPreferenceValueChangedListener<String>
    lateinit var themeChangeListener: OnPreferenceValueChangedListener<String>

    override fun inflateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        return inflater.inflate(R.layout.controller_settings, container, false)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setHasOptionsMenu(true)

        ViewCompat.setTransitionName(avatarImageView!!, "userAvatar.transitionTag")

        if (saveStateHandler == null) {
            saveStateHandler = LovelySaveStateHandler()
        }

        proxyTypeChangeListener = ProxyTypeChangeListener()
        proxyCredentialsChangeListener = ProxyCredentialsChangeListener()
        screenSecurityChangeListener = ScreenSecurityChangeListener()
        screenLockChangeListener = ScreenLockListener()
        screenLockTimeoutChangeListener = ScreenLockTimeoutListener()
        themeChangeListener = ThemeChangeListener()
    }

    private fun showLovelyDialog(
            dialogId: Int,
            savedInstanceState: Bundle?
    ) {
        when (dialogId) {
            ID_REMOVE_ACCOUNT_WARNING_DIALOG -> showRemoveAccountWarning(savedInstanceState)
            else -> {
            }
        }
    }

    @OnClick(R.id.settings_version)
    fun sendLogs() {
        if (resources!!.getBoolean(R.bool.nc_is_debug)) {
            LoggingUtils.sendMailWithAttachment(context!!)
        }
    }

    override fun onSaveViewState(
            view: View,
            outState: Bundle
    ) {
        saveStateHandler!!.saveInstanceState(outState)
        super.onSaveViewState(view, outState)
    }

    override fun onRestoreViewState(
            view: View,
            savedViewState: Bundle
    ) {
        super.onRestoreViewState(view, savedViewState)
        if (LovelySaveStateHandler.wasDialogOnScreen(savedViewState)) {
            //Dialog won't be restarted automatically, so we need to call this method.
            //Each dialog knows how to restore its viewState
            showLovelyDialog(LovelySaveStateHandler.getSavedDialogId(savedViewState), savedViewState)
        }
    }

    private fun showRemoveAccountWarning(savedInstanceState: Bundle?) {
        if (activity != null) {
            LovelyStandardDialog(activity, LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                    .setTopColorRes(R.color.nc_darkRed)
                    .setIcon(
                            DisplayUtils.getTintedDrawable(
                                    resources!!,
                                    R.drawable.ic_delete_black_24dp, R.color.bg_default
                            )
                    )
                    .setPositiveButtonColor(context.resources.getColor(R.color.nc_darkRed))
                    .setTitle(R.string.nc_settings_remove_account)
                    .setMessage(R.string.nc_settings_remove_confirmation)
                    .setPositiveButton(R.string.nc_settings_remove) { removeCurrentAccount() }
                    .setNegativeButton(R.string.nc_cancel, null)
                    .setInstanceStateHandler(ID_REMOVE_ACCOUNT_WARNING_DIALOG, saveStateHandler!!)
                    .setSavedInstanceState(savedInstanceState)
                    .show()
        }
    }

    private fun removeCurrentAccount() {
        val user = usersRepository.getActiveUser()
        user!!.status = UserStatus.PENDING_DELETE
        GlobalScope.launch {
            val job = async {
                usersRepository.updateUser(user)
                val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
                        .build()
                WorkManager.getInstance()
                        .enqueue(accountRemovalWork)
            }
            job.await()

            if (usersRepository.setAnyUserAsActive()) {
                withContext(Dispatchers.Main) {
                    onViewBound(view!!)
                    onAttach(view!!)
                }
            } else {
                router.setRoot(RouterTransaction.with(
                        ServerSelectionController()
                )
                        .pushChangeHandler(VerticalChangeHandler())
                        .popChangeHandler(VerticalChangeHandler())
                )

            }
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        if (actionBar != null) {
            actionBar!!.show()
        }

        GlobalScope.launch {
            var hasMultipleUsers: Boolean = false
            val job = async {
                currentUser = usersRepository.getActiveUser()
                hasMultipleUsers = usersRepository.getUsers().size > 0
                credentials = currentUser!!.getCredentials()
            }

            job.await()
            withContext(Dispatchers.Main) {
                appPreferences.registerProxyTypeListener(proxyTypeChangeListener)
                appPreferences.registerProxyCredentialsListener { proxyCredentialsChangeListener }
                appPreferences.registerScreenSecurityListener { screenSecurityChangeListener }
                appPreferences.registerScreenLockListener { screenLockChangeListener }
                appPreferences.registerScreenLockTimeoutListener { screenLockTimeoutChangeListener }
                appPreferences.registerThemeChangeListener { themeChangeListener }

                val listWithIntFields = ArrayList<String>()
                listWithIntFields.add("proxy_port")

                settingsScreen!!.setUserInputModule(MagicUserInputModule(activity, listWithIntFields))
                settingsScreen!!.setVisibilityController(
                        R.id.settings_proxy_use_credentials,
                        Arrays.asList(R.id.settings_proxy_username_edit, R.id.settings_proxy_password_edit),
                        true
                )

                if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_gpl3_url))) {
                    licenceButton!!.addPreferenceClickListener { view1 ->
                        val browserIntent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(resources!!.getString(R.string.nc_gpl3_url)))
                        startActivity(browserIntent)
                    }
                } else {
                    licenceButton!!.visibility = View.GONE
                }

                if (!DoNotDisturbUtils.hasVibrator()) {
                    shouldVibrateSwitchPreference!!.visibility = View.GONE
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    incognitoKeyboardSwitchPreference!!.visibility = View.GONE
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    screenLockSwitchPreference!!.visibility = View.GONE
                    screenLockTimeoutChoicePreference!!.visibility = View.GONE
                } else {
                    screenLockSwitchPreference!!.setSummary(
                            String.format(
                                    Locale.getDefault(),
                                    resources!!.getString(R.string.nc_settings_screen_lock_desc),
                                    resources!!.getString(R.string.nc_app_name)
                            )
                    )
                }

                if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_privacy_url))) {
                    privacyButton!!.addPreferenceClickListener { view12 ->
                        val browserIntent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(resources!!.getString(R.string.nc_privacy_url)))
                        startActivity(browserIntent)
                    }
                } else {
                    privacyButton!!.visibility = View.GONE
                }

                if (!TextUtils.isEmpty(resources!!.getString(R.string.nc_source_code_url))) {
                    sourceCodeButton!!.addPreferenceClickListener { view13 ->
                        val browserIntent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(resources!!.getString(R.string.nc_source_code_url)))
                        startActivity(browserIntent)
                    }
                } else {
                    sourceCodeButton!!.visibility = View.GONE
                }

                versionInfo!!.setSummary("v" + BuildConfig.VERSION_NAME)

                settingsCallSound!!.setOnClickListener { v ->
                    val bundle = Bundle()
                    bundle.putBoolean(BundleKeys.KEY_ARE_CALL_SOUNDS, true)
                    router.pushController(
                            RouterTransaction.with(RingtoneSelectionController(bundle))
                                    .pushChangeHandler(HorizontalChangeHandler())
                                    .popChangeHandler(HorizontalChangeHandler())
                    )
                }

                settingsMessageSound!!.setOnClickListener { v ->
                    val bundle = Bundle()
                    bundle.putBoolean(BundleKeys.KEY_ARE_CALL_SOUNDS, false)
                    router.pushController(
                            RouterTransaction.with(RingtoneSelectionController(bundle))
                                    .pushChangeHandler(HorizontalChangeHandler())
                                    .popChangeHandler(HorizontalChangeHandler())
                    )
                }

                addAccountButton!!.addPreferenceClickListener { view15 ->
                    router.pushController(
                            RouterTransaction.with(ServerSelectionController()).pushChangeHandler(
                                    VerticalChangeHandler()
                            )
                                    .popChangeHandler(VerticalChangeHandler())
                    )
                }

                switchAccountButton!!.addPreferenceClickListener { view16 ->
                    router.pushController(
                            RouterTransaction.with(SwitchAccountController()).pushChangeHandler(
                                    VerticalChangeHandler()
                            )
                                    .popChangeHandler(VerticalChangeHandler())
                    )
                }

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

                val finalHost = host
                val finalPort = port
                certificateSetup!!.addPreferenceClickListener { v ->
                    KeyChain.choosePrivateKeyAlias(
                            Objects.requireNonNull<Activity>(activity), { alias ->
                        activity!!.runOnUiThread {
                            if (alias != null) {
                                certificateSetup!!.setTitle(R.string.nc_client_cert_change)
                            } else {
                                certificateSetup!!.setTitle(R.string.nc_client_cert_setup)
                            }
                        }

                        var realAlias = alias
                        if (realAlias == null) {
                            realAlias = ""
                        }

                        currentUser = usersRepository.getUserWithId(currentUser!!.id!!)
                        currentUser!!.clientCertificate = realAlias
                        GlobalScope.launch {
                            usersRepository.updateUser(currentUser!!)
                        }
                    }, arrayOf("RSA", "EC"), null, finalHost, finalPort,
                            currentUser!!.clientCertificate
                    )
                }

                if (!TextUtils.isEmpty(currentUser!!.clientCertificate)) {
                    certificateSetup!!.setTitle(R.string.nc_client_cert_change)
                } else {
                    certificateSetup!!.setTitle(R.string.nc_client_cert_setup)
                }

                if (shouldVibrateSwitchPreference!!.visibility == View.VISIBLE) {
                    (shouldVibrateSwitchPreference!!.findViewById<View>(
                            R.id.mp_checkable
                    ) as Checkable).isChecked = appPreferences.shouldVibrateSetting
                }

                (screenSecuritySwitchPreference!!.findViewById<View>(
                        R.id.mp_checkable
                ) as Checkable).isChecked = appPreferences.isScreenSecured
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    (incognitoKeyboardSwitchPreference!!.findViewById<View>(
                            R.id.mp_checkable
                    ) as Checkable).isChecked =
                            appPreferences.isKeyboardIncognito
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    (incognitoKeyboardSwitchPreference!!.findViewById<View>(
                            R.id.mp_checkable
                    ) as Checkable).isChecked =
                            appPreferences.isKeyboardIncognito
                }

                (linkPreviewsSwitchPreference!!.findViewById<View>(R.id.mp_checkable) as Checkable).isChecked =
                        appPreferences.areLinkPreviewsAllowed

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

                    if (keyguardManager.isKeyguardSecure) {
                        screenLockSwitchPreference!!.isEnabled = true
                        screenLockTimeoutChoicePreference!!.isEnabled = true
                        (screenLockSwitchPreference!!.findViewById<View>(
                                R.id.mp_checkable
                        ) as Checkable).isChecked =
                                appPreferences.isScreenLocked

                        screenLockTimeoutChoicePreference!!.isEnabled = appPreferences!!.isScreenLocked

                        if (appPreferences!!.isScreenLocked) {
                            screenLockTimeoutChoicePreference!!.alpha = 1.0f
                        } else {
                            screenLockTimeoutChoicePreference!!.alpha = 0.38f
                        }

                        screenLockSwitchPreference!!.alpha = 1.0f
                    } else {
                        screenLockSwitchPreference!!.isEnabled = false
                        screenLockTimeoutChoicePreference!!.isEnabled = false
                        appPreferences.removeScreenLock()
                        appPreferences.removeScreenLockTimeout()
                        (screenLockSwitchPreference!!.findViewById<View>(
                                R.id.mp_checkable
                        ) as Checkable).isChecked =
                                false
                        screenLockSwitchPreference!!.alpha = 0.38f
                        screenLockTimeoutChoicePreference!!.alpha = 0.38f
                    }
                }

                var ringtoneName = ""
                var ringtoneSettings: RingtoneSettings
                if (!TextUtils.isEmpty(appPreferences.callRingtoneUri)) {
                    try {
                        ringtoneSettings =
                                LoganSquare.parse(appPreferences.callRingtoneUri, RingtoneSettings::class.java)
                        ringtoneName = ringtoneSettings.ringtoneName
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to parse ringtone name")
                    }

                    settingsCallSound!!.setSummary(ringtoneName)
                } else {
                    settingsCallSound!!.setSummary(R.string.nc_settings_default_ringtone)
                }

                ringtoneName = ""

                if (!TextUtils.isEmpty(appPreferences.messageRingtoneUri)) {
                    try {
                        ringtoneSettings =
                                LoganSquare.parse(appPreferences.messageRingtoneUri, RingtoneSettings::class.java)
                        ringtoneName = ringtoneSettings.ringtoneName
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to parse ringtone name")
                    }

                    settingsMessageSound!!.setSummary(ringtoneName)
                } else {
                    settingsMessageSound!!.setSummary(R.string.nc_settings_default_ringtone)
                }

                if ("No proxy" == appPreferences!!.proxyType || appPreferences!!.proxyType == null) {
                    hideProxySettings()
                } else {
                    showProxySettings()
                }

                if (appPreferences!!.proxyCredentials) {
                    showProxyCredentials()
                } else {
                    hideProxyCredentials()
                }

                if (currentUser != null) {

                    baseUrlTextView!!.text = Uri.parse(currentUser!!.baseUrl)
                            .host

                    reauthorizeButton!!.addPreferenceClickListener { view14 ->
                        router.pushController(
                                RouterTransaction.with(
                                        WebViewLoginController(currentUser!!.baseUrl, true)
                                )
                                        .pushChangeHandler(VerticalChangeHandler())
                                        .popChangeHandler(VerticalChangeHandler())
                        )
                    }

                    if (currentUser!!.displayName != null) {
                        displayNameTextView!!.text = currentUser!!.displayName
                    }

                    loadAvatarImage()

                    ncApi!!.getUserProfile(
                            credentials,
                            ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl)
                    )
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .`as`<ObservableSubscribeProxy<UserProfileOverall>>(
                                    AutoDispose.autoDisposable<UserProfileOverall>(scopeProvider)
                            )
                            .subscribe({ userProfileOverall ->

                                var displayName: String? = null
                                if (!TextUtils.isEmpty(
                                                userProfileOverall.ocs.data.displayName
                                        )
                                ) {
                                    displayName = userProfileOverall.ocs.data.displayName
                                } else if (!TextUtils.isEmpty(
                                                userProfileOverall.ocs.data.displayName
                                        )
                                ) {
                                    displayName = userProfileOverall.ocs.data.displayNameAlt
                                }

                                if (!TextUtils.isEmpty(displayName) && displayName != currentUser!!.displayName) {
                                    val user = usersRepository.getUserWithId(currentUser!!.id!!)
                                    user.displayName = displayName
                                    GlobalScope.launch {
                                        usersRepository.updateUser(user)
                                    }
                                    displayNameTextView!!.text = displayName

                                }
                            }, { throwable -> }, { Log.d(TAG, "") })

                    removeAccountButton!!.addPreferenceClickListener { view1 ->
                        showLovelyDialog(
                                ID_REMOVE_ACCOUNT_WARNING_DIALOG, null
                        )
                    }
                }

                if (!hasMultipleUsers) {
                    switchAccountButton!!.visibility = View.GONE
                }

                if (ApplicationWideMessageHolder.getInstance().messageType != null) {
                    when (ApplicationWideMessageHolder.getInstance().messageType) {
                        ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED -> {
                            messageText!!.setTextColor(resources!!.getColor(R.color.colorPrimary))
                            messageText!!.text = resources!!.getString(R.string.nc_settings_account_updated)
                            messageView!!.visibility = View.VISIBLE
                        }
                        ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK -> {
                            messageText!!.setTextColor(resources!!.getColor(R.color.nc_darkRed))
                            messageText!!.text = resources!!.getString(R.string.nc_settings_wrong_account)
                            messageView!!.visibility = View.VISIBLE
                            messageText!!.setTextColor(resources!!.getColor(R.color.colorPrimary))
                            messageText!!.text = resources!!.getString(R.string.nc_Server_account_imported)
                            messageView!!.visibility = View.VISIBLE
                        }
                        ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED -> {
                            messageText!!.setTextColor(resources!!.getColor(R.color.colorPrimary))
                            messageText!!.text = resources!!.getString(R.string.nc_Server_account_imported)
                            messageView!!.visibility = View.VISIBLE
                        }
                        ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT -> {
                            messageText!!.setTextColor(resources!!.getColor(R.color.nc_darkRed))
                            messageText!!.text = resources!!.getString(R.string.nc_server_failed_to_import_account)
                            messageView!!.visibility = View.VISIBLE
                        }
                        else -> messageView!!.visibility = View.GONE
                    }
                    ApplicationWideMessageHolder.getInstance()
                            .setMessageType(null)

                    messageView!!.animate()
                            .translationY(0f)
                            .alpha(0.0f)
                            .setDuration(2500)
                            .setStartDelay(5000)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    super.onAnimationEnd(animation)
                                    if (messageView != null) {
                                        messageView!!.visibility = View.GONE
                                    }
                                }
                            })
                } else {
                    if (messageView != null) {
                        messageView!!.visibility = View.GONE
                    } else {
                        // do nothing
                    }
                }
            }
        }


    }

    private fun loadAvatarImage() {
        val avatarId: String
        if (!TextUtils.isEmpty(currentUser!!.userId)) {
            avatarId = currentUser!!.userId
        } else {
            avatarId = currentUser!!.username
        }

        avatarImageView!!.load(
                ApiUtils.getUrlForAvatarWithName(
                        currentUser!!.baseUrl,
                        avatarId, R.dimen.avatar_size_big
                )
        ) {
            transformations(CircleCropTransformation())
        }
    }

    public override fun onDestroy() {
        appPreferences.unregisterProxyTypeListener(proxyTypeChangeListener)
        appPreferences.unregisterProxyCredentialsListener(proxyCredentialsChangeListener)
        appPreferences.unregisterScreenSecurityListener(screenSecurityChangeListener)
        appPreferences.unregisterScreenLockListener(screenLockChangeListener)
        appPreferences.unregisterScreenLockTimeoutListener(screenLockTimeoutChangeListener)
        appPreferences.unregisterThemeChangeListener(themeChangeListener)
        super.onDestroy()
    }

    private fun hideProxySettings() {
        appPreferences.removeProxyHost()
        appPreferences.removeProxyPort()
        appPreferences.removeProxyCredentials()
        appPreferences.removeProxyUsername()
        appPreferences.removeProxyPassword()
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_host_edit)
                .visibility = View.GONE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_port_edit)
                .visibility = View.GONE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_use_credentials)
                .visibility = View.GONE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_username_edit)
                .visibility = View.GONE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_password_edit)
                .visibility = View.GONE
    }

    private fun showProxySettings() {
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_host_edit)
                .visibility = View.VISIBLE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_port_edit)
                .visibility = View.VISIBLE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_use_credentials)
                .visibility = View.VISIBLE
    }

    private fun showProxyCredentials() {
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_username_edit)
                .visibility = View.VISIBLE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_password_edit)
                .visibility = View.VISIBLE
    }

    private fun hideProxyCredentials() {
        appPreferences.removeProxyUsername()
        appPreferences.removeProxyPassword()
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_username_edit)
                .visibility = View.GONE
        settingsScreen!!.findViewById<View>(R.id.settings_proxy_password_edit)
                .visibility = View.GONE
    }

    override fun getTitle(): String? {
        return resources!!.getString(R.string.nc_settings)
    }

    private inner class ScreenLockTimeoutListener : OnPreferenceValueChangedListener<String> {

        override fun onChanged(newValue: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecurityUtils.createKey(appPreferences.screenLockTimeout)
            }
        }
    }

    private inner class ScreenLockListener : OnPreferenceValueChangedListener<Boolean> {

        override fun onChanged(newValue: Boolean?) {
            screenLockTimeoutChoicePreference!!.isEnabled = newValue!!

            if (newValue) {
                screenLockTimeoutChoicePreference!!.alpha = 1.0f
            } else {
                screenLockTimeoutChoicePreference!!.alpha = 0.38f
            }
        }
    }

    private inner class ScreenSecurityChangeListener : OnPreferenceValueChangedListener<Boolean> {

        override fun onChanged(newValue: Boolean) {
            if (newValue) {
                if (activity != null) {
                    activity!!.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            } else {
                if (activity != null) {
                    activity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
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

        override fun onChanged(newValue: String) {
            if ("No proxy" == newValue) {
                hideProxySettings()
            } else {
                when (newValue) {
                    "HTTP" -> if (proxyPortEditText != null) {
                        proxyPortEditText!!.value = "3128"
                    }
                    "DIRECT" -> if (proxyPortEditText != null) {
                        proxyPortEditText!!.value = "8080"
                    }
                    "SOCKS" -> if (proxyPortEditText != null) {
                        proxyPortEditText!!.value = "1080"
                    }
                    else -> {
                    }
                }

                showProxySettings()
            }
        }
    }

    private inner class ThemeChangeListener : OnPreferenceValueChangedListener<String> {
        override fun onChanged(newValue: String) {
            NextcloudTalkApplication.setAppTheme(newValue)
        }
    }

    companion object {

        val TAG = "SettingsController"
        private val ID_REMOVE_ACCOUNT_WARNING_DIALOG = 0
    }
}
