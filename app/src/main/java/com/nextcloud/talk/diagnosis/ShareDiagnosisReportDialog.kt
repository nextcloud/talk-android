/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.diagnosis

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.errorhandling.shareLogsAndDiagnosis
import com.nextcloud.talk.logger.LogsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.BrandingUtils
import com.nextcloud.talk.utils.preferences.AppPreferences

private const val DIALOG_PADDING_H_DP = 24
private const val DIALOG_PADDING_V_DP = 16
private const val DIALOG_SPACING_DP = 8

fun showShareReportDialog(
    activity: Activity,
    userManager: UserManager,
    appPreferences: AppPreferences,
    logsRepository: LogsRepository,
    saveZipLauncher: ActivityResultLauncher<String>
) {
    val options = buildShareReportOptions(activity, userManager, appPreferences, logsRepository, saveZipLauncher)
    var dialog: AlertDialog? = null
    val view = buildShareDialogContentView(activity, options) { dialog?.dismiss() }
    dialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.nc_settings_share_report_title)
        .setView(view)
        .show()
}

private fun buildShareReportOptions(
    activity: Activity,
    userManager: UserManager,
    appPreferences: AppPreferences,
    logsRepository: LogsRepository,
    saveZipLauncher: ActivityResultLauncher<String>
): List<Pair<String, () -> Unit>> {
    val options = mutableListOf(
        activity.getString(R.string.nc_logs_share) to {
            val diagnosisText = buildDiagnosisReportText(activity, userManager, appPreferences, logsRepository)
            shareLogsAndDiagnosis(
                context = activity,
                subject = activity.getString(
                    R.string.nc_logs_share_subject,
                    activity.getString(R.string.nc_app_product_name)
                ),
                diagnosisText = diagnosisText
            )
        },
        activity.getString(R.string.nc_logs_download_zip) to {
            saveZipLauncher.launch("nc_talk_logs.zip")
        }
    )
    if (BrandingUtils.isOriginalNextcloudClient(activity.applicationContext)) {
        options.add(
            activity.getString(R.string.create_issue) to {
                val diagnosisText = buildDiagnosisReportText(activity, userManager, appPreferences, logsRepository)
                val clipboard = activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(activity.getString(R.string.nc_app_product_name), diagnosisText)
                )
                Toast.makeText(
                    activity,
                    activity.getString(R.string.nc_common_copy_success),
                    Toast.LENGTH_LONG
                ).show()
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, activity.getString(R.string.nc_talk_android_issues_url).toUri())
                )
            }
        )
    }
    return options
}

@Suppress("Detekt.LongMethod")
fun buildDiagnosisReportText(
    activity: Activity,
    userManager: UserManager,
    appPreferences: AppPreferences,
    logsRepository: LogsRepository
): String =
    buildDiagnosisElements(
        context = activity,
        userManager = userManager,
        appPreferences = appPreferences,
        logsRepository = logsRepository
    ).toMarkdown()

@Suppress("LongMethod")
private fun buildShareDialogContentView(
    activity: Activity,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
): ScrollView {
    val density = activity.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val selectableBackground = with(TypedValue()) {
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
        resourceId
    }
    val list = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(activity).apply {
                text = activity.getString(R.string.nc_logs_advanced_logging_privacy_warning)
                val h = dp(DIALOG_PADDING_H_DP)
                val v = dp(DIALOG_PADDING_V_DP)
                setPadding(h, v, h, v)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
        )
        addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ).also { it.setMargins(0, 0, 0, dp(DIALOG_SPACING_DP)) }
                setBackgroundColor(
                    MaterialColors.getColor(
                        activity,
                        com.google.android.material.R.attr.colorOutlineVariant,
                        0
                    )
                )
            }
        )
        options.forEach { (label, action) ->
            addView(
                TextView(activity).apply {
                    text = label
                    val h = dp(DIALOG_PADDING_H_DP)
                    val v = dp(DIALOG_PADDING_V_DP)
                    setPadding(h, v, h, v)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    setBackgroundResource(selectableBackground)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        onDismiss()
                        action()
                    }
                }
            )
        }
        addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(DIALOG_SPACING_DP)
                )
            }
        )
    }
    return ScrollView(activity).apply { addView(list) }
}
