/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.fullscreenfile

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.AccountUtils.canWeOpenFilesApp
import com.nextcloud.talk.utils.Mimetype.TEXT_PREFIX_GENERIC
import com.nextcloud.talk.utils.adjustUIForAPILevel35
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_ID
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenTextViewerActivity : AppCompatActivity() {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val fileName = intent.getStringExtra("FILE_NAME").orEmpty()
        val isMarkdown = intent.getBooleanExtra("IS_MARKDOWN", false)
        val fileId = intent.getStringExtra("FILE_ID").orEmpty()
        val link = intent.getStringExtra("LINK")
        val username = intent.getStringExtra("USERNAME").orEmpty()
        val baseUrl = intent.getStringExtra("BASE_URL").orEmpty()
        val path = applicationContext.cacheDir.absolutePath + "/" + fileName
        val text = readFile(path)

        adjustUIForAPILevel35()

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                FullScreenTextScreen(
                    title = fileName,
                    text = text,
                    isMarkdown = isMarkdown,
                    actions = FullScreenTextActions(
                        onShare = { shareFile(path) },
                        onSave = { showSaveDialog(fileName) },
                        onOpenInFilesApp = if (fileId.isNotEmpty()) {
                            { openInFilesApp(link, fileId, username, baseUrl) }
                        } else {
                            null
                        }
                    )
                )
            }
        }
    }

    private fun openInFilesApp(link: String?, fileId: String, username: String, baseUrl: String) {
        val accountString = "$username@${baseUrl.replace("https://", "").replace("http://", "")}"
        if (canWeOpenFilesApp(this, accountString)) {
            val filesAppIntent = Intent(Intent.ACTION_VIEW, null)
            val componentName = ComponentName(
                getString(R.string.nc_import_accounts_from),
                "com.owncloud.android.ui.activity.FileDisplayActivity"
            )
            filesAppIntent.component = componentName
            filesAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            filesAppIntent.setPackage(getString(R.string.nc_import_accounts_from))
            filesAppIntent.putExtra(KEY_ACCOUNT, accountString)
            filesAppIntent.putExtra(KEY_FILE_ID, fileId)
            startActivity(filesAppIntent)
        } else if (!link.isNullOrEmpty()) {
            startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
        }
    }

    private fun shareFile(path: String) {
        val shareUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, File(path))
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = TEXT_PREFIX_GENERIC
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }

    private fun showSaveDialog(fileName: String) {
        val saveFragment: DialogFragment = SaveToStorageDialogFragment.newInstance(fileName)
        saveFragment.show(supportFragmentManager, SaveToStorageDialogFragment.TAG)
    }

    private fun readFile(fileName: String) = File(fileName).inputStream().readBytes().toString(Charsets.UTF_8)
}
