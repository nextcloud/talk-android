/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Ezhil Shanmugham
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
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

package com.nextcloud.talk.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import autodagger.AutoInjector
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityFullScreenMediaBinding
import com.nextcloud.talk.utils.Mimetype.VIDEO_PREFIX_GENERIC
import java.io.File

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenMediaActivity : AppCompatActivity(), Player.Listener {
    lateinit var binding: ActivityFullScreenMediaBinding

    private lateinit var path: String
    private lateinit var player: SimpleExoPlayer

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else if (item.itemId == R.id.share) {
            val shareUri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID,
                File(path)
            )

            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, shareUri)
                type = VIDEO_PREFIX_GENERIC
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))

            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileName = intent.getStringExtra("FILE_NAME")
        val isAudioOnly = intent.getBooleanExtra("AUDIO_ONLY", false)

        path = applicationContext.cacheDir.absolutePath + "/" + fileName

        binding = ActivityFullScreenMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.mediaviewToolbar)
        supportActionBar?.title = fileName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.playerView.showController()
        if (isAudioOnly) {
            binding.playerView.controllerShowTimeoutMs = 0
        }

        binding.playerView.setControllerVisibilityListener(
            StyledPlayerView.ControllerVisibilityListener { v ->
                if (v != 0) {
                    hideSystemUI()
                    supportActionBar?.hide()
                } else {
                    showSystemUI()
                    supportActionBar?.show()
                }
            }
        )

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()

        val mediaItem: MediaItem = MediaItem.fromUri(path)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer() {
        player = SimpleExoPlayer.Builder(applicationContext).build()
        binding.playerView.player = player
        player.playWhenReady = true
        player.addListener(this)
    }

    private fun releasePlayer() {
        player.release()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }
}
