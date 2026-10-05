/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.FileUtils
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_ADD_MORE_FILES = 10

private const val APP_BAR_HEIGHT_DP = 64
private const val APP_BAR_HORIZONTAL_PADDING_DP = 4
private const val SELECTION_MARK_SIZE_DP = 32
private const val SELECTION_MARK_BORDER_DP = 2
private const val SCRIM_ALPHA = 0.6f
private const val TOOL_ROW_SPACING_DP = 12

/**
 * Full-screen dialog content for reviewing, selecting, editing and captioning files picked for
 * upload, hosted by [FileAttachmentPreviewFragment]. [viewModel] owns the file list, the selection
 * and its (IO-derived) descriptions so they survive configuration changes; everything else here is
 * ephemeral UI state.
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
    var allowUpdate by rememberSaveable { mutableStateOf(false) }
    var drawingUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentFiles.toSet(), compressImages) {
        viewModel.describeFiles(compressImages)
    }
    val fileDescriptions = currentFiles.mapNotNull { viewModel.descriptionsByUri[it] }

    AutoStartDrawing(viewModel, fileDescriptions) {
        viewModel.drawing.clear()
        drawingUri = it
    }

    val pagerState = rememberPagerState(pageCount = { fileDescriptions.size })
    val coroutineScope = rememberCoroutineScope()
    val currentDescription = fileDescriptions.getOrNull(pagerState.currentPage)

    val pickMoreMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_ADD_MORE_FILES)
    ) { uris ->
        viewModel.addFiles(uris.map { it.toString() })
    }
    val cameraCapture = rememberCameraCaptureActions(currentFiles)
    val startCrop = rememberCropLauncher(viewModel)

    LaunchedEffect(viewModel.editFailed) {
        if (viewModel.editFailed) {
            Toast.makeText(context, R.string.nc_attachment_edit_failed, Toast.LENGTH_LONG).show()
            viewModel.editFailureShown()
        }
    }

    LaunchedEffect(currentFiles.size) {
        if (currentFiles.isEmpty()) {
            onDismiss()
        }
    }

    val density = LocalDensity.current
    var bottomPanelHeight by remember { mutableStateOf(0.dp) }
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + APP_BAR_HEIGHT_DP.dp

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (fileDescriptions.isNotEmpty()) {
            LargePreview(
                descriptions = fileDescriptions,
                pagerState = pagerState,
                videoPadding = PaddingValues(top = topBarHeight, bottom = bottomPanelHeight),
                detailTopPadding = topBarHeight
            )
        }

        PreviewTopBar(
            conversationName = conversationName,
            position = pagerState.currentPage to fileDescriptions.size,
            selectedCount = viewModel.selectedFiles().size,
            currentSelected = currentDescription?.let { it.uri !in viewModel.unselected } ?: false,
            onToggleSelected = { currentDescription?.let { viewModel.toggleSelected(it.uri) } },
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomPanelHeight = with(density) { it.height.toDp() } }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))))
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (fileDescriptions.size > 1) {
                ThumbnailStrip(
                    descriptions = fileDescriptions,
                    selectedIndex = pagerState.currentPage,
                    unselected = viewModel.unselected.toSet(),
                    onSelect = { index -> coroutineScope.launch { pagerState.scrollToPage(index) } },
                    onReorder = { from, to -> viewModel.reorder(from, to) }
                )
            }

            CaptionInputBar(
                caption = caption,
                onCaptionChange = { caption = it },
                addMoreActions = AddMoreActions(
                    onPickFromGallery = {
                        pickMoreMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                    },
                    onTakePhoto = cameraCapture.onTakePhoto,
                    onTakeVideo = cameraCapture.onTakeVideo
                )
            )

            BottomToolRow(
                options = ToolBarState(
                    showQuality = hasCompressibleMedia,
                    highQuality = !compressImages,
                    showPermission = showFilePermissionsOption,
                    allowUpdate = allowUpdate
                ),
                current = currentDescription?.takeUnless { viewModel.isEditing },
                onCrop = startCrop,
                onDraw = {
                    viewModel.drawing.clear()
                    drawingUri = it.uri
                },
                onHighQualityChange = { highQuality -> compressImages = !highQuality },
                onAllowUpdateChange = { allowUpdate = it },
                sendEnabled = viewModel.selectedFiles().isNotEmpty() && !viewModel.isEditing,
                onSend = { onSend(viewModel.selectedFiles(), caption, compressImages, allowUpdate) }
            )
        }

        DrawingOverlay(
            target = fileDescriptions.firstOrNull { it.uri == drawingUri },
            session = viewModel.drawing,
            onCancel = {
                viewModel.drawing.clear()
                drawingUri = null
            },
            onDone = { target ->
                viewModel.saveDrawing(target)
                drawingUri = null
            }
        )
    }
}

/** Opened from the media viewer's draw button: go straight into the drawing editor once the photo is described. */
@Composable
private fun AutoStartDrawing(
    viewModel: FileAttachmentPreviewViewModel,
    descriptions: List<FileDescription>,
    onStart: (String) -> Unit
) {
    val target = viewModel.autoDrawUri?.let { uri -> descriptions.firstOrNull { it.uri == uri } }
    LaunchedEffect(target) {
        if (target != null) {
            if (target.kind == MediaKind.IMAGE && target.aspectRatio != null) {
                onStart(target.uri)
            }
            viewModel.autoDrawStarted()
        }
    }
}

/** The edit tools / quality / permission pill on the left, the round send button on the right. */
@Suppress("LongParameterList")
@Composable
internal fun BottomToolRow(
    options: ToolBarState,
    current: FileDescription?,
    onCrop: (FileDescription) -> Unit,
    onDraw: (FileDescription) -> Unit,
    onHighQualityChange: (Boolean) -> Unit,
    onAllowUpdateChange: (Boolean) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit
) {
    // Edit tools exist for images only; drawing additionally needs a known aspect ratio to map touches.
    val image = current?.takeIf { it.kind == MediaKind.IMAGE }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TOOL_ROW_SPACING_DP.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        // The pill gets all the width the send button leaves; it scrolls instead of squeezing its buttons.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            AttachmentToolBar(
                state = options,
                actions = ToolBarActions(
                    onCrop = image?.let { { onCrop(it) } },
                    onDraw = image?.takeIf { it.aspectRatio != null }?.let { { onDraw(it) } },
                    onHighQualityChange = onHighQualityChange,
                    onAllowUpdateChange = onAllowUpdateChange
                )
            )
        }
        SendButton(enabled = sendEnabled, onClick = onSend)
    }
}

@Composable
private fun DrawingOverlay(
    target: FileDescription?,
    session: DrawingSession,
    onCancel: () -> Unit,
    onDone: (FileDescription) -> Unit
) {
    val ratio = target?.aspectRatio ?: return
    DrawingEditor(
        imageUri = target.uri,
        aspectRatio = ratio,
        session = session,
        onCancel = onCancel,
        onDone = { onDone(target) }
    )
}

/** Launches uCrop on a file and swaps the cropped/rotated result in at the original's position. */
@Composable
private fun rememberCropLauncher(viewModel: FileAttachmentPreviewViewModel): (FileDescription) -> Unit {
    val context = LocalContext.current
    var sourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationPath by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = sourceUri
        val destination = destinationPath?.let(::File)
        val cropped = result.resultCode == Activity.RESULT_OK && result.data?.let { UCrop.getOutput(it) } != null
        if (cropped && source != null && destination != null && destination.length() > 0) {
            viewModel.applyCrop(source, destination)
        } else {
            // cancelled or failed: don't leave uCrop's empty/partial output behind
            destination?.delete()
        }
        sourceUri = null
        destinationPath = null
    }

    return { description ->
        val destination = createEditOutputFile(context, description.name, description.mimeType)
        if (destination == null) {
            Toast.makeText(context, R.string.nc_attachment_edit_failed, Toast.LENGTH_LONG).show()
        } else {
            sourceUri = description.uri
            destinationPath = destination.absolutePath
            launcher.launch(createCropIntent(context, description.uri.toUri(), destination, description.mimeType))
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PreviewTopBar(
    conversationName: String,
    position: Pair<Int, Int>,
    selectedCount: Int,
    currentSelected: Boolean,
    onToggleSelected: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM_ALPHA), Color.Transparent)))
            .statusBarsPadding()
            .height(APP_BAR_HEIGHT_DP.dp)
            .padding(horizontal = APP_BAR_HORIZONTAL_PADDING_DP.dp)
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.nc_common_dismiss),
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = APP_BAR_HORIZONTAL_PADDING_DP.dp)
        ) {
            Text(
                text = conversationName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (position.second > 1) {
                Text(
                    text = stringResource(R.string.nc_attachment_position, position.first + 1, position.second),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        SelectionMark(
            selected = currentSelected,
            onClick = onToggleSelected,
            modifier = Modifier.padding(end = 4.dp)
        )
        SelectedCount(selectedCount)
    }
}

@Composable
private fun SelectedCount(count: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(SELECTION_MARK_SIZE_DP.dp)
            .border(SELECTION_MARK_BORDER_DP.dp, Color.White, CircleShape)
    ) {
        Text(text = count.toString(), style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** The round tick that includes/excludes the current file; the last selected file can't be un-ticked. */
@Composable
private fun SelectionMark(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.nc_attachment_include)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(SELECTION_MARK_SIZE_DP.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(SELECTION_MARK_BORDER_DP.dp, if (selected) Color.Transparent else Color.White, CircleShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = description }
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
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
