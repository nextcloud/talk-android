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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.FileUtils
import kotlinx.coroutines.launch

private const val MAX_ADD_MORE_FILES = 10

/**
 * Full-screen dialog content for reviewing, reordering and captioning files picked for upload,
 * hosted by [FileAttachmentPreviewFragment]. [viewModel] owns the file list and its (IO-derived)
 * descriptions so both survive configuration changes; everything else here is ephemeral UI state.
 */
@Suppress("LongMethod")
@Composable
internal fun FileAttachmentPreviewContent(
    viewModel: FileAttachmentPreviewViewModel,
    conversationName: String,
    initialCompressImages: Boolean,
    onDismiss: () -> Unit,
    onSend: (files: List<String>, caption: String, compressImages: Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentFiles = viewModel.files
    val hasCompressibleMedia = currentFiles.any { isCompressible(FileUtils.resolveMimeType(context, it.toUri())) }
    var caption by rememberSaveable { mutableStateOf("") }
    var compressImages by rememberSaveable { mutableStateOf(hasCompressibleMedia && initialCompressImages) }
    var hqToggleVersion by rememberSaveable { mutableStateOf(0) }

    // Keyed by the set of files (not their order), so dragging to reorder doesn't trigger a
    // re-describe round trip through Dispatchers.IO — that async gap was causing a brief mismatch
    // between the still-old-ordered list and the already-updated drag state. Reordering just
    // re-maps against the existing descriptions synchronously below.
    LaunchedEffect(currentFiles.toSet(), compressImages) {
        viewModel.describeFiles(compressImages)
    }
    val fileDescriptions = currentFiles.mapNotNull { viewModel.descriptionsByUri[it] }

    // HeroPreview's HorizontalPager is keyed by file uri, so Pager itself already keeps
    // currentPage pointing at the same uri when the list is reordered (PagerState's built-in
    // matchScrollPositionWithKey) — no manual correction needed here.
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
            PreviewTopBar(
                conversationName = conversationName,
                hasCompressibleMedia = hasCompressibleMedia,
                highQuality = !compressImages,
                onDismiss = onDismiss,
                onHighQualityChange = { highQuality ->
                    compressImages = !highQuality
                    hqToggleVersion++
                }
            )

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
                    onRemove = { uri -> viewModel.removeFile(uri) },
                    onReorder = { from, to -> viewModel.reorder(from, to) },
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

@Composable
private fun PreviewTopBar(
    conversationName: String,
    hasCompressibleMedia: Boolean,
    highQuality: Boolean,
    onDismiss: () -> Unit,
    onHighQualityChange: (Boolean) -> Unit
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
            HighQualityToggle(checked = highQuality, onCheckedChange = onHighQualityChange)
        }
    }
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
            onSend = { _, _, _ -> }
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
            onSend = { _, _, _ -> }
        )
    }
}
