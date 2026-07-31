/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.ImageCompressor
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Suppress("LongMethod", "LongParameterList")
@Composable
fun FileAttachmentPreviewContent(
    files: List<String>,
    conversationName: String,
    initialCompressImages: Boolean,
    onDismiss: () -> Unit,
    onSend: (files: List<String>, caption: String, compressImages: Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentFiles = remember(files) { mutableStateListOf<String>().apply { addAll(files) } }
    val hasCompressibleMedia = currentFiles.any { isCompressible(FileUtils.resolveMimeType(context, it.toUri())) }
    var caption by rememberSaveable { mutableStateOf("") }
    var compressImages by rememberSaveable { mutableStateOf(hasCompressibleMedia && initialCompressImages) }
    var hqToggleVersion by rememberSaveable { mutableStateOf(0) }

    // Keyed by the set of files (not their order), so dragging to reorder doesn't trigger a re-describe
    // round trip through Dispatchers.IO — that async gap was causing a brief mismatch between the
    // still-old-ordered list and the already-updated drag state. Reordering just re-maps synchronously below.
    val descriptionsByUri by produceState(
        initialValue = emptyMap<String, FileDescription>(),
        currentFiles.toSet(),
        compressImages
    ) {
        val filesSnapshot = currentFiles.toList()
        value = withContext(Dispatchers.IO) {
            filesSnapshot.associateWith { describeFile(context, it, compressImages) }
        }
    }
    val fileDescriptions = currentFiles.mapNotNull { descriptionsByUri[it] }
    
    val pagerState = rememberPagerState(pageCount = { fileDescriptions.size })
    val coroutineScope = rememberCoroutineScope()

    val pickMoreMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_ADD_MORE_FILES)
    ) { uris ->
        val newFiles = uris.map { it.toString() }.filterNot { it in currentFiles }
        currentFiles.addAll(newFiles)
    }

    val cameraCapture = rememberCameraCaptureActions(currentFiles)

    LaunchedEffect(currentFiles.size) {
        if (currentFiles.isEmpty()) {
            onDismiss()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.nc_common_dismiss)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversationName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.nc_add_file),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (hasCompressibleMedia) {
                    HighQualityToggle(
                        checked = !compressImages,
                        onCheckedChange = { highQuality ->
                            compressImages = !highQuality
                            hqToggleVersion++
                        }
                    )
                }
            }

            HorizontalDivider()

            if (fileDescriptions.isNotEmpty()) {
                HeroPreview(
                    descriptions = fileDescriptions,
                    pagerState = pagerState,
                    hqToggleVersion = hqToggleVersion,
                    modifier = Modifier.weight(1f)
                )

                ThumbnailStrip(
                    descriptions = fileDescriptions,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index -> coroutineScope.launch { pagerState.scrollToPage(index) } },
                    onRemove = { uri -> currentFiles.remove(uri) },
                    onReorder = { from, to ->
                        if (from != to && from in currentFiles.indices && to in currentFiles.indices) {
                            val item = currentFiles.removeAt(from)
                            currentFiles.add(to, item)
                        }
                    },
                    onAddMore = {
                        pickMoreMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                    },
                    onTakePhoto = cameraCapture.onTakePhoto,
                    onTakeVideo = cameraCapture.onTakeVideo
                )
            }

            CaptionInputBar(
                caption = caption,
                onCaptionChange = { caption = it },
                sendEnabled = currentFiles.isNotEmpty(),
                onSend = { onSend(currentFiles.toList(), caption, compressImages) }
            )
        }
    }
}

private fun isCompressible(mimeType: String?): Boolean =
    ImageCompressor.isCompressible(mimeType) || VideoCompressor.isCompressible(mimeType)

private enum class CameraCaptureType { PHOTO, VIDEO }

private const val CAMERA_FILE_DATE_PATTERN = "yyyy-MM-dd HH-mm-ss"

private fun createCameraOutputUri(context: Context, type: CameraCaptureType): Uri {
    val outputDir = FileUtils.getSharedAttachmentsDirectory(context.cacheDir) ?: context.cacheDir
    val timestamp = SimpleDateFormat(CAMERA_FILE_DATE_PATTERN, Locale.ROOT).format(Date())
    val extension = if (type == CameraCaptureType.PHOTO) "jpg" else "mp4"
    val file = File(outputDir, "$timestamp.$extension")
    return FileProvider.getUriForFile(context, context.packageName, file)
}

private data class CameraCaptureActions(val onTakePhoto: () -> Unit, val onTakeVideo: () -> Unit)

@Composable
private fun rememberCameraCaptureActions(currentFiles: MutableList<String>): CameraCaptureActions {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraPermissionType by remember { mutableStateOf<CameraCaptureType?>(null) }

    val onCaptureResult: (Boolean) -> Unit = { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            currentFiles.add(uri.toString())
        }
        pendingCameraUri = null
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCaptureResult)
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo(), onCaptureResult)

    fun launchCameraCapture(type: CameraCaptureType) {
        val uri = createCameraOutputUri(context, type)
        pendingCameraUri = uri
        when (type) {
            CameraCaptureType.PHOTO -> takePicture.launch(uri)
            CameraCaptureType.VIDEO -> captureVideo.launch(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val type = pendingCameraPermissionType
        pendingCameraPermissionType = null
        if (granted && type != null) {
            launchCameraCapture(type)
        }
    }

    fun requestCameraCapture(type: CameraCaptureType) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraCapture(type)
        } else {
            pendingCameraPermissionType = type
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return CameraCaptureActions(
        onTakePhoto = { requestCameraCapture(CameraCaptureType.PHOTO) },
        onTakeVideo = { requestCameraCapture(CameraCaptureType.VIDEO) }
    )
}

@Composable
private fun HighQualityToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val description = stringResource(R.string.nc_hq_toggle_description)
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = { Text(stringResource(R.string.nc_hq_toggle)) },
        leadingIcon = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
        modifier = Modifier.semantics { contentDescription = description }
    )
}

@Composable
private fun CaptionInputBar(
    caption: String,
    onCaptionChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit
) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.button_corner_radius))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = dimensionResource(R.dimen.min_size_clickable_area))
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (caption.isEmpty()) {
                Text(
                    text = stringResource(R.string.nc_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val sendTint = if (sendEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        IconButton(onClick = onSend, enabled = sendEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_send_24px),
                contentDescription = stringResource(R.string.nc_description_send_message_button),
                tint = sendTint
            )
        }
    }
}

private enum class MediaKind { IMAGE, VIDEO, OTHER }

private data class FileDescription(
    val uri: String,
    val name: String,
    val kind: MediaKind,
    val mimeType: String?,
    val detail: String?,
    val aspectRatio: Float? = null,
    val videoThumbnail: Bitmap? = null
)

private const val MAX_ADD_MORE_FILES = 10
private const val HERO_MAX_HEIGHT_DP = 480
private const val HERO_VERTICAL_SPACING_DP = 12
private const val HERO_ICON_SIZE_DP = 96
private const val DETAIL_CHIP_CORNER_RADIUS_PERCENT = 50
private const val DETAIL_CHIP_POP_DURATION_MS = 520
private const val DETAIL_CHIP_POP_INITIAL_SCALE = 0.85f
private const val PLAY_BUTTON_SIZE_DP = 56
private const val PLAY_ICON_SIZE_DP = 32
private const val STRIP_THUMBNAIL_SIZE_DP = 64
private const val STRIP_ICON_SIZE_DP = 28
private const val STRIP_PLAY_ICON_SIZE_DP = 28
private const val STRIP_ITEM_SPACING_DP = 8
private const val SELECTED_BORDER_WIDTH_DP = 2
private const val HERO_OTHER_BORDER_WIDTH_DP = 1
private const val HERO_OTHER_BORDER_PADDING_DP = 24
private const val THUMBNAIL_CORNER_RADIUS_DP = 16
private const val THUMBNAIL_ICON_SIZE_DP = 48
private const val VIDEO_FRAME_MAX_DIMENSION_PX = 720

@Composable
private fun HeroPreview(
    descriptions: List<FileDescription>,
    pagerState: PagerState,
    hqToggleVersion: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HERO_VERTICAL_SPACING_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> descriptions.getOrNull(page)?.uri ?: page },
            modifier = Modifier
                .fillMaxSize()
                .heightIn(max = HERO_MAX_HEIGHT_DP.dp)
        ) { page ->
            descriptions.getOrNull(page)?.let { description -> HeroPage(description, hqToggleVersion) }
        }
    }
}

@Composable
private fun HeroPage(description: FileDescription, hqToggleVersion: Int) {
    var isPlayingVideo by remember(description.uri) { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        var cardModifier = description.aspectRatio?.let { ratio ->
            val (cardWidth, cardHeight) = fitWithinBounds(maxWidth, maxHeight, ratio)
            Modifier.size(cardWidth, cardHeight)
        } ?: Modifier.fillMaxSize()

        // Non-media files have no image content of their own to visually delimit the card, so
        // without an explicit outline the icon would appear to float directly on the dialog's
        // background instead of reading as a bounded preview, unlike images/videos.
        if (description.kind == MediaKind.OTHER) {
            cardModifier = cardModifier
                .padding(HERO_OTHER_BORDER_PADDING_DP.dp)
                .border(
                    width = HERO_OTHER_BORDER_WIDTH_DP.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp)
                )
        }

        Box(modifier = cardModifier) {
            if (description.kind == MediaKind.VIDEO && isPlayingVideo) {
                VideoPlayerCard(uri = description.uri, modifier = Modifier.fillMaxSize())
            } else {
                FileThumbnailImage(
                    description,
                    iconSize = HERO_ICON_SIZE_DP.dp,
                    contentScale = ContentScale.Fit,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize()
                )

                if (description.kind == MediaKind.VIDEO) {
                    PlayButtonOverlay(
                        onClick = { isPlayingVideo = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                description.detail?.let { detail ->
                    HeroDetailOverlay(
                        detail,
                        hqToggleVersion,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayButtonOverlay(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(PLAY_BUTTON_SIZE_DP.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
            contentDescription = stringResource(R.string.media_message_content_play),
            tint = Color.White,
            modifier = Modifier.size(PLAY_ICON_SIZE_DP.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerCard(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) { ExoPlayer.Builder(context).build() }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Preparing/starting playback here (instead of alongside player creation above) ensures the
    // PlayerView below has already been created and bound via `player = exoPlayer` first. Doing
    // both synchronously in the same step let the player start decoding before its SurfaceView
    // existed, silently dropping the first playback's frames — a black screen until the *next*
    // play attempt, once the surface had already been created once.
    LaunchedEffect(exoPlayer, uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri.toUri()))
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}

private fun fitWithinBounds(maxWidth: Dp, maxHeight: Dp, ratio: Float): Pair<Dp, Dp> {
    val boundsRatio = maxWidth / maxHeight
    return if (boundsRatio > ratio) {
        (maxHeight * ratio) to maxHeight
    } else {
        maxWidth to (maxWidth / ratio)
    }
}

@Composable
private fun HeroDetailOverlay(detail: String, hqToggleVersion: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DETAIL_CHIP_CORNER_RADIUS_PERCENT))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        AnimatedContent(
            targetState = hqToggleVersion,
            transitionSpec = {
                (fadeIn(tween(DETAIL_CHIP_POP_DURATION_MS)) + scaleIn(initialScale = DETAIL_CHIP_POP_INITIAL_SCALE))
                    .togetherWith(fadeOut(tween(DETAIL_CHIP_POP_DURATION_MS)))
            },
            label = "detailChipPop"
        ) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ThumbnailStrip(
    descriptions: List<FileDescription>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onAddMore: () -> Unit,
    onTakePhoto: () -> Unit,
    onTakeVideo: () -> Unit
) {
    val density = LocalDensity.current
    val itemExtentPx = remember(density) {
        with(density) { (STRIP_THUMBNAIL_SIZE_DP + STRIP_ITEM_SPACING_DP).dp.toPx() }
    }
    val dragState = remember { DragReorderState() }

    val horizontalArrangement = if (descriptions.size > 1) {
        Arrangement.spacedBy(STRIP_ITEM_SPACING_DP.dp)
    } else {
        Arrangement.spacedBy(STRIP_ITEM_SPACING_DP.dp, Alignment.CenterHorizontally)
    }

    LazyRow(
        horizontalArrangement = horizontalArrangement,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (descriptions.size > 1) {
            itemsIndexed(descriptions, key = { _, description -> description.uri }) { index, description ->
                DraggableStripItem(
                    description = description,
                    index = index,
                    selected = index == selectedIndex,
                    itemExtentPx = itemExtentPx,
                    itemCount = descriptions.size,
                    dragState = dragState,
                    onSelect = onSelect,
                    onRemove = onRemove,
                    onReorder = onReorder
                )
            }
        }
        item { AddMoreTile(onClick = onAddMore) }
        item { TakePhotoTile(onClick = onTakePhoto) }
        item { TakeVideoTile(onClick = onTakeVideo) }
    }
}

/**
 * No live reordering happens while dragging — only [draggedIndex] (fixed for the whole gesture)
 * and [insertionIndex] (where a drop would currently land) update. The actual list mutation is a
 * single [onReorder] call on release. [insertionIndex] ranges over `0..itemCount` (not just
 * `0..lastIndex`): `itemCount` itself is the distinct "after the very last item" position, needed
 * so the drop-line can be drawn after the last thumbnail, not just before it.
 */
private class DragReorderState {
    val draggedIndex = mutableStateOf(-1)
    val dragOffsetX = mutableStateOf(0f)
    val insertionIndex = mutableStateOf(-1)
}

private const val INDICATOR_WIDTH_DP = 3
private const val INDICATOR_GAP_OFFSET_DP = 5

@Suppress("LongParameterList")
@Composable
private fun DraggableStripItem(
    description: FileDescription,
    index: Int,
    selected: Boolean,
    itemExtentPx: Float,
    itemCount: Int,
    dragState: DragReorderState,
    onSelect: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val isDragged = index == dragState.draggedIndex.value
    val currentIndex by rememberUpdatedState(index)

    Box {
        StripThumbnail(
            description = description,
            selected = selected,
            onClick = { onSelect(index) },
            onRemove = { onRemove(description.uri) },
            modifier = Modifier
                .graphicsLayer { translationX = if (isDragged) dragState.dragOffsetX.value else 0f }
                .zIndex(if (isDragged) 1f else 0f)
                .pointerInput(description.uri) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragState.draggedIndex.value = currentIndex
                            dragState.insertionIndex.value = currentIndex
                            dragState.dragOffsetX.value = 0f
                        },
                        onDragEnd = {
                            val from = dragState.draggedIndex.value
                            val insertion = dragState.insertionIndex.value
                            if (from >= 0 && insertion >= 0) {
                                val to = if (insertion > from) insertion - 1 else insertion
                                if (to != from) onReorder(from, to)
                            }
                            dragState.draggedIndex.value = -1
                            dragState.insertionIndex.value = -1
                            dragState.dragOffsetX.value = 0f
                        },
                        onDragCancel = {
                            dragState.draggedIndex.value = -1
                            dragState.insertionIndex.value = -1
                            dragState.dragOffsetX.value = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragState.dragOffsetX.value += dragAmount.x
                            val slotsMoved = (dragState.dragOffsetX.value / itemExtentPx).roundToInt()
                            dragState.insertionIndex.value = (currentIndex + slotsMoved).coerceIn(0, itemCount)
                        }
                    )
                }
        )

        DropIndicators(index, itemCount, dragState)
    }
}

@Composable
private fun BoxScope.DropIndicators(index: Int, itemCount: Int, dragState: DragReorderState) {
    if (dragState.draggedIndex.value < 0) return

    if (dragState.insertionIndex.value == index) {
        DropIndicatorLine(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-INDICATOR_GAP_OFFSET_DP).dp)
        )
    }
    if (index == itemCount - 1 && dragState.insertionIndex.value == itemCount) {
        DropIndicatorLine(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = INDICATOR_GAP_OFFSET_DP.dp)
        )
    }
}

@Composable
private fun DropIndicatorLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(INDICATOR_WIDTH_DP.dp)
            .height(STRIP_THUMBNAIL_SIZE_DP.dp)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(INDICATOR_WIDTH_DP.dp / 2)
            )
    )
}

@Composable
private fun StripThumbnail(
    description: FileDescription,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp)
    Box(
        modifier = modifier
            .size(STRIP_THUMBNAIL_SIZE_DP.dp)
            .clip(shape)
            .border(SELECTED_BORDER_WIDTH_DP.dp, borderColor, shape)
            .clickable(onClick = if (selected) onRemove else onClick)
    ) {
        FileThumbnailImage(
            description,
            iconSize = STRIP_ICON_SIZE_DP.dp,
            modifier = Modifier.fillMaxSize()
        )

        if (description.kind == MediaKind.VIDEO && !selected) {
            Box(
                modifier = Modifier
                    .size(STRIP_PLAY_ICON_SIZE_DP.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((STRIP_PLAY_ICON_SIZE_DP / 2).dp)
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.nc_remove_file),
                    tint = Color.White,
                    modifier = Modifier.size(STRIP_ICON_SIZE_DP.dp)
                )
            }
        }
    }
}

@Composable
private fun AddMoreTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(STRIP_THUMBNAIL_SIZE_DP.dp)
            .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.baseline_photo_library_24),
            contentDescription = stringResource(R.string.nc_add_more_files),
            modifier = Modifier.size(STRIP_ICON_SIZE_DP.dp)
        )
    }
}

@Composable
private fun TakePhotoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(STRIP_THUMBNAIL_SIZE_DP.dp)
            .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = stringResource(R.string.take_photo),
            modifier = Modifier.size(STRIP_ICON_SIZE_DP.dp)
        )
    }
}

@Composable
private fun TakeVideoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(STRIP_THUMBNAIL_SIZE_DP.dp)
            .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Videocam,
            contentDescription = stringResource(R.string.nc_take_video),
            modifier = Modifier.size(STRIP_ICON_SIZE_DP.dp)
        )
    }
}

@Composable
private fun FileThumbnailImage(
    description: FileDescription,
    modifier: Modifier = Modifier,
    iconSize: Dp = THUMBNAIL_ICON_SIZE_DP.dp,
    contentScale: ContentScale = ContentScale.Crop,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val shape = RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp)

    when (description.kind) {
        MediaKind.IMAGE ->
            AsyncImage(
                model = description.uri,
                contentDescription = description.name,
                contentScale = contentScale,
                modifier = modifier.clip(shape).background(backgroundColor)
            )

        MediaKind.VIDEO ->
            if (description.videoThumbnail != null) {
                Image(
                    bitmap = description.videoThumbnail.asImageBitmap(),
                    contentDescription = description.name,
                    contentScale = contentScale,
                    modifier = modifier.clip(shape).background(backgroundColor)
                )
            } else {
                Box(
                    modifier = modifier.clip(shape).background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mimetype_video),
                        contentDescription = description.name,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

        MediaKind.OTHER ->
            Box(
                modifier = modifier.clip(shape).background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(DrawableUtils.getDrawableResourceIdForMimeType(description.mimeType)),
                    contentDescription = description.name,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(iconSize)
                )
            }
    }
}

private fun mediaKind(mimeType: String?): MediaKind =
    when {
        mimeType?.startsWith(Mimetype.IMAGE_PREFIX) == true -> MediaKind.IMAGE
        mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true -> MediaKind.VIDEO
        else -> MediaKind.OTHER
    }

@Suppress("ReturnCount")
private fun describeFile(context: Context, uriString: String, compress: Boolean): FileDescription {
    val uri = uriString.toUri()
    val name = FileUtils.getFileName(uri, context)
    val mimeType = FileUtils.resolveMimeType(context, uri)
    val kind = mediaKind(mimeType)
    val file = FileUtils.getFileFromUri(context, uri)
        ?: return FileDescription(uriString, name, kind, mimeType, null)
    val sizeOnly = formatSize(context, file.length())

    val detail = when (kind) {
        MediaKind.IMAGE -> describeImageDetail(context, file, compress, sizeOnly)
        MediaKind.VIDEO -> describeVideoDetail(context, file, compress, sizeOnly)
        MediaKind.OTHER -> "$name, $sizeOnly"
    }
    val aspectRatio = when (kind) {
        MediaKind.IMAGE -> imageAspectRatio(file)
        MediaKind.VIDEO -> videoAspectRatio(file)
        MediaKind.OTHER -> null
    }
    val videoThumbnail = if (kind == MediaKind.VIDEO) extractVideoFrame(file) else null
    return FileDescription(uriString, name, kind, mimeType, detail, aspectRatio, videoThumbnail)
}

@Suppress("ReturnCount")
private fun imageAspectRatio(file: File): Float? {
    val info = ImageCompressor.readImageInfo(file) ?: return null
    if (info.height <= 0) return null
    val (width, height) = if (isSidewaysExifOrientation(file)) {
        info.height to info.width
    } else {
        info.width to info.height
    }
    if (height <= 0) return null
    return width.toFloat() / height.toFloat()
}

// BitmapFactory's decode bounds are the raw, un-rotated pixel grid, so a photo whose sensor-native
// orientation is landscape (with an EXIF tag marking it as rotated for portrait display) reports
// swapped width/height here compared to how it actually renders. Swap them back so the aspect ratio
// used to size the preview matches what Coil (which does honor EXIF) actually draws.
private fun isSidewaysExifOrientation(file: File): Boolean =
    try {
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
    } catch (e: IOException) {
        false
    }

private const val VIDEO_ROTATION_DEGREES_90 = 90
private const val VIDEO_ROTATION_DEGREES_270 = 270

@Suppress("ReturnCount")
private fun videoAspectRatio(file: File): Float? {
    val info = VideoCompressor.readVideoInfo(file) ?: return null
    if (info.height <= 0) return null
    val (width, height) = if (isSidewaysVideoRotation(file)) {
        info.height to info.width
    } else {
        info.width to info.height
    }
    if (height <= 0) return null
    return width.toFloat() / height.toFloat()
}

// MediaMetadataRetriever's width/height metadata reflect the raw, un-rotated encoded frame, so a
// video recorded in the sensor's landscape orientation (with a 90/270 rotation flag for portrait
// playback) reports swapped dimensions here compared to how it actually renders. getFrameAtTime()
// (used for the thumbnail in extractVideoFrame) already applies this rotation, so swap back here
// too, matching what's actually drawn.
@Suppress("TooGenericExceptionCaught")
private fun isSidewaysVideoRotation(file: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        rotation == VIDEO_ROTATION_DEGREES_90 || rotation == VIDEO_ROTATION_DEGREES_270
    } catch (e: Exception) {
        false
    } finally {
        retriever.release()
    }
}

@Suppress("TooGenericExceptionCaught")
private fun extractVideoFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::downscaleIfNeeded)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
    val largestDimension = maxOf(bitmap.width, bitmap.height)
    if (largestDimension <= VIDEO_FRAME_MAX_DIMENSION_PX) return bitmap
    val scale = VIDEO_FRAME_MAX_DIMENSION_PX.toFloat() / largestDimension
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

@Suppress("ReturnCount")
private fun describeImageDetail(context: Context, file: File, compress: Boolean, fallback: String): String {
    val original = ImageCompressor.readImageInfo(file) ?: return fallback
    if (!compress) return describeMedia(context, original.width, original.height, original.sizeBytes)
    val compressed = ImageCompressor.estimateCompression(file)
        ?: return describeMedia(context, original.width, original.height, original.sizeBytes)
    return describeMedia(context, compressed.width, compressed.height, compressed.sizeBytes)
}

@Suppress("ReturnCount")
private fun describeVideoDetail(context: Context, file: File, compress: Boolean, fallback: String): String {
    val original = VideoCompressor.readVideoInfo(file) ?: return fallback
    if (!compress) return describeMedia(context, original.width, original.height, original.sizeBytes)
    val compressed = VideoCompressor.estimateCompression(file)
        ?: return describeMedia(context, original.width, original.height, original.sizeBytes)
    return describeMedia(context, compressed.width, compressed.height, compressed.sizeBytes)
}

private fun describeMedia(context: Context, width: Int, height: Int, sizeBytes: Long): String =
    "$width×$height, ${formatSize(context, sizeBytes)}"

private fun formatSize(context: Context, sizeBytes: Long): String = Formatter.formatShortFileSize(context, sizeBytes)

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun FileAttachmentPreviewContentPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        FileAttachmentPreviewContent(
            files = listOf(
                "file:///sdcard/DCIM/photo.jpg",
                "file:///sdcard/DCIM/video.mp4",
                "file:///sdcard/Documents/report.pdf",
                "file:///sdcard/DCIM/photo2.jpg"
            ),
            conversationName = "Team Chat",
            initialCompressImages = true,
            onDismiss = {},
            onSend = { _, _, _ -> }
        )
    }
}

@Preview(name = "Single File", showBackground = true)
@Composable
private fun FileAttachmentPreviewContentSingleFilePreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        FileAttachmentPreviewContent(
            files = listOf("file:///sdcard/DCIM/photo.jpg"),
            conversationName = "Team Chat",
            initialCompressImages = true,
            onDismiss = {},
            onSend = { _, _, _ -> }
        )
    }
}
