/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Dariusz Olszewski
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger (infoi@andy-scherzinger.de)
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021 Dariusz Olszewski
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
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.ActivityFullScreenImageBinding
import pl.droidsonroids.gif.GifDrawable
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {
    lateinit var binding: ActivityFullScreenImageBinding

    private lateinit var path: String

    private var showFullscreen = false

    private val maxScale = 6.0f
    private val mediumScale = 2.45f

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.share) {
            val shareUri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID,
                File(path)
            )

            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, shareUri)
                type = "image/*"
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

        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.imageviewToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.photoView.setOnPhotoTapListener { view, x, y ->
            toggleFullscreen()
        }
        binding.photoView.setOnOutsidePhotoTapListener {
            toggleFullscreen()
        }
        binding.gifView.setOnClickListener {
            toggleFullscreen()
        }

        // Enable enlarging the image more than default 3x maximumScale.
        // Medium scale adapted to make double-tap behaviour more consistent.
        binding.photoView.maximumScale = maxScale
        binding.photoView.mediumScale = mediumScale

        val fileName = intent.getStringExtra("FILE_NAME")
        val isGif = intent.getBooleanExtra("IS_GIF", false)

        path = applicationContext.cacheDir.absolutePath + "/" + fileName
        if (isGif) {
            binding.photoView.visibility = View.INVISIBLE
            binding.gifView.visibility = View.VISIBLE
            val gifFromUri = GifDrawable(path)
            binding.gifView.setImageDrawable(gifFromUri)
        } else {
            binding.gifView.visibility = View.INVISIBLE
            binding.photoView.visibility = View.VISIBLE
            binding.photoView.setImageURI(Uri.parse(path))
        }
    }

    private fun toggleFullscreen() {
        showFullscreen = !showFullscreen
        if (showFullscreen) {
            hideSystemUI()
            supportActionBar?.hide()
        } else {
            showSystemUI()
            supportActionBar?.show()
        }
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
