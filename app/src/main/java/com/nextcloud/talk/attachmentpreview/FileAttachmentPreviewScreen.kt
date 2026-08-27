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
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.font.FontWeight
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

            if (showFilePermissionsOption || hasCompressibleMedia) {
                // Both options are filled in from the same state they control on purpose: letting a
                // button show its selection independently of what is uploaded would leave the two
                // free to drift apart.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    if (showFilePermissionsOption) {
                        FilePermissionOptionButton(
                            allowUpdate = allowUpdate,
                            onAllowUpdateChange = { allowUpdate = it }
                        )
                    }

                    if (hasCompressibleMedia) {
                        MediaQualityOptionButton(
                            highQuality = !compressImages,
                            onHighQualityChange = { highQuality -> compressImages = !highQuality }
                        )
                    }
                }
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

private const val OPTION_BUTTON_ICON_SIZE_DP = 16
private const val OPTION_BUTTON_HORIZONTAL_PADDING_DP = 12
private const val OPTION_BUTTON_VERTICAL_PADDING_DP = 6
private const val OPTION_BUTTON_LABEL_PADDING_DP = 4
private const val TEXT_BADGE_BORDER_DP = 1
private const val TEXT_BADGE_CORNER_RADIUS_DP = 4
private const val TEXT_BADGE_HORIZONTAL_PADDING_DP = 3

/**
 * A button that shows the option it currently has selected and offers the alternatives in a
 * dropdown menu: gray as long as it holds the option a share starts with (the default), and
 * filled with the theme color once it does not - the same pair of colors and the same
 * gray-unless-an-exception idea the upload option buttons of the iOS app use.
 */
@Composable
private fun OptionButton(label: String, isDefault: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val colors = if (isDefault) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(
            horizontal = OPTION_BUTTON_HORIZONTAL_PADDING_DP.dp,
            vertical = OPTION_BUTTON_VERTICAL_PADDING_DP.dp
        )
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = OPTION_BUTTON_LABEL_PADDING_DP.dp)
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(OPTION_BUTTON_ICON_SIZE_DP.dp)
        )
    }
}

/**
 * A short text in a bordered box, standing in for the SD/HD badges iOS draws next to its quality
 * option - SF Symbols has no equivalent, so the iOS app renders them the same way.
 */
@Composable
private fun TextBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = LocalContentColor.current,
        modifier = Modifier
            .border(
                width = TEXT_BADGE_BORDER_DP.dp,
                color = LocalContentColor.current,
                shape = RoundedCornerShape(TEXT_BADGE_CORNER_RADIUS_DP.dp)
            )
            .padding(horizontal = TEXT_BADGE_HORIZONTAL_PADDING_DP.dp)
    )
}

@Composable
private fun MediaQualityOptionButton(highQuality: Boolean, onHighQualityChange: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OptionButton(
            label = stringResource(
                if (highQuality) R.string.nc_media_quality_original else R.string.nc_media_quality_reduced
            ),
            isDefault = !highQuality,
            onClick = { expanded = true },
            icon = { TextBadge(if (highQuality) "HD" else "SD") }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_media_quality_original)) },
                leadingIcon = { TextBadge("HD") },
                trailingIcon = { if (highQuality) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onHighQualityChange(true)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_media_quality_reduced)) },
                leadingIcon = { TextBadge("SD") },
                trailingIcon = { if (!highQuality) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onHighQualityChange(false)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun FilePermissionOptionButton(allowUpdate: Boolean, onAllowUpdateChange: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OptionButton(
            label = stringResource(
                if (allowUpdate) R.string.nc_file_permission_editable else R.string.nc_file_permission_view_only
            ),
            isDefault = !allowUpdate,
            onClick = { expanded = true },
            icon = {
                Icon(
                    imageVector = if (allowUpdate) Icons.Filled.Edit else Icons.Filled.EditOff,
                    contentDescription = null,
                    modifier = Modifier.size(OPTION_BUTTON_ICON_SIZE_DP.dp)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_file_permission_view_only)) },
                leadingIcon = { Icon(Icons.Filled.EditOff, contentDescription = null) },
                trailingIcon = { if (!allowUpdate) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onAllowUpdateChange(false)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_file_permission_editable)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                trailingIcon = { if (allowUpdate) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onAllowUpdateChange(true)
                    expanded = false
                }
            )
        }
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
