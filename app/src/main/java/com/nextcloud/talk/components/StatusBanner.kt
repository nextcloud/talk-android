/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

/**
 * Row of full-width red banners for offline/maintenance-mode status, meant to sit at the very
 * top of a screen. Self-contained regarding the status bar inset: [Modifier.statusBarsPadding] is
 * only applied when a row is actually visible, so a screen that drops this in gets zero extra
 * space reserved in the common case (nothing showing) and correct clearance from the status bar
 * whenever a row appears.
 */
@Composable
fun StatusBannerRow(isOffline: Boolean, isMaintenanceMode: Boolean) {
    val showBanner = isOffline || isMaintenanceMode
    Column(modifier = if (showBanner) Modifier.statusBarsPadding() else Modifier) {
        AnimatedVisibility(
            visible = isOffline,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = stringResource(R.string.connection_lost),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.nc_darkRed))
                    .padding(4.dp),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        AnimatedVisibility(
            visible = isMaintenanceMode,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = stringResource(R.string.nc_dialog_maintenance_mode_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.nc_darkRed))
                    .padding(4.dp),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBannerOfflinePreview() {
    StatusBannerRow(isOffline = true, isMaintenanceMode = false)
}

@Preview(showBackground = true)
@Composable
fun StatusBannerMaintenancePreview() {
    StatusBannerRow(isOffline = false, isMaintenanceMode = true)
}

@Preview(showBackground = true)
@Composable
fun StatusBannerBothPreview() {
    StatusBannerRow(isOffline = true, isMaintenanceMode = true)
}

@Preview(showBackground = true)
@Composable
fun StatusBannerNonePreview() {
    StatusBannerRow(isOffline = false, isMaintenanceMode = false)
}
