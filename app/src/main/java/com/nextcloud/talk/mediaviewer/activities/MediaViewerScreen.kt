/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.PlaybackException
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nextcloud.talk.chat.data.io.MessageAudioPolicy
import com.nextcloud.talk.mediaviewer.model.VideoAction
import com.nextcloud.talk.ui.chat.VideoActions
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.chrisbanes.photoview.PhotoView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.mediaviewer.model.MediaViewerGroup
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.mediaviewer.viewmodels.MediaViewerViewModel
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.ui.LocalImageAuthHeader
import com.nextcloud.talk.ui.withAuthHeader
import java.io.File
import kotlinx.coroutines.launch

private const val TOOLBAR_ALPHA = 0.6f
private const val MAX_SCALE = 6.0f
private const val MEDIUM_SCALE = 2.45f
private const val CONTROLLER_SHOW_TIMEOUT_MS = 2000

private val thumbnailSize = 48.dp
private val thumbnailSpacing = 4.dp
private val thumbnailStripVerticalPadding = 12.dp
private val controlsSlideDistance = 24.dp

@Suppress("Detekt.LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onShare: (MediaViewerItem, String) -> Unit,
    onSave: (MediaViewerItem, String) -> Unit,
    exoPlayer: ExoPlayer,
    inPictureInPicture: Boolean,
    initialAction: VideoAction,
    onInitialActionConsumed: () -> Unit,
    onPictureInPicture: () -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = uiState.flattenedItems
    if (items.isEmpty()) return

    val context = LocalContext.current
    val dateUtils = remember { DateUtils(context) }

    val pagerState = rememberPagerState(initialPage = uiState.currentGlobalIndex.coerceIn(0, items.size - 1)) {
        items.size
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.pendingShift?.id) {
        val shift = uiState.pendingShift
        if (shift != null) {
            if (shift.amount != 0) {
                pagerState.scrollToPage((pagerState.currentPage + shift.amount).coerceIn(0, items.size - 1))
            }
            viewModel.consumePendingShift(shift.id)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.onPageSettled(page)
        }
    }

    val currentItem = uiState.currentItem
    val currentLocalPath = currentItem?.let { uiState.cachedFilePaths[it.messageId] }

    val currentStream = currentItem?.let { uiState.videoStreams[it.messageId] }
    val currentPlaybackSource = currentStream ?: currentLocalPath

    // Autoplay only the item the viewer was directly opened on from chat - once the user has
    // swiped to any other item, nothing autoplays again, even swiping back to this one.
    val initialMessageId = remember { items.getOrNull(uiState.currentGlobalIndex)?.messageId }
    var hasAutoPlayed by remember { mutableStateOf(false) }
    val openingAction = remember { initialAction }
    var pendingAction by remember { mutableStateOf(initialAction) }
    var playerReady by remember { mutableStateOf(false) }
    var hasVideoFrame by remember(currentItem?.messageId) { mutableStateOf(false) }
    var playbackFailed by remember(currentItem?.messageId) { mutableStateOf(false) }

    DisposableEffect(exoPlayer, currentItem?.messageId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerReady = playbackState == Player.STATE_READY
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
            }

            override fun onRenderedFirstFrame() {
                hasVideoFrame = true
                if (pendingAction == VideoAction.PICTURE_IN_PICTURE &&
                    currentItem?.messageId == initialMessageId &&
                    MessageAudioPolicy.canPlay(context)
                ) {
                    pendingAction = VideoAction.PLAY
                    onInitialActionConsumed()
                    onPictureInPicture()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentItem?.messageId, currentLocalPath) {
        if (currentItem?.messageId != initialMessageId) {
            pendingAction = VideoAction.PLAY
            onInitialActionConsumed()
        } else if (pendingAction == VideoAction.DOWNLOAD && currentLocalPath != null && currentItem != null) {
            pendingAction = VideoAction.PLAY
            onInitialActionConsumed()
            onSave(currentItem, currentLocalPath)
        }
    }

    LaunchedEffect(currentItem?.messageId, currentPlaybackSource) {
        playerReady = false
        playbackFailed = false
        val isVideo = currentItem?.mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true
        if (isVideo && currentItem != null && currentPlaybackSource != null) {
            val media = MediaItem.Builder()
                .setUri(currentLocalPath?.toUri() ?: "video://${currentItem.messageId}".toUri())
                .setMediaId(currentItem.messageId.toString())
                .setMimeType(currentItem.mimeType)
                .build()
            if (currentStream != null) {
                exoPlayer.setMediaSource(currentStream.mediaSource(media))
            } else {
                exoPlayer.setMediaItem(media)
            }
            val shouldAutoPlay = !hasAutoPlayed &&
                currentItem.messageId == initialMessageId &&
                openingAction != VideoAction.DOWNLOAD &&
                MessageAudioPolicy.canPlay(context)
            exoPlayer.playWhenReady = shouldAutoPlay
            if (shouldAutoPlay) hasAutoPlayed = true
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
        }
    }

    // Tapping the currently shown item toggles this off, hiding the top bar and thumbnail strip so
    // only the media itself is visible. For video, ExoPlayer's own controller visibility is the
    // source of truth (see VideoPlayerView) rather than an independently toggled flag, since the
    // controller already auto-hides itself after a timeout.
    var showControls by remember { mutableStateOf(true) }

    // The status/nav bars toggle together with the top bar and thumbnail strip - one tap hides all
    // of it.
    LaunchedEffect(showControls, inPictureInPicture) {
        onControlsVisibilityChanged(showControls && !inPictureInPicture)
    }

    // Hoisted here (rather than inside ThumbnailStrip) so its scroll position survives showControls
    // toggling the strip in and out of composition - otherwise every reveal remounted a fresh,
    // unscrolled LazyListState and re-animated the scroll from the start, reading as the thumbnails
    // sliding in from the side instead of the intended plain fade.
    val thumbnailListState = rememberLazyListState()
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { controlsSlideDistance.roundToPx() }

    val group = uiState.currentGroup
    val hasThumbnailStrip = group != null && group.items.size > 1
    // Reserved so the video's own ExoPlayer controller (progress bar, play/pause row) never renders
    // underneath the thumbnail strip - the strip's height is fixed (a constant thumbnail size plus
    // its own fixed padding), so this is knowable up front rather than measured. The strip's
    // navigation bar inset is excluded here: VideoPlayerView adds that inset itself.
    val thumbnailStripHeightPx = with(density) {
        (thumbnailSize + thumbnailStripVerticalPadding * 2).roundToPx()
    }
    val isCurrentVideo = currentItem?.mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true
    var videoActionsHeightPx by remember { mutableIntStateOf(0) }
    val controllerExtraBottomInsetPx = if (inPictureInPicture) {
        0
    } else {
        (if (hasThumbnailStrip) thumbnailStripHeightPx else 0) + (if (isCurrentVideo) videoActionsHeightPx else 0)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !inPictureInPicture,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            MediaPage(
                item = item,
                localPath = uiState.cachedFilePaths[item.messageId],
                // Gated on settledPage, not the live currentPage: the shared exoPlayer's media
                // source is only swapped once the pager settles too (see the LaunchedEffect keyed
                // on currentItem below), so mounting VideoPlayerView any earlier would attach it
                // to a page whose video hasn't been loaded into the player yet.
                isCurrentPage = page == pagerState.settledPage,
                canStream = item.messageId in uiState.videoStreams,
                exoPlayer = exoPlayer,
                onToggleControls = { showControls = !showControls },
                onControlsVisibilityChanged = { showControls = it },
                controllerExtraBottomInsetPx = controllerExtraBottomInsetPx,
                inPictureInPicture = inPictureInPicture
            )
        }

        AnimatedVisibility(
            visible = showControls && !inPictureInPicture,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -slideOffsetPx }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -slideOffsetPx }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val toolbarColors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = TOOLBAR_ALPHA),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
            val menuItems = buildList {
                if (currentItem != null && currentLocalPath != null) {
                    add(stringResource(R.string.share) to { onShare(currentItem, currentLocalPath) })
                    add(stringResource(R.string.nc_save_message) to { onSave(currentItem, currentLocalPath) })
                }
            }
            val sentDateTime = currentItem?.let {
                dateUtils.getLocalDateTimeStringFromTimestamp(it.timestamp * DateConstants.SECOND_DIVIDER)
            }
            StandardAppBar(
                title = currentItem?.actorDisplayName.orEmpty(),
                menuItems = menuItems,
                colors = toolbarColors,
                subtitle = sentDateTime
            )
        }

        // Keyed on the live pager page (pagerState.currentPage), not the settle-driven currentItem
        // above - otherwise, after a quick swipe that outruns settling, this kept showing the
        // spinner for the page the user had already left rather than the one on screen.
        val livePageItem = items.getOrNull(pagerState.currentPage)
        val livePageLocalPath = livePageItem?.let { uiState.cachedFilePaths[it.messageId] }
        if (livePageItem != null && livePageLocalPath == null && !inPictureInPicture) {
            val playing = livePageItem.messageId == currentItem?.messageId && hasVideoFrame && !playbackFailed
            val placement = if (playing) {
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = TOOLBAR_ALPHA), RoundedCornerShape(8.dp)).padding(8.dp)
            } else {
                Modifier.align(Alignment.Center)
            }
            Column(modifier = placement, horizontalAlignment = Alignment.CenterHorizontally) {
                if (livePageItem.messageId in uiState.failedMessageIds) {
                    Text(stringResource(R.string.video_download_failed), color = Color.White)
                    TextButton(onClick = { viewModel.retryDownload(livePageItem.messageId) }) {
                        Text(stringResource(R.string.nc_retry), color = Color.White)
                    }
                } else {
                    val percent = uiState.downloadProgress[livePageItem.messageId]?.takeIf { it >= 0 }
                    val indicator = Modifier.size(if (playing) 24.dp else 48.dp)
                    if (percent != null) {
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = indicator,
                            color = Color.White
                        )
                        Text("$percent%", color = Color.White)
                    } else {
                        CircularProgressIndicator(modifier = indicator, color = Color.White)
                    }
                }
            }
        }

        if (playbackFailed && !inPictureInPicture) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.video_playback_failed), color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = {
                    playbackFailed = false
                    exoPlayer.prepare()
                    if (MessageAudioPolicy.canPlay(context)) exoPlayer.play()
                }) {
                    Text(stringResource(R.string.nc_retry), color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && !inPictureInPicture && (hasThumbnailStrip || isCurrentVideo),
            enter = fadeIn() + slideInVertically(initialOffsetY = { slideOffsetPx }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { slideOffsetPx }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.background(Color.Black.copy(alpha = TOOLBAR_ALPHA)).navigationBarsPadding()) {
                if (isCurrentVideo && currentItem != null) {
                    VideoActions(
                        onAction = { action ->
                            when (action) {
                                VideoAction.DOWNLOAD -> currentLocalPath?.let { onSave(currentItem, it) }
                                VideoAction.PICTURE_IN_PICTURE -> if (playerReady) onPictureInPicture()
                                VideoAction.PLAY -> Unit
                            }
                        },
                        modifier = Modifier.fillMaxWidth().onSizeChanged { videoActionsHeightPx = it.height },
                        enabled = currentLocalPath != null,
                        pipEnabled = playerReady && !playbackFailed
                    )
                }
                if (hasThumbnailStrip && group != null) {
                    ThumbnailStrip(
                        group = group,
                        currentItem = currentItem,
                        listState = thumbnailListState,
                        onThumbnailClick = { clickedItem ->
                            val globalIndex = items.indexOfFirst { it.messageId == clickedItem.messageId }
                            if (globalIndex >= 0) {
                                coroutineScope.launch { pagerState.animateScrollToPage(globalIndex) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@OptIn(UnstableApi::class)
@Composable
private fun MediaPage(
    item: MediaViewerItem,
    localPath: String?,
    isCurrentPage: Boolean,
    canStream: Boolean,
    exoPlayer: ExoPlayer,
    onToggleControls: () -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    controllerExtraBottomInsetPx: Int,
    inPictureInPicture: Boolean
) {
    val isVideo = item.mimeType.startsWith(Mimetype.VIDEO_PREFIX)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            localPath == null && !canStream -> PreviewPlaceholder(item, onToggleControls = onToggleControls)
            isVideo -> if (isCurrentPage) {
                VideoPlayerView(
                    exoPlayer = exoPlayer,
                    onControlsVisibilityChanged = onControlsVisibilityChanged,
                    extraBottomInsetPx = controllerExtraBottomInsetPx,
                    inPictureInPicture = inPictureInPicture
                )
            } else {
                PreviewPlaceholder(item, onToggleControls = onToggleControls)
            }
            else -> localPath?.let { ImagePage(localPath = it, onToggleControls = onToggleControls) }
        }
    }
}

@Composable
private fun PreviewPlaceholder(item: MediaViewerItem, onToggleControls: () -> Unit) {
    if (item.previewUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.previewUrl)
                .withAuthHeader(LocalImageAuthHeader.current)
                .build(),
            contentDescription = item.fileName,
            modifier = Modifier.fillMaxSize().clickable(onClick = onToggleControls),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ImagePage(localPath: String, onToggleControls: () -> Unit) {
    val context = LocalContext.current
    var drawable by remember(localPath) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(localPath) {
        val displayMetrics = context.resources.displayMetrics
        val request = ImageRequest.Builder(context)
            .data(File(localPath))
            .size(displayMetrics.widthPixels * 2, displayMetrics.heightPixels * 2)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            drawable = result.drawable
        } else {
            Log.e(TAG, "image could not be decoded from path: $localPath")
        }
    }

    AndroidView(
        factory = { ctx ->
            PhotoView(ctx).apply {
                maximumScale = MAX_SCALE
                mediumScale = MEDIUM_SCALE
                setOnPhotoTapListener { _, _, _ -> onToggleControls() }
                setOnOutsidePhotoTapListener { onToggleControls() }
            }
        },
        update = { photoView ->
            drawable?.let {
                photoView.setImageDrawable(it)
                // Animated images (GIF, animated WebP/HEIF) decoded by Coil are not started automatically
                (it as? Animatable)?.start()
            }
        },
        onRelease = { photoView -> (photoView.drawable as? Animatable)?.stop() },
        modifier = Modifier.fillMaxSize()
    )
}

// Pushes ExoPlayer's own controller (progress bar, play/pause row) up by extraBottomInsetPx (the
// thumbnail strip's height, when one is showing for the current group) on top of the system nav
// bar inset, so the controller never renders underneath the strip instead of shrinking the video
// content itself, which would visibly resize the video on every show/hide-controls tap.
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    extraBottomInsetPx: Int,
    inPictureInPicture: Boolean
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // Deliberately "IgnoringVisibility": the plain systemBars value animates across many frames
    // while we hide/show the bars (see enterImmersiveMode/exitImmersiveMode), and reapplying
    // these margins on every one of those frames was fighting the ExoPlayer controller's own
    // hide animation, flipping it back to visible mid-fade and looping forever.
    val systemBarsBottomPx = WindowInsets.systemBarsIgnoringVisibility.getBottom(density)
    val leftPx = WindowInsets.systemBarsIgnoringVisibility.getLeft(density, layoutDirection)
    val rightPx = WindowInsets.systemBarsIgnoringVisibility.getRight(density, layoutDirection)
    val bottomPx = systemBarsBottomPx + extraBottomInsetPx
    val originalProgressMarginBottom = remember { intArrayOf(-1) }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = !inPictureInPicture
                controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT_MS
                showController()
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onControlsVisibilityChanged(visibility == View.VISIBLE)
                    }
                )
            }
        },
        update = { playerView ->
            playerView.useController = !inPictureInPicture
            if (inPictureInPicture) playerView.hideController()
            val exoControls = playerView.findViewById<FrameLayout>(R.id.exo_bottom_bar)
            val exoProgress = playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
            exoControls?.apply {
                updateLayoutParams<MarginLayoutParams> { bottomMargin = bottomPx }
                updatePadding(left = leftPx, right = rightPx)
            }
            exoProgress?.apply {
                if (originalProgressMarginBottom[0] < 0) {
                    originalProgressMarginBottom[0] = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
                }
                updateLayoutParams<MarginLayoutParams> {
                    bottomMargin = bottomPx + originalProgressMarginBottom[0]
                }
                updatePadding(left = leftPx, right = rightPx)
            }
        },
        // The player instance is shared across pages (see rememberViewerExoPlayer), so when this
        // page's PlayerView leaves composition it must let go of it explicitly - otherwise it stays
        // registered as a Player.Listener and keeps its (by-then-detached) surface referenced, which
        // piles up across swipes instead of being released with the view.
        onRelease = { playerView -> playerView.player = null },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ThumbnailStrip(
    group: MediaViewerGroup,
    currentItem: MediaViewerItem?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onThumbnailClick: (MediaViewerItem) -> Unit
) {
    val currentIndex = currentItem?.let { item -> group.items.indexOfFirst { it.messageId == item.messageId } } ?: -1

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = TOOLBAR_ALPHA))
            .padding(vertical = thumbnailStripVerticalPadding)
    ) {
        // Side padding equal to half the leftover viewport width, so the row can scroll any item -
        // including the first/last - all the way to the exact horizontal center, not just as close
        // to it as the natural content bounds allow.
        val sidePadding = ((maxWidth - thumbnailSize) / 2).coerceAtLeast(0.dp)

        LaunchedEffect(group.key, currentIndex) {
            if (currentIndex >= 0) {
                listState.animateScrollToItem(currentIndex)
            }
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(thumbnailSpacing),
            contentPadding = PaddingValues(horizontal = sidePadding),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(group.items, key = { it.messageId }) { item ->
                ThumbnailTile(
                    item = item,
                    isSelected = item.messageId == currentItem?.messageId,
                    onClick = { onThumbnailClick(item) }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailTile(item: MediaViewerItem, isSelected: Boolean, onClick: () -> Unit) {
    val isVideo = item.mimeType.startsWith(Mimetype.VIDEO_PREFIX)
    Box(
        modifier = Modifier
            .size(thumbnailSize)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (item.previewUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.previewUrl)
                    .withAuthHeader(LocalImageAuthHeader.current)
                    .build(),
                contentDescription = item.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(DrawableUtils.getDrawableResourceIdForMimeType(item.mimeType)),
                contentDescription = item.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
                contentDescription = stringResource(R.string.media_message_content_play),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private const val TAG = "MediaViewerScreen"
