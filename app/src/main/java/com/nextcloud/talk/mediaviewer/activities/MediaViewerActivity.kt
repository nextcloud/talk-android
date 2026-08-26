/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.extensions.getParcelableArrayListExtraProvider
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.mediaviewer.viewmodels.MediaViewerViewModel
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import com.nextcloud.talk.utils.Mimetype.VIDEO_PREFIX_GENERIC
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import java.io.File
import javax.inject.Inject

/**
 * Swipeable, group-aware media viewer - the entry point for every image/video tap in chat. See
 * MediaViewerViewModel for the navigation/paging model and MediaViewerScreen for the UI.
 */
@AutoInjector(NextcloudTalkApplication::class)
class MediaViewerActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MediaViewerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)
        val seedItems = intent.getParcelableArrayListExtraProvider<MediaViewerItem>(EXTRA_SEED_ITEMS)
        val startMessageId = intent.getLongExtra(EXTRA_START_MESSAGE_ID, -1L)
        val user = currentUserProviderOld.currentUser.blockingGet()

        if (roomToken == null || seedItems.isNullOrEmpty() || user == null) {
            Log.e(TAG, "Missing data to open the media viewer")
            finish()
            return
        }

        viewModel = ViewModelProvider(this, viewModelFactory)[MediaViewerViewModel::class.java]
        viewModel.initialize(user, roomToken, seedItems, startMessageId)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Deliberately no SwipeToCloseLayout here (unlike FullScreenImageActivity/
        // FullScreenMediaActivity): its ViewDragHelper intercepts drags at the parent level before
        // the HorizontalPager below ever sees them, and a real swipe is rarely perfectly
        // horizontal - the small vertical component was enough to trigger it, closing the viewer
        // on what the user meant as a page-navigation swipe. Closing is still available via the
        // top bar's Close button and the system back gesture/button.
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val colorScheme = viewThemeUtils.getColorScheme(this@MediaViewerActivity)
                MaterialTheme(colorScheme = colorScheme) {
                    MediaViewerScreen(
                        viewModel = viewModel,
                        onShare = ::shareFile,
                        onSave = ::showSaveDialog
                    )
                }
            }
        }

        setContentView(composeView)
    }

    private fun shareFile(item: MediaViewerItem, localPath: String) {
        val file = File(localPath)
        val shareUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, file)
        val isVideo = item.mimeType.startsWith("video/")
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = if (isVideo) VIDEO_PREFIX_GENERIC else IMAGE_PREFIX_GENERIC
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }

    private fun showSaveDialog(item: MediaViewerItem, localPath: String) {
        val safeFile = FileUtils.resolveSharedAttachmentFile(cacheDir, File(localPath).name)
        if (safeFile == null) {
            Snackbar.make(window.decorView, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            return
        }
        val saveFragment: DialogFragment = SaveToStorageDialogFragment.newInstance(safeFile.name)
        saveFragment.show(supportFragmentManager, SaveToStorageDialogFragment.TAG)
    }

    companion object {
        private val TAG = MediaViewerActivity::class.java.simpleName
        private const val EXTRA_SEED_ITEMS = "MEDIA_VIEWER_SEED_ITEMS"
        private const val EXTRA_START_MESSAGE_ID = "MEDIA_VIEWER_START_MESSAGE_ID"

        fun newIntent(
            context: Context,
            roomToken: String,
            seedItems: List<MediaViewerItem>,
            startMessageId: Long
        ): Intent =
            Intent(context, MediaViewerActivity::class.java).apply {
                putExtra(KEY_ROOM_TOKEN, roomToken)
                putParcelableArrayListExtra(EXTRA_SEED_ITEMS, ArrayList(seedItems))
                putExtra(EXTRA_START_MESSAGE_ID, startMessageId)
            }
    }
}
