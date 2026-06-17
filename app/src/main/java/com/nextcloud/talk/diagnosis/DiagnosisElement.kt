/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.diagnosis

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.PushUtils.Companion.LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY
import com.nextcloud.talk.utils.PushUtils.Companion.LATEST_PUSH_REGISTRATION_AT_SERVER
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.unifiedpush.android.connector.UnifiedPush

sealed class DiagnosisElement {
    data class DiagnosisHeadline(val headline: String) : DiagnosisElement()
    data class DiagnosisEntry(val key: String, val value: String) : DiagnosisElement()
}

fun List<DiagnosisElement>.toMarkdown(): String =
    buildString {
        this@toMarkdown.forEach { element ->
            when (element) {
                is DiagnosisElement.DiagnosisHeadline -> {
                    append("### ${element.headline}")
                    append("\n\n")
                }
                is DiagnosisElement.DiagnosisEntry -> {
                    append("**${element.key}**")
                    append("\n")
                    append(element.value)
                    append("\n\n")
                }
            }
        }
    }

private const val PUSH_TOKEN_PREFIX_END: Int = 5

@Suppress("LongMethod", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
fun buildDiagnosisElements(
    context: Context,
    userManager: UserManager,
    appPreferences: AppPreferences,
    arbitraryStorageManager: ArbitraryStorageManager? = null
): List<DiagnosisElement> {
    val data = mutableListOf<DiagnosisElement>()

    val isGooglePlayServicesAvailable = ClosedInterfaceImpl().isGooglePlayServicesAvailable
    val nUnifiedPushServices = UnifiedPushUtils.getExternalDistributors(context).size
    val offerUnifiedPush = try {
        nUnifiedPushServices > 0 && userManager.users.blockingGet().all { it.hasWebPushCapability }
    } catch (_: Exception) {
        false
    }
    val useUnifiedPush = appPreferences.useUnifiedPush
    val useEmbeddedDistrib = UnifiedPushUtils.hasEmbeddedDistributor(context) && !useUnifiedPush
    val unifiedPushService = UnifiedPush.getAckDistributor(context) ?: "N/A"

    val boolStr: (Boolean?) -> String = { b ->
        when (b) {
            true -> context.getString(R.string.nc_diagnosis_yes)
            false -> context.getString(R.string.nc_diagnosis_no)
            null -> context.getString(R.string.nc_diagnosis_unknown)
        }
    }
    fun addHeadline(text: String) {
        data.add(DiagnosisElement.DiagnosisHeadline(text))
    }
    fun addEntry(key: String, value: String) {
        data.add(DiagnosisElement.DiagnosisEntry(key, value))
    }

    // Meta
    addHeadline(context.getString(R.string.nc_diagnosis_meta_category_title))
    addEntry(
        context.getString(R.string.nc_diagnosis_meta_system_report_date),
        DisplayUtils.unixTimeToHumanReadable(System.currentTimeMillis())
    )

    // Phone
    addHeadline(context.getString(R.string.nc_diagnosis_phone_category_title))
    val deviceName = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
        Build.MODEL
    } else {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    addEntry(context.getString(R.string.nc_diagnosis_device_brand), Build.BRAND)
    addEntry(context.getString(R.string.nc_diagnosis_device_name_title), deviceName)
    addEntry(context.getString(R.string.nc_diagnosis_android_version_title), Build.VERSION.RELEASE.toString())
    addEntry(context.getString(R.string.nc_diagnosis_android_api_version_title), Build.VERSION.SDK_INT.toString())

    when (true) {
        isGooglePlayServicesAvailable -> {
            addEntry(
                key = context.resources.getString(R.string.nc_diagnosis_gplay_available_title),
                value = context.resources.getString(R.string.nc_diagnosis_gplay_available_yes)
            )
        }
        useEmbeddedDistrib -> {
            addEntry(
                key = context.resources.getString(R.string.nc_diagnosis_gplay_available_title),
                value = context.resources.getString(R.string.nc_diagnosis_gplay_available_yes_up)
            )
        }
        else -> {
            addEntry(
                key = context.resources.getString(R.string.nc_diagnosis_gplay_available_title),
                value = context.resources.getString(R.string.nc_diagnosis_gplay_available_no_short)
            )
        }
    }

    addEntry(
        key = context.getString(R.string.nc_diagnosis_unifiedpush_available_title),
        value = context.resources.getQuantityString(
            R.plurals.nc_diagnosis_unifiedpush_available_n,
            nUnifiedPushServices,
            nUnifiedPushServices
        )
    )

    // App
    addHeadline(context.getString(R.string.nc_diagnosis_app_category_title))
    addEntry(context.getString(R.string.nc_diagnosis_app_name_title), context.getString(R.string.nc_app_product_name))
    addEntry(context.getString(R.string.nc_diagnosis_app_version_title), "v${BuildConfig.VERSION_NAME}")
    addEntry(context.getString(R.string.nc_diagnosis_flavor), BuildConfig.FLAVOR)
    addEntry(context.getString(R.string.nc_diagnosis_offer_unifiedpush), boolStr(offerUnifiedPush))
    addEntry(context.getString(R.string.nc_diagnosis_use_unifiedpush), boolStr(useUnifiedPush))

    if (useUnifiedPush || useEmbeddedDistrib) {
        addPushCommonEntries(context, data, boolStr)
        addEntry(context.getString(R.string.nc_diagnosis_unifiedpush_service), unifiedPushService)
        addEntry(
            key = context.getString(R.string.nc_diagnosis_unifiedpush_latest_endpoint),
            value = appPreferences.unifiedPushLatestEndpoint
                ?.takeIf { it != 0L }
                ?.let { DisplayUtils.unixTimeToHumanReadable(it) }
                ?: context.getString(R.string.nc_common_unknown)
        )
    } else if (isGooglePlayServicesAvailable) {
        addPushCommonEntries(context, data, boolStr)
        addEntry(
            key = context.getString(R.string.nc_diagnosis_firebase_push_token_title),
            value = if (appPreferences.pushToken.isNullOrEmpty()) {
                context.getString(R.string.nc_diagnosis_firebase_push_token_missing)
            } else {
                "${appPreferences.pushToken.substring(0, PUSH_TOKEN_PREFIX_END)}..."
            }
        )
        addEntry(
            key = context.getString(R.string.nc_diagnosis_firebase_push_token_latest_generated),
            value = appPreferences.pushTokenLatestGeneration
                ?.takeIf { it != 0L }
                ?.let { DisplayUtils.unixTimeToHumanReadable(it) }
                ?: context.getString(R.string.nc_common_unknown)
        )
        addEntry(
            key = context.getString(R.string.nc_diagnosis_firebase_push_token_latest_fetch),
            value = appPreferences.pushTokenLatestFetch
                ?.takeIf { it != 0L }
                ?.let { DisplayUtils.unixTimeToHumanReadable(it) }
                ?: context.getString(R.string.nc_common_unknown)
        )
    }

    try {
        addEntry(
            context.getString(R.string.nc_diagnosis_app_users_amount),
            userManager.users.blockingGet().size.toString()
        )
    } catch (_: Exception) { }

    // Account
    try {
        val user = userManager.currentUser.blockingGet() ?: return data
        addHeadline(context.getString(R.string.nc_diagnosis_account_category_title))
        addEntry(context.getString(R.string.nc_diagnosis_account_server), user.baseUrl ?: "")
        addEntry(context.getString(R.string.nc_diagnosis_account_user_name), user.displayName ?: "")
        addEntry(
            context.getString(R.string.nc_diagnosis_account_user_status_enabled),
            boolStr(user.capabilities?.userStatusCapability?.enabled)
        )
        addEntry(
            context.getString(R.string.nc_diagnosis_account_server_notification_app),
            boolStr(user.capabilities?.notificationsCapability?.features?.isNotEmpty())
        )
        addEntry(context.getString(R.string.nc_diagnosis_server_supports_webpush), boolStr(user.hasWebPushCapability))

        if (isGooglePlayServicesAvailable && arbitraryStorageManager != null) {
            val accountId = UserIdUtils.getIdForUser(user)
            val regAtServer = arbitraryStorageManager
                .getStorageSetting(accountId, LATEST_PUSH_REGISTRATION_AT_SERVER, "")
                .blockingGet()?.value
            addEntry(
                key = context.getString(R.string.nc_diagnosis_latest_push_registration_at_server),
                value = regAtServer?.toLongOrNull()
                    ?.let { DisplayUtils.unixTimeToHumanReadable(it) }
                    ?: context.getString(R.string.nc_diagnosis_latest_push_registration_at_server_fail)
            )
            val regAtProxy = arbitraryStorageManager
                .getStorageSetting(accountId, LATEST_PUSH_REGISTRATION_AT_PUSH_PROXY, "")
                .blockingGet()?.value
            addEntry(
                key = context.getString(R.string.nc_diagnosis_latest_push_registration_at_push_proxy),
                value = regAtProxy?.toLongOrNull()
                    ?.let { DisplayUtils.unixTimeToHumanReadable(it) }
                    ?: context.getString(R.string.nc_diagnosis_latest_push_registration_at_push_proxy_fail)
            )
        }

        addEntry(context.getString(R.string.nc_diagnosis_server_version), user.serverVersion?.versionString ?: "")
        addEntry(
            context.getString(R.string.nc_diagnosis_server_talk_version),
            user.capabilities?.spreedCapability?.version ?: ""
        )
        addEntry(
            key = context.getString(R.string.nc_diagnosis_signaling_mode_title),
            value = if (user.externalSignalingServer?.externalSignalingServer?.isNotEmpty() == true) {
                context.getString(R.string.nc_diagnosis_signaling_mode_extern)
            } else {
                context.getString(R.string.nc_diagnosis_signaling_mode_intern)
            }
        )
    } catch (_: Exception) { }

    return data
}

private fun addPushCommonEntries(context: Context, data: MutableList<DiagnosisElement>, boolStr: (Boolean?) -> String) {
    data.add(
        DiagnosisElement.DiagnosisEntry(
            key = context.getString(R.string.nc_diagnosis_battery_optimization_title),
            value = if (PowerManagerUtils().isIgnoringBatteryOptimizations()) {
                context.getString(R.string.nc_diagnosis_battery_optimization_ignored)
            } else {
                context.getString(R.string.nc_diagnosis_battery_optimization_not_ignored)
            }
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        data.add(
            DiagnosisElement.DiagnosisEntry(
                key = context.getString(R.string.nc_diagnosis_notification_permission),
                value = if (granted) {
                    context.getString(R.string.nc_diagnosis_notifications_granted)
                } else {
                    context.getString(R.string.nc_diagnosis_notifications_declined)
                }
            )
        )
    }
    data.add(
        DiagnosisElement.DiagnosisEntry(
            key = context.getString(R.string.nc_diagnosis_notification_calls_channel_permission),
            value = boolStr(NotificationUtils.isCallsNotificationChannelEnabled(context))
        )
    )
    data.add(
        DiagnosisElement.DiagnosisEntry(
            key = context.getString(R.string.nc_diagnosis_notification_messages_channel_permission),
            value = boolStr(NotificationUtils.isMessagesNotificationChannelEnabled(context))
        )
    )
}
