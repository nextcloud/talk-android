/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Dariusz Olszewski <starypatyk@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.fullscreenfile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityFullScreenImageBinding
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.utils.BitmapShrinker
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import pl.droidsonroids.gif.GifDrawable
import java.io.File

@AutoInjector(NextcloudTalkApplication::class)
class FullScreenImageActivity : AppCompatActivity() {
    lateinit var binding: ActivityFullScreenImageBinding
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private lateinit var path: String
    private var showFullscreen = false

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
                    type = IMAGE_PREFIX_GENERIC
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

        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.imageviewToolbar)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        initWindowInsetsController()
        applyWindowInsets()
        binding.photoView.setOnPhotoTapListener { _, _, _ ->
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
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
        } else {
            binding.photoView.setImageBitmap(bitmap)
        }
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
        supportActionBar?.hide()
    }

    private fun exitImmersiveMode() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        supportActionBar?.show()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.imageviewToolbar.updateLayoutParams<MarginLayoutParams> {
                topMargin = insets.top
            }
            binding.imageviewToolbar.updatePadding(left = insets.left, right = insets.right)
            WindowInsetsCompat.CONSUMED
        }
    }

    companion object {
        private const val TAG = "FullScreenImageActivity"
        private const val HUNDRED_MB = 100 * 1024 * 1024
        private const val MAX_SCALE = 6.0f
        private const val MEDIUM_SCALE = 2.45f
    }
}
