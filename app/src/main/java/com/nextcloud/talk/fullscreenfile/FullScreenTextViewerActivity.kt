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
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityFullScreenTextBinding
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.Mimetype.TEXT_PREFIX_GENERIC
import io.noties.markwon.Markwon
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenTextViewerActivity : AppCompatActivity() {
    lateinit var binding: ActivityFullScreenTextBinding

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var path: String

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.share -> {
                val shareUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID,
                    File(path)
                )

                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    type = TEXT_PREFIX_GENERIC
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))

                true
            }

            R.id.save -> {
                val saveFragment: DialogFragment = SaveToStorageDialogFragment.newInstance(
                    intent.getStringExtra("FILE_NAME").toString()
                )
                saveFragment.show(
                    supportFragmentManager,
                    SaveToStorageDialogFragment.TAG
                )
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityFullScreenTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.textviewToolbar)

        val fileName = intent.getStringExtra("FILE_NAME")
        val isMarkdown = intent.getBooleanExtra("IS_MARKDOWN", false)
        path = applicationContext.cacheDir.absolutePath + "/" + fileName
        val text = readFile(path)

        if (isMarkdown) {
            val markwon = Markwon.create(applicationContext)
            markwon.setMarkdown(binding.textView, text)
        } else {
            binding.textView.text = text
        }

        supportActionBar?.title = fileName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewThemeUtils.platform.themeStatusBar(this)
        viewThemeUtils.material.themeToolbar(binding.textviewToolbar)
        viewThemeUtils.material.colorToolbarOverflowIcon(binding.textviewToolbar)

        if (resources != null) {
            DisplayUtils.applyColorToNavigationBar(
                this.window,
                ResourcesCompat.getColor(resources, R.color.bg_default, null)
            )
        }
    }

    private fun readFile(fileName: String) = File(fileName).inputStream().readBytes().toString(Charsets.UTF_8)
}
