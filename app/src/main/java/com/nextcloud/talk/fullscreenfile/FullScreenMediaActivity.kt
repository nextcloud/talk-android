/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2023 Parneet Singh <gurayaparneet@gmail.com>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2026 Enrique López-Mañas <eenriquelopez@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.fullscreenfile

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import autodagger.AutoInjector
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.SwipeToCloseLayout
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.utils.Mimetype.VIDEO_PREFIX_GENERIC
import java.io.File

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenMediaActivity : AppCompatActivity() {

    private lateinit var path: String
    private lateinit var fileName: String
    private var player: ExoPlayer? by mutableStateOf(null)
    private var playWhenReadyState: Boolean = true
    private var playBackPosition: Long = 0L
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileName = intent.getStringExtra("FILE_NAME").orEmpty()
        val isAudioOnly = intent.getBooleanExtra("AUDIO_ONLY", false)
        path = applicationContext.cacheDir.absolutePath + "/" + fileName

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        initWindowInsetsController()

        val swipeToCloseLayout = SwipeToCloseLayout(this)
        swipeToCloseLayout.setOnSwipeToCloseListener(object : SwipeToCloseLayout.OnSwipeToCloseListener {
            override fun onSwipeToClose() {
                finish()
            }
        })

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    FullScreenMediaScreen(
                        title = fileName,
                        player = player,
                        isAudioOnly = isAudioOnly,
                        actions = FullScreenMediaActions(
                            onShare = { shareFile() },
                            onSave = { showSaveDialog() },
                            onEnterImmersive = { enterImmersiveMode() },
                            onExitImmersive = { exitImmersiveMode() }
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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        preparePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        player = ExoPlayer.Builder(applicationContext)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun preparePlayer() {
        val mediaItem: MediaItem = MediaItem.fromUri(File(path).toUri())
        player?.let { exoPlayer ->
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = playWhenReadyState
            exoPlayer.seekTo(playBackPosition)
            exoPlayer.prepare()
        }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playBackPosition = exoPlayer.currentPosition
            playWhenReadyState = exoPlayer.playWhenReady
            exoPlayer.release()
        }
        player = null
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
            type = VIDEO_PREFIX_GENERIC
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }

    private fun showSaveDialog() {
        val saveFragment: DialogFragment = SaveToStorageDialogFragment.newInstance(fileName)
        saveFragment.show(supportFragmentManager, SaveToStorageDialogFragment.TAG)
    }
}
