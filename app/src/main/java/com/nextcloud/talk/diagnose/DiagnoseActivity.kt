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
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.bold
import androidx.core.view.updateLayoutParams
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.databinding.ActivityDiagnoseBinding
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
    private lateinit var binding: ActivityDiagnoseBinding

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    private var isGooglePlayServicesAvailable: Boolean = false

    private val markdownText = SpannableStringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityDiagnoseBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()

        isGooglePlayServicesAvailable = ClosedInterfaceImpl().isGooglePlayServicesAvailable

        markdownText.clear()
        setupMetaValues()
        setupPhoneValues()
        setupAppValues()
        setupAccountValues()

        createLayoutFromMarkdown()
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.settingsToolbar)
        binding.settingsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent, null)))
        supportActionBar?.title = context.getString(R.string.nc_settings_diagnose_title)
        viewThemeUtils.material.themeToolbar(binding.settingsToolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_diagnose, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.create_issue).isVisible = BrandingUtils.isOriginalNextcloudClient(applicationContext)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.copy -> {
                copyToClipboard(markdownText.toString())
                true
            }

            R.id.share -> {
                shareToOtherApps(markdownText.toString())
                true
            }

            R.id.send_mail -> {
                composeEmail(markdownText.toString())
                true
            }

            R.id.create_issue -> {
                createGithubIssue(markdownText.toString())
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
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

            data = Uri.parse("mailto:")
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
                Uri.parse(resources!!.getString(R.string.nc_talk_android_issues_url))
            )
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
        addKey(context.resources.getString(R.string.nc_diagnose_meta_system_report_date))
        addValue(DisplayUtils.unixTimeToHumanReadable(System.currentTimeMillis()))
    }

    private fun setupPhoneValues() {
        addHeadline(context.resources.getString(R.string.nc_diagnose_phone_category_title))

        addKey(context.resources.getString(R.string.nc_diagnose_device_name_title))
        addValue(getDeviceName())

        addKey(context.resources.getString(R.string.nc_diagnose_android_version_title))
        addValue(Build.VERSION.SDK_INT.toString())

        if (isGooglePlayServicesAvailable) {
            addKey(context.resources.getString(R.string.nc_diagnose_gplay_available_title))
            addValue(context.resources.getString(R.string.nc_diagnose_gplay_available_yes))
        } else {
            addKey(context.resources.getString(R.string.nc_diagnose_gplay_available_title))
            addValue(context.resources.getString(R.string.nc_diagnose_gplay_available_no))
        }
    }

    @SuppressLint("SetTextI18n")
    @Suppress("MagicNumber")
    private fun setupAppValues() {
        addHeadline(context.resources.getString(R.string.nc_diagnose_app_category_title))

        addKey(context.resources.getString(R.string.nc_diagnose_app_name_title))
        addValue(context.resources.getString(R.string.nc_app_product_name))

        addKey(context.resources.getString(R.string.nc_diagnose_app_version_title))
        addValue(String.format("v" + BuildConfig.VERSION_NAME))

        addKey(context.resources.getString(R.string.nc_diagnose_flavor))
        addValue(BuildConfig.FLAVOR)

        if (isGooglePlayServicesAvailable) {
            addKey(context.resources.getString(R.string.nc_diagnose_battery_optimization_title))

            if (PowerManagerUtils().isIgnoringBatteryOptimizations()) {
                addValue(context.resources.getString(R.string.nc_diagnose_battery_optimization_ignored))
            } else {
                addValue(context.resources.getString(R.string.nc_diagnose_battery_optimization_not_ignored))
            }

            // handle notification permission on API level >= 33
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addKey(context.resources.getString(R.string.nc_diagnose_notification_permission))
                if (platformPermissionUtil.isPostNotificationsPermissionGranted()) {
                    addValue(context.resources.getString(R.string.nc_settings_notifications_granted))
                } else {
                    addValue(context.resources.getString(R.string.nc_settings_notifications_declined))
                }
            }

            addKey(context.resources.getString(R.string.nc_diagnose_notification_calls_channel_permission))
            addValue(
                translateBoolean(
                    NotificationUtils.isCallsNotificationChannelEnabled(this)
                )
            )

            addKey(context.resources.getString(R.string.nc_diagnose_notification_messages_channel_permission))
            addValue(
                translateBoolean(
                    NotificationUtils.isMessagesNotificationChannelEnabled(this)
                )
            )

            addKey(context.resources.getString(R.string.nc_diagnose_firebase_push_token_title))
            if (appPreferences.pushToken.isNullOrEmpty()) {
                addValue(context.resources.getString(R.string.nc_diagnose_firebase_push_token_missing))
            } else {
                addValue("${appPreferences.pushToken.substring(0, 5)}...")
            }

            addKey(context.resources.getString(R.string.nc_diagnose_firebase_push_token_latest_generated))
            if (appPreferences.pushTokenLatestGeneration != null && appPreferences.pushTokenLatestGeneration != 0L) {
                addValue(
                    DisplayUtils.unixTimeToHumanReadable(
                        appPreferences
                            .pushTokenLatestGeneration
                    )
                )
            } else {
                addValue(context.resources.getString(R.string.nc_common_unknown))
            }

            addKey(context.resources.getString(R.string.nc_diagnose_firebase_push_token_latest_fetch))
            if (appPreferences.pushTokenLatestFetch != null && appPreferences.pushTokenLatestFetch != 0L) {
                addValue(DisplayUtils.unixTimeToHumanReadable(appPreferences.pushTokenLatestFetch))
            } else {
                addValue(context.resources.getString(R.string.nc_common_unknown))
            }
        }

        addKey(context.resources.getString(R.string.nc_diagnose_app_users_amount))
        addValue(userManager.users.blockingGet().size.toString())
    }

    private fun setupAccountValues() {
        addHeadline(context.resources.getString(R.string.nc_diagnose_account_category_title))

        addKey(context.resources.getString(R.string.nc_diagnose_account_server))
        addValue(userManager.currentUser.blockingGet().baseUrl!!)

        addKey(context.resources.getString(R.string.nc_diagnose_account_user_name))
        addValue(userManager.currentUser.blockingGet().displayName!!)

        addKey(context.resources.getString(R.string.nc_diagnose_account_user_status_enabled))
        addValue(
            translateBoolean(
                (userManager.currentUser.blockingGet().capabilities?.userStatusCapability?.enabled)
            )
        )

        addKey(context.resources.getString(R.string.nc_diagnose_account_server_notification_app))
        addValue(
            translateBoolean(
                userManager.currentUser.blockingGet().capabilities?.notificationsCapability?.features?.isNotEmpty()
            )
        )

        if (isGooglePlayServicesAvailable) {
            setupPushRegistrationDiagnose()
        }

        addKey(context.resources.getString(R.string.nc_diagnose_server_version))
        addValue(userManager.currentUser.blockingGet().serverVersion?.versionString!!)

        addKey(context.resources.getString(R.string.nc_diagnose_server_talk_version))
        addValue(userManager.currentUser.blockingGet().capabilities?.spreedCapability?.version!!)

        addKey(context.resources.getString(R.string.nc_diagnose_signaling_mode_title))

        if (userManager.currentUser.blockingGet().externalSignalingServer?.externalSignalingServer?.isNotEmpty()
            == true
        ) {
            addValue(context.resources.getString(R.string.nc_diagnose_signaling_mode_extern))
        } else {
            addValue(context.resources.getString(R.string.nc_diagnose_signaling_mode_intern))
        }
    }

    private fun setupPushRegistrationDiagnose() {
        val accountId = UserIdUtils.getIdForUser(userManager.currentUser.blockingGet())

        val latestPushRegistrationAtServer = arbitraryStorageManager.getStorageSetting(
            accountId,
            LATEST_PUSH_REGISTRATION_AT_SERVER,
            ""
        ).blockingGet()?.value

        addKey(context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_server))
        if (latestPushRegistrationAtServer.isNullOrEmpty()) {
            addValue(context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_server_fail))
        } else {
            addValue(DisplayUtils.unixTimeToHumanReadable(latestPushRegistrationAtServer.toLong()))
        }

        val latestPushRegistrationAtPushProxy = arbitraryStorageManager.getStorageSetting(
            accountId,
            LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY,
            ""
        ).blockingGet()?.value

        addKey(context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_push_proxy))
        if (latestPushRegistrationAtPushProxy.isNullOrEmpty()) {
            addValue(context.resources.getString(R.string.nc_diagnose_latest_push_registration_at_push_proxy_fail))
        } else {
            addValue(DisplayUtils.unixTimeToHumanReadable(latestPushRegistrationAtPushProxy.toLong()))
        }
    }

    private fun getDeviceName(): String =
        if (MODEL.startsWith(MANUFACTURER, ignoreCase = true)) {
            MODEL
        } else {
            "$MANUFACTURER $MODEL"
        }

    private fun translateBoolean(answer: Boolean?): String {
        return when (answer) {
            null -> context.resources.getString(R.string.nc_common_unknown)
            true -> context.resources.getString(R.string.nc_yes)
            else -> context.resources.getString(R.string.nc_no)
        }
    }

    @Suppress("MagicNumber")
    private fun createLayoutFromMarkdown() {
        val standardMargin = 16
        val halfMargin = 8
        val standardPadding = 16

        binding.diagnoseContentWrapper.removeAllViews()

        markdownText.lines().forEach {
            if (it.startsWith(MARKDOWN_HEADLINE)) {
                val headline = TextView(context, null, 0)
                headline.textSize = 2.0f
                headline.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.headline_text_size)
                )
                headline.setTypeface(null, Typeface.BOLD)
                headline.text = it.removeRange(0, 4)

                binding.diagnoseContentWrapper.addView(headline)

                headline.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(0, standardMargin, 0, standardMargin)
                }
                headline.setPadding(0, standardPadding, 0, standardPadding)

                viewThemeUtils.platform.colorTextView(headline)
            } else if (it.startsWith(MARKDOWN_BOLD)) {
                val key = TextView(context, null, 0)
                key.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
                key.setTypeface(null, Typeface.BOLD)
                key.text = it.replace(MARKDOWN_BOLD, "")

                binding.diagnoseContentWrapper.addView(key)

                key.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(0, 0, 0, halfMargin)
                }
            } else if (it.isNotEmpty()) {
                val value = TextView(context, null, 0)
                value.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
                value.text = it

                binding.diagnoseContentWrapper.addView(value)

                value.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(0, 0, 0, standardMargin)
                }
                value.setPadding(0, 0, 0, standardPadding)
            }
        }
    }

    private fun addHeadline(text: String) {
        markdownText.append("$MARKDOWN_HEADLINE $text")
        markdownText.append("\n\n")
    }

    private fun addKey(text: String) {
        markdownText.bold { append("$MARKDOWN_BOLD$text$MARKDOWN_BOLD") }
        markdownText.append("\n\n")
    }

    private fun addValue(text: String) {
        markdownText.append(text)
        markdownText.append("\n\n")
    }

    companion object {
        val TAG = DiagnoseActivity::class.java.simpleName
        private const val MARKDOWN_HEADLINE = "###"
        private const val MARKDOWN_BOLD = "**"
        private const val ORIGINAL_NEXTCLOUD_TALK_APPLICATION_ID = "com.nextcloud.talk2"
    }
}
