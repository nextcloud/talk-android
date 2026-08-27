/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.FileUtils
import kotlinx.coroutines.launch

private const val MAX_ADD_MORE_FILES = 10

private const val APP_BAR_HEIGHT_DP = 64
private const val APP_BAR_HORIZONTAL_PADDING_DP = 4

/**
 * Full-screen dialog content for reviewing, reordering and captioning files picked for upload,
 * hosted by [FileAttachmentPreviewFragment]. [viewModel] owns the file list and its (IO-derived)
 * descriptions so both survive configuration changes; everything else here is ephemeral UI state.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun FileAttachmentPreviewContent(
    viewModel: FileAttachmentPreviewViewModel,
    conversationName: String,
    initialCompressImages: Boolean,
    showFilePermissionsOption: Boolean = false,
    onDismiss: () -> Unit,
    onSend: (files: List<String>, caption: String, compressImages: Boolean, allowUpdate: Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentFiles = viewModel.files
    val hasCompressibleMedia = currentFiles.any { isCompressible(FileUtils.resolveMimeType(context, it.toUri())) }
    var caption by rememberSaveable { mutableStateOf("") }
    var compressImages by rememberSaveable { mutableStateOf(hasCompressibleMedia && initialCompressImages) }
    // Deliberately not remembered between uploads, so an exception stays an exception.
    var allowUpdate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentFiles.toSet(), compressImages) {
        viewModel.describeFiles(compressImages)
    }
    val fileDescriptions = currentFiles.mapNotNull { viewModel.descriptionsByUri[it] }

    val pagerState = rememberPagerState(pageCount = { fileDescriptions.size })
    val coroutineScope = rememberCoroutineScope()

    val pickMoreMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_ADD_MORE_FILES)
    ) { uris ->
        viewModel.addFiles(uris.map { it.toString() })
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
            PreviewTopBar(conversationName = conversationName, onDismiss = onDismiss)

            HorizontalDivider()

            Column(modifier = Modifier.weight(1f)) {
                if (fileDescriptions.isNotEmpty()) {
                    LargePreview(
                        descriptions = fileDescriptions,
                        pagerState = pagerState,
                        modifier = Modifier.weight(1f)
                    )

                    ThumbnailStrip(
                        descriptions = fileDescriptions,
                        selectedIndex = pagerState.currentPage,
                        onSelect = { index -> coroutineScope.launch { pagerState.scrollToPage(index) } },
                        onRemove = { uri -> viewModel.removeFile(uri) },
                        onReorder = { from, to -> viewModel.reorder(from, to) },
                        onAddMore = {
                            pickMoreMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                        },
                        onTakePhoto = cameraCapture.onTakePhoto,
                        onTakeVideo = cameraCapture.onTakeVideo
                    )
                }
            }

            if (hasCompressibleMedia) {
                MediaQualitySegmentedButton(
                    highQuality = !compressImages,
                    onHighQualityChange = { highQuality -> compressImages = !highQuality },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (showFilePermissionsOption) {
                FilePermissionSegmentedButton(
                    allowUpdate = allowUpdate,
                    onAllowUpdateChange = { allowUpdate = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            CaptionInputBar(
                caption = caption,
                onCaptionChange = { caption = it },
                sendEnabled = currentFiles.isNotEmpty(),
                onSend = { onSend(currentFiles.toList(), caption, compressImages, allowUpdate) }
            )
        }
    }
}

@Composable
private fun PreviewTopBar(conversationName: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(APP_BAR_HEIGHT_DP.dp)
            .padding(horizontal = APP_BAR_HORIZONTAL_PADDING_DP.dp)
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.nc_common_dismiss)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = APP_BAR_HORIZONTAL_PADDING_DP.dp)
        ) {
            Text(
                text = conversationName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.nc_add_file),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaQualitySegmentedButton(
    highQuality: Boolean,
    onHighQualityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = highQuality,
            onClick = { onHighQualityChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.high_quality_24px),
                    contentDescription = null,
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.nc_media_quality_original)) }
        )
        SegmentedButton(
            selected = !highQuality,
            onClick = { onHighQualityChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.high_quality_off_24px),
                    contentDescription = null,
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.nc_media_quality_reduced)) }
        )
    }
}

@Composable
private fun FilePermissionSegmentedButton(
    allowUpdate: Boolean,
    onAllowUpdateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = !allowUpdate,
            onClick = { onAllowUpdateChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.lock_24px),
                    contentDescription = null,
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.nc_file_permission_view_only)) }
        )
        SegmentedButton(
            selected = allowUpdate,
            onClick = { onAllowUpdateChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.edit_24px),
                    contentDescription = null,
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.nc_file_permission_editable)) }
        )
    }
}

@Composable
private fun rememberPreviewViewModel(files: List<String>): FileAttachmentPreviewViewModel {
    val context = LocalContext.current
    return remember { FileAttachmentPreviewViewModel(context).apply { setInitialFiles(files) } }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(name = "RTL Arabic", showBackground = true, locale = "ar")
@Composable
private fun FileAttachmentPreviewContentPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        FileAttachmentPreviewContent(
            viewModel = rememberPreviewViewModel(
                listOf(
                    "file:///sdcard/DCIM/photo.jpg",
                    "file:///sdcard/DCIM/video.mp4",
                    "file:///sdcard/Documents/report.pdf",
                    "file:///sdcard/DCIM/photo2.jpg"
                )
            ),
            conversationName = "Team Chat",
            initialCompressImages = true,
            onDismiss = {},
            onSend = { _, _, _, _ -> }
        )
    }
}

@Preview(name = "Single File", showBackground = true)
@Composable
private fun FileAttachmentPreviewContentSingleFilePreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        FileAttachmentPreviewContent(
            viewModel = rememberPreviewViewModel(listOf("file:///sdcard/DCIM/photo.jpg")),
            conversationName = "Team Chat",
            initialCompressImages = true,
            onDismiss = {},
            onSend = { _, _, _, _ -> }
        )
    }
}
