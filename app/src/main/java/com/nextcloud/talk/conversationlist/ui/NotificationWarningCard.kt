/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R

@Composable
fun NotificationWarningCard(visible: Boolean, onNotNow: () -> Unit, onShowSettings: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(R.dimen.standard_margin),
                    vertical = dimensionResource(R.dimen.standard_half_margin)
                )
        ) {
            Column(
                modifier = Modifier.padding(dimensionResource(R.dimen.margin_between_elements))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(R.dimen.standard_half_margin),
                            vertical = dimensionResource(R.dimen.standard_half_margin)
                        ),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_notification_settings_24px),
                        contentDescription = null,
                        modifier = Modifier.size(dimensionResource(R.dimen.iconized_single_line_item_icon_size))
                    )
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_half_margin)))
                    Text(
                        text = stringResource(R.string.nc_notification_warning),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onNotNow) {
                        Text(text = stringResource(R.string.nc_not_now))
                    }
                    TextButton(onClick = onShowSettings) {
                        Text(text = stringResource(R.string.nc_settings))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "RTL Arabic", locale = "ar")
@Composable
private fun NotificationWarningCardVisiblePreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        NotificationWarningCard(
            visible = true,
            onNotNow = {},
            onShowSettings = {}
        )
    }
}
