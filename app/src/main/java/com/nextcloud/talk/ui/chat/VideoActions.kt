/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.chat

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.mediaviewer.model.VideoAction

@Composable
fun VideoActions(
    onAction: (VideoAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pipEnabled: Boolean = enabled
) {
    val context = LocalContext.current
    val supportsPip = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        IconButton(onClick = { onAction(VideoAction.DOWNLOAD) }, enabled = enabled) {
            Icon(
                painterResource(R.drawable.baseline_download_24),
                stringResource(R.string.video_download),
                Modifier.size(24.dp)
            )
        }
        if (supportsPip) {
            IconButton(
                onClick = { onAction(VideoAction.PICTURE_IN_PICTURE) },
                enabled = pipEnabled
            ) {
                Icon(
                    painterResource(R.drawable.ic_baseline_picture_in_picture_alt_24),
                    stringResource(R.string.video_picture_in_picture),
                    Modifier.size(24.dp)
                )
            }
        }
    }
}
