/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Dariusz Olszewski
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
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.github.chrisbanes.photoview.PhotoView
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var path: String
    private lateinit var imageWrapperView: FrameLayout
    private lateinit var photoView: PhotoView
    private lateinit var gifView: GifImageView

    private var showFullscreen = false

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

        setContentView(R.layout.activity_full_screen_image)
        setSupportActionBar(findViewById(R.id.imageview_toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        imageWrapperView = findViewById(R.id.image_wrapper_view)
        photoView = findViewById(R.id.photo_view)
        gifView = findViewById(R.id.gif_view)

        photoView.setOnPhotoTapListener { view, x, y ->
            toggleFullscreen()
        }
        photoView.setOnOutsidePhotoTapListener {
            toggleFullscreen()
        }
        gifView.setOnClickListener {
            toggleFullscreen()
        }

        // Enable enlarging the image more than default 3x maximumScale.
        // Medium scale adapted to make double-tap behaviour more consistent.
        photoView.maximumScale = 6.0f
        photoView.mediumScale = 2.45f

        val fileName = intent.getStringExtra("FILE_NAME")
        val isGif = intent.getBooleanExtra("IS_GIF", false)

        path = applicationContext.cacheDir.absolutePath + "/" + fileName
        if (isGif) {
            photoView.visibility = View.INVISIBLE
            gifView.visibility = View.VISIBLE
            val gifFromUri = GifDrawable(path)
            gifView.setImageDrawable(gifFromUri)
        } else {
            gifView.visibility = View.INVISIBLE
            photoView.visibility = View.VISIBLE
            photoView.setImageURI(Uri.parse(path))
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
