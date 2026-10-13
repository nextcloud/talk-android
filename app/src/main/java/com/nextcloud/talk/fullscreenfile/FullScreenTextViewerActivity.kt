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
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.utils.AccountUtils.canWeOpenFilesApp
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype.TEXT_PREFIX_GENERIC
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenTextViewerActivity : BaseActivity() {

    private lateinit var textFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val user = setUpBoundUserOrFinish() ?: return

        val fileName = intent.getStringExtra("FILE_NAME").orEmpty()
        val isMarkdown = intent.getBooleanExtra("IS_MARKDOWN", false)
        val fileId = intent.getStringExtra("FILE_ID").orEmpty()
        val link = intent.getStringExtra("LINK")
        val username = user.username.orEmpty()
        val baseUrl = user.baseUrl.orEmpty()
        textFile = FileUtils.resolveSharedAttachmentFile(applicationContext.cacheDir, fileName) ?: run {
            Log.e(TAG, "Invalid text filename: $fileName")
            finish()
            return
        }
        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val text by produceState<String?>(null, textFile) {
                value = withContext(Dispatchers.IO) { readTextFileOrNull(textFile) } ?: run {
                    // the system may clear the cache directory while the viewer is not on screen
                    Log.e(TAG, "Could not read the text file: ${textFile.name}")
                    Toast.makeText(this@FullScreenTextViewerActivity, R.string.nc_common_error_sorry, Toast.LENGTH_LONG)
                        .show()
                    finish()
                    null
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                FullScreenTextScreen(
                    title = fileName,
                    text = text,
                    isMarkdown = isMarkdown,
                    actions = FullScreenTextActions(
                        onShare = { shareFile() },
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

    private fun shareFile() {
        val shareUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, textFile)
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

    companion object {
        private val TAG = FullScreenTextViewerActivity::class.java.simpleName

        internal fun readTextFileOrNull(file: File): String? =
            try {
                file.inputStream().use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to read the text file", e)
                null
            }
    }
}
