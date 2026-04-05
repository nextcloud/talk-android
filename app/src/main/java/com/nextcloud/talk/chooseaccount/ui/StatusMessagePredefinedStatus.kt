/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus

@Composable
internal fun PredefinedStatusList(
    statuses: List<PredefinedStatus>,
    isBackupStatusAvailable: Boolean,
    onRevertStatus: () -> Unit,
    onSelectStatus: (PredefinedStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    if (statuses.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        if (isBackupStatusAvailable) {
            Text(
                text = stringResource(R.string.automatic_status_set),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        statuses.forEachIndexed { index, status ->
            PredefinedStatusRow(
                status = status,
                isBackupEntry = isBackupStatusAvailable && index == 0,
                onRevertStatus = onRevertStatus,
                onClick = { onSelectStatus(status) }
            )
        }
    }
}

@Composable
private fun PredefinedStatusRow(
    status: PredefinedStatus,
    isBackupEntry: Boolean,
    onRevertStatus: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val clearAtLabel = remember(status) { resolveClearAtLabel(status, context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = status.icon ?: "",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(42.dp)
        )
        if (isBackupEntry) {
            BackupStatusContent(message = status.message, onRevertStatus = onRevertStatus)
        } else {
            StandardStatusContent(message = status.message, clearAtLabel = clearAtLabel)
        }
    }
}

@Composable
private fun RowScope.BackupStatusContent(message: String, onRevertStatus: () -> Unit) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.previously_set),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.width(8.dp))
    FilledTonalButton(
        onClick = onRevertStatus,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(stringResource(R.string.reset_status))
    }
}

@Composable
private fun RowScope.StandardStatusContent(message: String, clearAtLabel: String) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (clearAtLabel.isNotEmpty()) {
            Text(
                text = stringResource(R.string.divider),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Text(
                text = clearAtLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun resolveClearAtLabel(status: PredefinedStatus, context: android.content.Context): String {
    val clearAt = status.clearAt ?: return context.getString(R.string.dontClear)
    return when (clearAt.type) {
        "period" -> when (clearAt.time) {
            "900" -> context.getString(R.string.fifteenMinutes)
            "1800" -> context.getString(R.string.thirtyMinutes)
            "3600" -> context.getString(R.string.oneHour)
            "14400" -> context.getString(R.string.fourHours)
            else -> ""
        }
        "end-of" -> if (clearAt.time == "day") {
            context.getString(R.string.today)
        } else {
            context.getString(R.string.thisWeek)
        }
        else -> ""
    }
}
