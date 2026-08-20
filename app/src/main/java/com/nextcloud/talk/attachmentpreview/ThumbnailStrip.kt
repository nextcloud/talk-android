/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nextcloud.talk.R
import kotlin.math.roundToInt

private const val STRIP_THUMBNAIL_SIZE_DP = 64
private const val STRIP_ICON_SIZE_DP = 28
private const val STRIP_PLAY_ICON_SIZE_DP = 28
private const val STRIP_ITEM_SPACING_DP = 8
private const val SELECTED_BORDER_WIDTH_DP = 2
private const val INDICATOR_WIDTH_DP = 3
private const val INDICATOR_GAP_OFFSET_DP = 5

@Suppress("LongParameterList")
@Composable
internal fun ThumbnailStrip(
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

    // With no file thumbnails shown (single-file case, handled below), the action tiles are the
    // only content — center them instead of leaving them stuck to the start like a scrollable list.
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
        // With a single file it's already shown in full in the large preview above, and the
        // "selected" thumbnail styling (dark overlay + trash icon) would otherwise cover the
        // only thumbnail permanently — looking like the file is already marked for deletion
        // rather than just being the one file that was picked.
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
        item {
            ActionTile(
                icon = Icons.Outlined.PhotoLibrary,
                contentDescription = R.string.nc_add_more_files,
                onClick = onAddMore
            )
        }
        item {
            ActionTile(
                icon = Icons.Outlined.PhotoCamera,
                contentDescription = R.string.take_photo,
                onClick = onTakePhoto
            )
        }
        item {
            ActionTile(
                icon = Icons.Outlined.Videocam,
                contentDescription = R.string.nc_take_video,
                onClick = onTakeVideo
            )
        }
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
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.nc_remove_file),
                    tint = Color.White,
                    modifier = Modifier.size(STRIP_ICON_SIZE_DP.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, @StringRes contentDescription: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(STRIP_THUMBNAIL_SIZE_DP.dp)
            .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescription),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(STRIP_ICON_SIZE_DP.dp)
        )
    }
}
