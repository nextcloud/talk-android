/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DrawableUtils

internal const val THUMBNAIL_CORNER_RADIUS_DP = 16
private const val THUMBNAIL_ICON_SIZE_DP = 48

/**
 * Renders [description] as an image, a video frame, or a mimetype icon, depending on [FileDescription.kind].
 * Shared by the large preview pager and the thumbnail strip so both stay visually consistent.
 */
@Composable
internal fun FileThumbnailImage(
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
