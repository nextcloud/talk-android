/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chooseaccount.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.status.StatusType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineStatusModalBottomSheet(
    currentStatusType: StatusType?,
    onStatusSelected: (StatusType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        OnlineStatusSheetContent(
            currentStatusType = currentStatusType,
            onStatusSelected = { statusType ->
                onStatusSelected(statusType)
                onDismiss()
            }
        )
    }
}

@Composable
fun OnlineStatusSheetContent(
    currentStatusType: StatusType?,
    modifier: Modifier = Modifier,
    onStatusSelected: (StatusType) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.online_status),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OnlineStatusRow(
            iconRes = R.drawable.online_status,
            headline = stringResource(R.string.online),
            subtitle = null,
            selected = currentStatusType == StatusType.ONLINE,
            onClick = { onStatusSelected(StatusType.ONLINE) }
        )
        OnlineStatusRow(
            iconRes = R.drawable.ic_user_status_away,
            headline = stringResource(R.string.away),
            subtitle = null,
            selected = currentStatusType == StatusType.AWAY,
            onClick = { onStatusSelected(StatusType.AWAY) }
        )
        OnlineStatusRow(
            iconRes = R.drawable.ic_user_status_busy,
            headline = stringResource(R.string.busy),
            subtitle = null,
            selected = currentStatusType == StatusType.BUSY,
            onClick = { onStatusSelected(StatusType.BUSY) }
        )
        OnlineStatusRow(
            iconRes = R.drawable.ic_user_status_dnd,
            headline = stringResource(R.string.dnd),
            subtitle = stringResource(R.string.mute_all_notifications),
            selected = currentStatusType == StatusType.DND,
            onClick = { onStatusSelected(StatusType.DND) }
        )
        OnlineStatusRow(
            iconRes = R.drawable.ic_user_status_invisible,
            headline = stringResource(R.string.invisible),
            subtitle = stringResource(R.string.appear_offline),
            selected = currentStatusType == StatusType.INVISIBLE,
            onClick = { onStatusSelected(StatusType.INVISIBLE) }
        )
    }
}

@Composable
private fun OnlineStatusRow(iconRes: Int, headline: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(R.dimen.standard_half_padding)),
        shape = RoundedCornerShape(dimensionResource(R.dimen.status_corner_radius)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.standard_padding), vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_margin)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL · Arabic", locale = "ar")
@Composable
private fun PreviewOnlineStatusSheet() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState()
        ) {
            OnlineStatusSheetContent(
                currentStatusType = StatusType.ONLINE,
                onStatusSelected = {}
            )
        }
    }
}
