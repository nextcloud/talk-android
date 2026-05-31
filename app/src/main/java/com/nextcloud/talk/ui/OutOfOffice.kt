/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceData
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AVATAR_SIZE = 20
private const val CHIP_CORNER_RADIUS = 16
private const val CHIP_HEIGHT = 28
private const val CHIP_PADDING = 4
private const val MAX_CONTENT_HEIGHT = 150
private const val MILLIS_PER_SECOND = 1000L

private data class OutOfOfficeDateState(val isSameDay: Boolean, val period: String?)

data class OutOfOfficeViewData(
    val userAbsence: UserAbsenceData,
    val displayName: String,
    val baseUrl: String?,
    val initialExpanded: Boolean = false
)

@Composable
private fun rememberOutOfOfficeDateState(userAbsence: UserAbsenceData, locale: Locale): OutOfOfficeDateState {
    val startDate = Date(userAbsence.startDate.toLong() * MILLIS_PER_SECOND)
    val endDate = Date(userAbsence.endDate.toLong() * MILLIS_PER_SECOND)
    return remember(userAbsence.startDate, userAbsence.endDate) {
        val isSameDay = SimpleDateFormat("yyyyMMdd", locale).run {
            format(startDate) == format(endDate)
        }
        val period = if (!isSameDay) {
            val fmt = SimpleDateFormat("MMM d, yyyy", locale)
            "${fmt.format(startDate)} - ${fmt.format(endDate)}"
        } else {
            null
        }
        OutOfOfficeDateState(isSameDay, period)
    }
}

@Composable
fun OutOfOfficeView(data: OutOfOfficeViewData, viewThemeUtils: ViewThemeUtils, onReplacementClick: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember { viewThemeUtils.getColorScheme(context) }
    val scrollState = rememberScrollState()
    var isExpanded by remember { mutableStateOf(data.initialExpanded) }
    val locale = LocalConfiguration.current.locales[0]
    val dateState = rememberOutOfOfficeDateState(data.userAbsence, locale)
    val shortMessage = if (dateState.isSameDay) {
        stringResource(R.string.user_absence_for_one_day, data.displayName)
    } else {
        stringResource(R.string.user_absence, data.displayName)
    }
    val collapsedMessage = stringResource(R.string.user_absence_collapsed, data.displayName)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(start = 8.dp, end = 0.dp)) {
            Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isExpanded) shortMessage else collapsedMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isExpanded) {
                    OutOfOfficeExpandedContent(
                        userAbsence = data.userAbsence,
                        period = dateState.period,
                        baseUrl = data.baseUrl,
                        onReplacementClick = onReplacementClick,
                        scrollState = scrollState
                    )
                }
            }
            OutOfOfficeToggleButton(isExpanded = isExpanded, onToggle = { isExpanded = !isExpanded })
        }
    }
}

@Composable
private fun OutOfOfficeToggleButton(isExpanded: Boolean, onToggle: () -> Unit) {
    Column {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = null
            )
        }
    }
}

@Composable
private fun OutOfOfficeExpandedContent(
    userAbsence: UserAbsenceData,
    period: String?,
    baseUrl: String?,
    onReplacementClick: () -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .heightIn(max = MAX_CONTENT_HEIGHT.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (period != null) {
            Text(
                text = period,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (userAbsence.replacementUserId != null &&
            userAbsence.replacementUserDisplayName != null
        ) {
            OutOfOfficeReplacementRow(
                userAbsence = userAbsence,
                baseUrl = baseUrl,
                onReplacementClick = onReplacementClick
            )
        }
        Text(
            text = userAbsence.message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OutOfOfficeReplacementRow(userAbsence: UserAbsenceData, baseUrl: String?, onReplacementClick: () -> Unit) {
    val context = LocalContext.current
    val avatarUrl = remember(baseUrl, userAbsence.replacementUserId) {
        if (baseUrl != null) {
            ApiUtils.getUrlForAvatar(
                baseUrl,
                userAbsence.replacementUserId,
                false,
                darkMode = DisplayUtils.isDarkModeOn(context)
            )
        } else {
            null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.user_absence_replacement),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.size(8.dp))
        Card(
            onClick = onReplacementClick,
            shape = RoundedCornerShape(CHIP_CORNER_RADIUS.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(CHIP_HEIGHT.dp)
                    .padding(CHIP_PADDING.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl?.toUri())
                        .transformations(CircleCropTransformation())
                        .placeholder(R.drawable.account_circle_96dp)
                        .error(R.drawable.account_circle_96dp)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(AVATAR_SIZE.dp)
                )
                Spacer(modifier = Modifier.size(CHIP_PADDING.dp))
                Text(
                    text = userAbsence.replacementUserDisplayName!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = CHIP_PADDING.dp)
                )
            }
        }
    }
}

@Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OutOfOfficePreviewDark() {
    OutOfOfficePreview()
}

@Preview(name = "R-t-L", locale = "ar")
@Composable
fun OutOfOfficePreviewRtl() {
    OutOfOfficePreview(
        displayName = "جين",
        message = "مرحباً، أنا خارج المكتب هذا الأسبوع. يرجى التواصل مع بوب في الأمور العاجلة."
    )
}

@Preview(name = "Light Mode / Collapsed")
@Composable
fun OutOfOfficePreviewCollapsed() {
    OutOfOfficePreview(initialExpanded = false)
}

@Preview(name = "Dark Mode / Collapsed", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OutOfOfficePreviewDarkCollapsed() {
    OutOfOfficePreview(initialExpanded = false)
}

@Preview(name = "R-t-L / Collapsed", locale = "ar")
@Composable
fun OutOfOfficePreviewRtlCollapsed() {
    OutOfOfficePreview(displayName = "جين", initialExpanded = false)
}

@Suppress("MagicNumber")
@Preview(name = "Light Mode")
@Composable
fun OutOfOfficePreview(
    displayName: String = "Jane",
    initialExpanded: Boolean = true,
    message: String = "Hi, I am out of office this week. Please contact Bob for urgent matters."
) {
    val context = LocalContext.current
    val previewUtils = ComposePreviewUtils.getInstance(context)
    val viewThemeUtils = previewUtils.viewThemeUtils
    val colorScheme = viewThemeUtils.getColorScheme(context)

    val userAbsence = UserAbsenceData(
        id = "1",
        userId = "jane",
        startDate = 1748736000,
        endDate = 1749340800,
        shortMessage = "Out of office",
        message = message,
        replacementUserId = "bob",
        replacementUserDisplayName = "Bob"
    )

    MaterialTheme(colorScheme = colorScheme) {
        OutOfOfficeView(
            data = OutOfOfficeViewData(
                userAbsence = userAbsence,
                displayName = displayName,
                baseUrl = null,
                initialExpanded = initialExpanded
            ),
            viewThemeUtils = viewThemeUtils,
            onReplacementClick = {}
        )
    }
}
