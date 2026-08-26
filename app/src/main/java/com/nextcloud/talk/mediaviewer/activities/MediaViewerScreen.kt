/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.github.chrisbanes.photoview.PhotoView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.mediaviewer.model.MediaViewerGroup
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.mediaviewer.viewmodels.MediaViewerViewModel
import com.nextcloud.talk.utils.BitmapShrinker
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
private val thumbnailStripPadding = 12.dp

@Suppress("Detekt.LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onShare: (MediaViewerItem, String) -> Unit,
    onSave: (MediaViewerItem, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = uiState.flattenedItems
    if (items.isEmpty()) return

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
    LaunchedEffect(currentItem?.messageId, currentLocalPath) {
        val isVideo = currentItem?.mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true
        if (isVideo && currentLocalPath != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentLocalPath.toUri()))
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            MediaPage(
                item = item,
                localPath = uiState.cachedFilePaths[item.messageId],
                isCurrentPage = page == pagerState.currentPage,
                exoPlayer = exoPlayer
            )
        }

        val toolbarColors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
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
        StandardAppBar(title = currentItem?.actorDisplayName.orEmpty(), menuItems = menuItems, colors = toolbarColors)

        if (currentItem != null && currentLocalPath == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White
            )
        }

        val group = uiState.currentGroup
        if (group != null && group.items.size > 1) {
            ThumbnailStrip(
                group = group,
                currentItem = currentItem,
                modifier = Modifier.align(Alignment.BottomCenter),
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

@OptIn(UnstableApi::class)
@Composable
private fun MediaPage(item: MediaViewerItem, localPath: String?, isCurrentPage: Boolean, exoPlayer: ExoPlayer) {
    val isVideo = item.mimeType.startsWith(Mimetype.VIDEO_PREFIX)
    val isGif = MimetypeUtils.isGif(item.mimeType)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            localPath == null -> PreviewPlaceholder(item)
            isVideo -> if (isCurrentPage) {
                VideoPlayerView(exoPlayer = exoPlayer)
            } else {
                PreviewPlaceholder(item)
            }
            isGif -> GifPage(localPath = localPath)
            else -> ImagePage(localPath = localPath)
        }
    }
}

@Composable
private fun PreviewPlaceholder(item: MediaViewerItem) {
    if (item.previewUrl != null) {
        AsyncImage(
            model = item.previewUrl,
            contentDescription = item.fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun GifPage(localPath: String) {
    AndroidView(
        factory = { ctx -> GifImageView(ctx).apply { setImageDrawable(GifDrawable(localPath)) } },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ImagePage(localPath: String) {
    AndroidView(
        factory = { ctx ->
            PhotoView(ctx).apply {
                maximumScale = MAX_SCALE
                mediumScale = MEDIUM_SCALE
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

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(exoPlayer: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ThumbnailStrip(
    group: MediaViewerGroup,
    currentItem: MediaViewerItem?,
    modifier: Modifier = Modifier,
    onThumbnailClick: (MediaViewerItem) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val currentIndex = currentItem?.let { item -> group.items.indexOfFirst { it.messageId == item.messageId } } ?: -1

    LaunchedEffect(group.key, currentIndex) {
        if (currentIndex < 0) return@LaunchedEffect
        val itemWidthPx = with(density) { (thumbnailSize + thumbnailSpacing).toPx() }
        val viewportWidthPx = listState.layoutInfo.viewportSize.width
        val centerOffset = (viewportWidthPx / 2f - itemWidthPx / 2f).toInt()
        listState.animateScrollToItem(currentIndex, -centerOffset)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(thumbnailSpacing),
        contentPadding = PaddingValues(horizontal = thumbnailStripPadding),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = TOOLBAR_ALPHA))
            .padding(vertical = thumbnailStripPadding)
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
