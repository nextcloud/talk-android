/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.content.Context
import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.ImageCompressor
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileAttachmentPreviewContent(
    files: List<String>,
    initialCompressImages: Boolean,
    onDismiss: () -> Unit,
    onSend: (caption: String, compressImages: Boolean) -> Unit
) {
    val context = LocalContext.current
    val hasCompressibleMedia = remember(files) {
        files.any { isCompressible(FileUtils.resolveMimeType(context, it.toUri())) }
    }
    var caption by rememberSaveable { mutableStateOf("") }
    var compressImages by rememberSaveable { mutableStateOf(hasCompressibleMedia && initialCompressImages) }

    val fileDescriptions by produceState(initialValue = emptyList<FileDescription>(), files, compressImages) {
        value = withContext(Dispatchers.IO) {
            files.map { describeFile(context, it, compressImages) }
        }
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.nc_add_file),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .heightIn(max = FILE_LIST_MAX_HEIGHT_DP.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                fileDescriptions.forEach { description ->
                    FileDescriptionRow(description)
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text(stringResource(R.string.nc_caption)) },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (hasCompressibleMedia) {
                CompressImagesCheckbox(
                    checked = compressImages,
                    onCheckedChange = { compressImages = it }
                )
            }

            HorizontalDivider()

            ActionButtons(
                onDismiss = onDismiss,
                onSend = { onSend(caption, compressImages) }
            )
        }
    }
}

private fun isCompressible(mimeType: String?): Boolean =
    ImageCompressor.isCompressible(mimeType) || VideoCompressor.isCompressible(mimeType)

@Composable
private fun CompressImagesCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, end = 16.dp, bottom = 8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringResource(R.string.nc_compress_images))
    }
}

@Composable
private fun ActionButtons(onDismiss: () -> Unit, onSend: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.nc_no))
        }
        TextButton(onClick = onSend) {
            Text(stringResource(R.string.nc_yes))
        }
    }
}

private enum class MediaKind { IMAGE, VIDEO, OTHER }

private data class FileDescription(val uri: String, val name: String, val kind: MediaKind, val detail: String?)

private const val THUMBNAIL_SIZE_DP = 40
private const val FILE_LIST_MAX_HEIGHT_DP = 240

@Composable
private fun FileDescriptionRow(description: FileDescription) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        FileThumbnail(description)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = description.name, style = MaterialTheme.typography.bodyLarge)
            description.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileThumbnail(description: FileDescription) {
    when (description.kind) {
        MediaKind.IMAGE ->
            AsyncImage(
                model = description.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(THUMBNAIL_SIZE_DP.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

        MediaKind.VIDEO ->
            Icon(
                painter = painterResource(R.drawable.ic_mimetype_video),
                contentDescription = null,
                modifier = Modifier.size(THUMBNAIL_SIZE_DP.dp)
            )

        MediaKind.OTHER ->
            Icon(
                painter = painterResource(R.drawable.baseline_insert_drive_file_24),
                contentDescription = null,
                modifier = Modifier.size(THUMBNAIL_SIZE_DP.dp)
            )
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
    val kind = mediaKind(FileUtils.resolveMimeType(context, uri))
    val file = FileUtils.getFileFromUri(context, uri) ?: return FileDescription(uriString, name, kind, null)
    val sizeOnly = formatSize(context, file.length())

    val detail = when (kind) {
        MediaKind.IMAGE -> describeImageDetail(context, file, compress, sizeOnly)
        MediaKind.VIDEO -> describeVideoDetail(context, file, compress, sizeOnly)
        MediaKind.OTHER -> sizeOnly
    }
    return FileDescription(uriString, name, kind, detail)
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
                "file:///sdcard/Documents/report.pdf"
            ),
            initialCompressImages = true,
            onDismiss = {},
            onSend = { _, _ -> }
        )
    }
}
