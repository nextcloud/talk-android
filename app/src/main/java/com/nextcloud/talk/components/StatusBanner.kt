/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
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
import com.nextcloud.talk.data.network.ServerConnectionStatus

/**
 * Warns that the server can't be used, for any [status] other than [ServerConnectionStatus.CONNECTED]. Meant for the
 * top of a screen: while shown, it pads itself for the status bar.
 */
@Composable
fun StatusBanner(status: ServerConnectionStatus) {
    val isShown = status != ServerConnectionStatus.CONNECTED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(if (isShown) Modifier.statusBarsPadding() else Modifier)
    ) {
        StatusBannerText(
            isVisible = status == ServerConnectionStatus.SERVER_UNREACHABLE,
            textRes = R.string.connection_lost
        )
        StatusBannerText(
            isVisible = status == ServerConnectionStatus.MAINTENANCE_MODE,
            textRes = R.string.nc_dialog_maintenance_mode_description
        )
    }
}

@Composable
private fun StatusBannerText(isVisible: Boolean, @StringRes textRes: Int) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Text(
            text = stringResource(textRes),
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.nc_darkRed))
                .padding(4.dp),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBannerServerUnreachablePreview() {
    StatusBanner(ServerConnectionStatus.SERVER_UNREACHABLE)
}

@Preview(showBackground = true)
@Composable
fun StatusBannerMaintenancePreview() {
    StatusBanner(ServerConnectionStatus.MAINTENANCE_MODE)
}

@Preview(showBackground = true)
@Composable
fun StatusBannerConnectedPreview() {
    StatusBanner(ServerConnectionStatus.CONNECTED)
}
