/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap

private const val PREVIEW_THUMBNAIL_PX = 64

// Coil can't load the image tiles' content in a preview, so only the video tile gets a stand-in
// bitmap - the flat color is enough to tell "has a thumbnail" apart from the icon fallback.
private fun previewVideoThumbnail(): Bitmap =
    createBitmap(PREVIEW_THUMBNAIL_PX, PREVIEW_THUMBNAIL_PX)
        .apply { eraseColor(android.graphics.Color.DKGRAY) }

private fun previewDescription(
    uri: String,
    name: String,
    mimeType: String,
    detail: String,
    videoThumbnail: Bitmap? = null
) = FileDescription(
    uri = uri,
    name = name,
    kind = mediaKind(mimeType),
    mimeType = mimeType,
    detail = detail,
    videoThumbnail = videoThumbnail
)

private fun previewDescriptions() =
    listOf(
        previewDescription("file:///sdcard/DCIM/photo.jpg", "photo.jpg", "image/jpeg", "2048×1152, 210 kB"),
        previewDescription(
            uri = "file:///sdcard/DCIM/clip.mp4",
            name = "clip.mp4",
            mimeType = "video/mp4",
            detail = "0:42, 8 MB",
            videoThumbnail = previewVideoThumbnail()
        ),
        previewDescription("file:///sdcard/Download/archive.zip", "archive.zip", "application/zip", "4 MB"),
        previewDescription("file:///sdcard/Documents/report.pdf", "report.pdf", "application/pdf", "820 kB")
    )

@Composable
private fun ThumbnailStripPreviewContainer(descriptions: List<FileDescription>, selectedIndex: Int) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ThumbnailStrip(
                descriptions = descriptions,
                selectedIndex = selectedIndex,
                onSelect = {},
                onRemove = {},
                onReorder = { _, _ -> },
                onAddMore = {},
                onTakePhoto = {},
                onTakeVideo = {}
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun ThumbnailStripPreview() {
    ThumbnailStripPreviewContainer(descriptions = previewDescriptions(), selectedIndex = 0)
}

/** The mimetype-icon tiles, where [mimetypeIconTint] decides whether an icon is themed or keeps its own colors. */
@Preview(name = "Documents Light", showBackground = true)
@Preview(
    name = "Documents Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun ThumbnailStripDocumentsPreview() {
    ThumbnailStripPreviewContainer(
        descriptions = listOf(
            previewDescription("file:///sdcard/Download/archive.zip", "archive.zip", "application/zip", "4 MB"),
            previewDescription("file:///sdcard/Documents/report.pdf", "report.pdf", "application/pdf", "820 kB"),
            previewDescription("file:///sdcard/Documents/notes.txt", "notes.txt", "text/plain", "2 kB"),
            previewDescription("file:///sdcard/Documents/letter.doc", "letter.doc", "application/msword", "34 kB")
        ),
        selectedIndex = 1
    )
}

/** A single file shows no thumbnails at all - just the centered action tiles. */
@Preview(name = "Single File", showBackground = true)
@Composable
private fun ThumbnailStripSingleFilePreview() {
    ThumbnailStripPreviewContainer(
        descriptions = previewDescriptions().take(1),
        selectedIndex = 0
    )
}
