/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Dariusz Olszewski <starypatyk@gmail.com>
 * SPDX-FileCopyrightText: 2026 Enrique López-Mañas <eenriquelopez@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.fullscreenfile

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.SwipeToCloseLayout
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenImageActivity : AppCompatActivity() {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private lateinit var path: String
    private lateinit var fileName: String
    private lateinit var swipeToCloseLayout: SwipeToCloseLayout
    private var showFullscreen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        fileName = intent.getStringExtra("FILE_NAME").orEmpty()
        val isGif = intent.getBooleanExtra("IS_GIF", false)
        path = applicationContext.cacheDir.absolutePath + "/" + fileName

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        initWindowInsetsController()

        swipeToCloseLayout = SwipeToCloseLayout(this)
        swipeToCloseLayout.setOnSwipeToCloseListener(object : SwipeToCloseLayout.OnSwipeToCloseListener {
            override fun onSwipeToClose() {
                finish()
            }
        })

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val colorScheme = viewThemeUtils.getColorScheme(this@FullScreenImageActivity)
                MaterialTheme(colorScheme = colorScheme) {
                    FullScreenImageScreen(
                        title = fileName,
                        isGif = isGif,
                        imagePath = path,
                        showFullscreen = showFullscreen,
                        actions = FullScreenImageActions(
                            onShare = { shareFile() },
                            onSave = { showSaveDialog() },
                            onToggleFullscreen = { toggleFullscreen() },
                            onBitmapError = { showBitmapError() }
                        )
                    )
                }
            }
        }

        swipeToCloseLayout.addView(
            composeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        setContentView(swipeToCloseLayout)
    }

    private fun toggleFullscreen() {
        showFullscreen = !showFullscreen
        if (showFullscreen) {
            enterImmersiveMode()
        } else {
            exitImmersiveMode()
        }
    }

    private fun initWindowInsetsController() {
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun enterImmersiveMode() {
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun exitImmersiveMode() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun shareFile() {
        val shareUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, File(path))
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = IMAGE_PREFIX_GENERIC
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }

    private fun showSaveDialog() {
        val saveFragment: DialogFragment = SaveToStorageDialogFragment.newInstance(fileName)
        saveFragment.show(supportFragmentManager, SaveToStorageDialogFragment.TAG)
    }

    private fun showBitmapError() {
        Snackbar.make(swipeToCloseLayout, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
    }
}
