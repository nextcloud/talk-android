/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Dariusz Olszewski
 * @author Andy Scherzinger
 * @author Ezhil Shanmugham
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.ActivityFullScreenImageBinding
import com.nextcloud.talk.utils.BitmapShrinker
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import pl.droidsonroids.gif.GifDrawable
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {
    lateinit var binding: ActivityFullScreenImageBinding

    private lateinit var path: String
    private var showFullscreen = false

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
                type = IMAGE_PREFIX_GENERIC
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
        binding.photoView.maximumScale = MAX_SCALE
        binding.photoView.mediumScale = MEDIUM_SCALE

        val fileName = intent.getStringExtra("FILE_NAME")
        val isGif = intent.getBooleanExtra("IS_GIF", false)

        supportActionBar?.title = fileName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        path = applicationContext.cacheDir.absolutePath + "/" + fileName
        if (isGif) {
            binding.photoView.visibility = View.INVISIBLE
            binding.gifView.visibility = View.VISIBLE
            val gifFromUri = GifDrawable(path)
            binding.gifView.setImageDrawable(gifFromUri)
        } else {
            binding.gifView.visibility = View.INVISIBLE
            binding.photoView.visibility = View.VISIBLE
            displayImage(path)
        }
    }

    private fun displayImage(path: String) {
        val displayMetrics = applicationContext.resources.displayMetrics
        val doubleScreenWidth = displayMetrics.widthPixels * 2
        val doubleScreenHeight = displayMetrics.heightPixels * 2

        val bitmap = BitmapShrinker.shrinkBitmap(path, doubleScreenWidth, doubleScreenHeight)

        val bitmapSize: Int = bitmap.byteCount

        // info that 100MB is the limit comes from https://stackoverflow.com/a/53334563
        if (bitmapSize > HUNDRED_MB) {
            Log.e(TAG, "bitmap will be too large to display. It won't be displayed to avoid RuntimeException")
            Toast.makeText(this, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
        } else {
            binding.photoView.setImageBitmap(bitmap)
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

    companion object {
        private val TAG = "FullScreenImageActivity"
        private const val HUNDRED_MB = 100 * 1024 * 1024
        private const val MAX_SCALE = 6.0f
        private const val MEDIUM_SCALE = 2.45f
    }
}
