/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.fullscreenfile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.Mimetype.TEXT_PREFIX_GENERIC
import com.nextcloud.talk.utils.adjustUIForAPILevel35
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
                    onShare = { shareFile(path) },
                    onSave = { showSaveDialog(fileName) }
                )
            }
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
