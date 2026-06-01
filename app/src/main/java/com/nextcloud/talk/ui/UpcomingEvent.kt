/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.upcomingEvents.UpcomingEvent
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun UpcomingEventView(event: UpcomingEvent, viewThemeUtils: ViewThemeUtils, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember { viewThemeUtils.getColorScheme(context) }

    val timeString = remember(event.start) {
        event.start?.let { start ->
            val startDateTime = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault())
            val currentTime = ZonedDateTime.now(ZoneId.systemDefault())
            DateUtils(context).getStringForMeetingStartDateTime(startDateTime, currentTime)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_event_24px),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            if (timeString != null) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = event.summary.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_close_24),
                        contentDescription = stringResource(R.string.nc_common_dismiss)
                    )
                }
            }
        }
    }
}

@Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun UpcomingEventPreviewDark() {
    UpcomingEventPreview()
}

@Preview(name = "R-t-L", locale = "ar")
@Composable
fun UpcomingEventPreviewRtl() {
    UpcomingEventPreview(summary = "اجتماع تنسيق الإدارة")
}

@Preview(name = "Light Mode")
@Composable
fun UpcomingEventPreview(summary: String = "Mgmt Coordination Call") {
    val context = LocalContext.current
    val previewUtils = ComposePreviewUtils.getInstance(context)
    val viewThemeUtils = previewUtils.viewThemeUtils
    val colorScheme = viewThemeUtils.getColorScheme(context)

    val event = UpcomingEvent(
        uri = "uri",
        recurrenceId = null,
        calendarUri = "calendarUri",
        start = System.currentTimeMillis() / 1000,
        summary = summary,
        location = null,
        calendarAppUrl = null
    )

    MaterialTheme(colorScheme = colorScheme) {
        UpcomingEventView(
            event = event,
            viewThemeUtils = viewThemeUtils,
            onDismiss = {}
        )
    }
}
