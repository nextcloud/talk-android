/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.github.chrisbanes.photoview.PhotoView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.mediaviewer.model.MediaViewerGroup
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.mediaviewer.viewmodels.MediaViewerViewModel
import com.nextcloud.talk.utils.BitmapShrinker
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.MimetypeUtils
import kotlinx.coroutines.launch
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

private const val TOOLBAR_ALPHA = 0.6f
private const val MAX_SCALE = 6.0f
private const val MEDIUM_SCALE = 2.45f

private val thumbnailSize = 48.dp
private val thumbnailSpacing = 4.dp
private val thumbnailStripTopPadding = 12.dp
private val thumbnailStripBottomPadding = 24.dp
private val controlsSlideDistance = 24.dp

@Suppress("Detekt.LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onShare: (MediaViewerItem, String) -> Unit,
    onSave: (MediaViewerItem, String) -> Unit,
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

    // See MediaViewerViewModel.indexShiftEvents: prepending older groups shifts every existing
    // index, so the pager must silently jump to keep the same item on screen.
    LaunchedEffect(viewModel) {
        viewModel.indexShiftEvents.collect { shift ->
            if (shift != 0) {
                pagerState.scrollToPage((pagerState.currentPage + shift).coerceIn(0, items.size - 1))
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.onPageSettled(page)
        }
    }

    val exoPlayer = rememberViewerExoPlayer()
    val currentItem = uiState.currentItem
    val currentLocalPath = currentItem?.let { uiState.cachedFilePaths[it.messageId] }

    // Autoplay only the item the viewer was directly opened on from chat - once the user has
    // swiped to any other item, nothing autoplays again, even swiping back to this one.
    val initialMessageId = remember { items.getOrNull(uiState.currentGlobalIndex)?.messageId }
    var hasAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(currentItem?.messageId, currentLocalPath) {
        val isVideo = currentItem?.mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true
        if (isVideo && currentLocalPath != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentLocalPath.toUri()))
            val shouldAutoPlay = !hasAutoPlayed && currentItem.messageId == initialMessageId
            exoPlayer.playWhenReady = shouldAutoPlay
            if (shouldAutoPlay) hasAutoPlayed = true
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
        }
    }

    // Tapping the currently shown item toggles this off, hiding the top bar and thumbnail strip so
    // only the media itself is visible - mirrors FullScreenMediaScreen's own tap-to-toggle-fullscreen
    // behavior (still used for audio). For video, ExoPlayer's own controller visibility is the
    // source of truth (see VideoPlayerView) rather than an independently toggled flag, since the
    // controller already auto-hides itself after a timeout.
    var showControls by remember { mutableStateOf(true) }

    // The status/nav bars toggle together with the top bar and thumbnail strip - one tap hides all
    // of it, matching FullScreenMediaScreen's own tap-to-toggle-fullscreen.
    LaunchedEffect(showControls) {
        onControlsVisibilityChanged(showControls)
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
    // its own fixed padding), so this is knowable up front rather than measured.
    val thumbnailStripHeightPx = with(density) {
        (thumbnailSize + thumbnailStripTopPadding + thumbnailStripBottomPadding).roundToPx()
    }
    val controllerExtraBottomInsetPx = if (hasThumbnailStrip) thumbnailStripHeightPx else 0

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            MediaPage(
                item = item,
                localPath = uiState.cachedFilePaths[item.messageId],
                isCurrentPage = page == pagerState.currentPage,
                exoPlayer = exoPlayer,
                onToggleControls = { showControls = !showControls },
                onControlsVisibilityChanged = { showControls = it },
                controllerExtraBottomInsetPx = controllerExtraBottomInsetPx
            )
        }

        AnimatedVisibility(
            visible = showControls,
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

        if (currentItem != null && currentLocalPath == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White
            )
        }

        AnimatedVisibility(
            visible = showControls && hasThumbnailStrip,
            enter = fadeIn() + slideInVertically(initialOffsetY = { slideOffsetPx }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { slideOffsetPx }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (group != null) {
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

@OptIn(UnstableApi::class)
@Composable
private fun rememberViewerExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}

@Suppress("LongParameterList")
@OptIn(UnstableApi::class)
@Composable
private fun MediaPage(
    item: MediaViewerItem,
    localPath: String?,
    isCurrentPage: Boolean,
    exoPlayer: ExoPlayer,
    onToggleControls: () -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    controllerExtraBottomInsetPx: Int
) {
    val isVideo = item.mimeType.startsWith(Mimetype.VIDEO_PREFIX)
    val isGif = MimetypeUtils.isGif(item.mimeType)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            localPath == null -> PreviewPlaceholder(item, onToggleControls = onToggleControls)
            isVideo -> if (isCurrentPage) {
                VideoPlayerView(
                    exoPlayer = exoPlayer,
                    onControlsVisibilityChanged = onControlsVisibilityChanged,
                    extraBottomInsetPx = controllerExtraBottomInsetPx
                )
            } else {
                PreviewPlaceholder(item, onToggleControls = onToggleControls)
            }
            isGif -> GifPage(localPath = localPath, onToggleControls = onToggleControls)
            else -> ImagePage(localPath = localPath, onToggleControls = onToggleControls)
        }
    }
}

@Composable
private fun PreviewPlaceholder(item: MediaViewerItem, onToggleControls: () -> Unit) {
    if (item.previewUrl != null) {
        AsyncImage(
            model = item.previewUrl,
            contentDescription = item.fileName,
            modifier = Modifier.fillMaxSize().clickable(onClick = onToggleControls),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun GifPage(localPath: String, onToggleControls: () -> Unit) {
    AndroidView(
        factory = { ctx ->
            GifImageView(ctx).apply {
                setImageDrawable(GifDrawable(localPath))
                setOnClickListener { onToggleControls() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ImagePage(localPath: String, onToggleControls: () -> Unit) {
    AndroidView(
        factory = { ctx ->
            PhotoView(ctx).apply {
                maximumScale = MAX_SCALE
                mediumScale = MEDIUM_SCALE
                setOnPhotoTapListener { _, _, _ -> onToggleControls() }
                setOnOutsidePhotoTapListener { onToggleControls() }
                val displayMetrics = ctx.resources.displayMetrics
                val bitmap = BitmapShrinker.shrinkBitmap(
                    localPath,
                    displayMetrics.widthPixels * 2,
                    displayMetrics.heightPixels * 2
                )
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    Log.e(TAG, "bitmap could not be decoded from path: $localPath")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Pushes ExoPlayer's own controller (progress bar, play/pause row) up by extraBottomInsetPx (the
// thumbnail strip's height, when one is showing for the current group) on top of the system nav
// bar inset, same technique FullScreenMediaScreen's MediaPlayerView already uses to keep the
// controller clear of the nav bar - so the controller never renders underneath the strip instead
// of shrinking the video content itself, which would visibly resize the video on every
// show/hide-controls tap.
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    extraBottomInsetPx: Int
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBarsBottomPx = WindowInsets.systemBars.getBottom(density)
    val leftPx = WindowInsets.systemBars.getLeft(density, layoutDirection)
    val rightPx = WindowInsets.systemBars.getRight(density, layoutDirection)
    val bottomPx = systemBarsBottomPx + extraBottomInsetPx
    val originalProgressMarginBottom = remember { intArrayOf(-1) }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                showController()
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onControlsVisibilityChanged(visibility == View.VISIBLE)
                    }
                )
            }
        },
        update = { playerView ->
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
            .padding(top = thumbnailStripTopPadding, bottom = thumbnailStripBottomPadding)
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
                model = item.previewUrl,
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
