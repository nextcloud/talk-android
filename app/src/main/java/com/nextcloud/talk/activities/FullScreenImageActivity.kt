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

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.ActivityFullScreenImageBinding
import com.nextcloud.talk.jobs.SaveFileToStorageWorker
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.BitmapShrinker
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.util.concurrent.ExecutionException

class FullScreenImageActivity : AppCompatActivity() {
    lateinit var binding: ActivityFullScreenImageBinding
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private lateinit var path: String
    private var showFullscreen = false
    lateinit var viewThemeUtils: ViewThemeUtils

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_preview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
                showWarningDialog()
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun showWarningDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.nc_dialog_save_to_storage_title)
        builder.setMessage(R.string.nc_dialog_save_to_storage_content)
        builder.setPositiveButton(R.string.nc_dialog_save_to_storage_yes) { dialog: DialogInterface, which: Int ->
            val fileName = intent.getStringExtra("FILE_NAME").toString()
            saveImageToStorage(fileName)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.nc_dialog_save_to_storage_no) { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    @SuppressLint("LongLogTag")
    private fun saveImageToStorage(
        fileName: String
    ) {
        val sourceFilePath = applicationContext.cacheDir.path

        val workers = WorkManager.getInstance(this).getWorkInfosByTag(fileName)
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    return
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }

        val data: Data = Data.Builder()
            .putString(SaveFileToStorageWorker.KEY_FILE_NAME, fileName)
            .putString(SaveFileToStorageWorker.KEY_SOURCE_FILE_PATH, "$sourceFilePath/$fileName")
            .build()

        val saveWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(SaveFileToStorageWorker::class.java)
            .setInputData(data)
            .addTag(fileName)
            .build()

        WorkManager.getInstance().enqueue(saveWorker)
    }

    companion object {
        private const val TAG = "FullScreenImageActivity"
        private const val HUNDRED_MB = 100 * 1024 * 1024
        private const val MAX_SCALE = 6.0f
        private const val MEDIUM_SCALE = 2.45f
    }
}
