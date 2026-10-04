/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private val previewImage = FileDescription(
    uri = "file:///sdcard/DCIM/photo.jpg",
    name = "photo.jpg",
    kind = MediaKind.IMAGE,
    mimeType = "image/jpeg",
    detail = "4000×3000, 3 MB",
    aspectRatio = 1.33f
)

@Composable
private fun BottomToolRowPreviewContainer(allowUpdate: Boolean = false) {
    MaterialTheme {
        BottomToolRow(
            options = ToolBarState(
                showQuality = true,
                highQuality = false,
                showPermission = true,
                allowUpdate = allowUpdate
            ),
            current = previewImage,
            onCrop = {},
            onDraw = {},
            onHighQualityChange = {},
            onAllowUpdateChange = {},
            sendEnabled = true,
            onSend = {}
        )
    }
}

@Preview(name = "Narrow 360dp", widthDp = 360, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Phone 393dp", widthDp = 393, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Landscape 850dp", widthDp = 850, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Side pane 500dp", widthDp = 500, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BottomToolRowWidthsPreview() {
    BottomToolRowPreviewContainer()
}

@Preview(
    name = "Huawei 348dp ru, font 1.3",
    widthDp = 348,
    fontScale = 1.3f,
    locale = "ru",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun BottomToolRowOwnerDevicePreview() {
    BottomToolRowPreviewContainer(allowUpdate = true)
}
