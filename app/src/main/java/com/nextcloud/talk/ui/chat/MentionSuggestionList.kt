/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.chat

import android.content.res.Configuration
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.PhoneUtils.isPhoneNumber
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_CALLS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_EMAILS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_FEDERATION
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_GROUPS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_GUESTS
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_TEAMS
import com.nextcloud.talk.models.json.mention.MentionDto
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.ActorAvatarImage
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CharacterAvatarUtils
import com.nextcloud.talk.utils.DisplayUtils
import java.util.Locale

private val listMaxHeight = 280.dp
private val avatarSize = 40.dp
private val statusSize = 18.dp
private val listElevation = 6.dp
private val listCornerRadius = 12.dp
private const val STATUS_RADIUS_DP = 9f

/**
 * Who a mention suggestion is resolved against: the account's server and the conversation, for
 * avatars of local and federated users.
 */
data class MentionSuggestionContext(val baseUrl: String, val credentials: String, val roomToken: String)

@Composable
fun MentionSuggestionList(
    items: List<MentionAutocompleteItem>,
    query: String,
    suggestionContext: MentionSuggestionContext,
    onItemClick: (MentionAutocompleteItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(listCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = listElevation
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = listMaxHeight)) {
            items(items, key = { "${it.source}/${it.objectId}" }) { item ->
                MentionSuggestionRow(
                    item = item,
                    query = query,
                    suggestionContext = suggestionContext,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun MentionSuggestionRow(
    item: MentionAutocompleteItem,
    query: String,
    suggestionContext: MentionSuggestionContext,
    onClick: () -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val displayName = item.displayName.orEmpty()
    val secondary = mentionSecondaryText(item.source, item.objectId, stringResource(R.string.nc_team))
    val statusText = item.statusMessage?.takeIf { it.isNotEmpty() }
        ?: StatusType.getDescription(item.status, LocalContext.current)
    val statusEmoji = item.statusIcon?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(avatarSize)) {
            MentionSuggestionAvatar(item, suggestionContext, Modifier.size(avatarSize))
            MentionSuggestionStatus(
                status = item.status,
                modifier = Modifier
                    .size(statusSize)
                    .align(Alignment.BottomEnd)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightQuery(displayName, query, highlightColor),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = highlightQuery(secondary, query, highlightColor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (statusEmoji != null || statusText.isNotEmpty()) {
                Text(
                    text = listOfNotNull(statusEmoji, statusText.takeIf { it.isNotEmpty() }).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MentionSuggestionAvatar(
    item: MentionAutocompleteItem,
    suggestionContext: MentionSuggestionContext,
    modifier: Modifier
) {
    val guestLabel = stringResource(R.string.nc_guest)
    val isDark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    when (item.source) {
        SOURCE_CALLS -> {
            val iconRes = if (isPhoneNumber(item.displayName)) {
                R.drawable.icon_circular_phone
            } else {
                R.drawable.ic_circular_group_mentions
            }
            MentionPlaceholderAvatar(iconRes, modifier)
        }

        SOURCE_GROUPS -> MentionPlaceholderAvatar(R.drawable.ic_circular_group_mentions, modifier)

        SOURCE_TEAMS -> MentionPlaceholderAvatar(R.drawable.icon_circular_team, modifier)

        SOURCE_GUESTS, SOURCE_EMAILS -> {
            val avatar = remember(item.source, item.objectId, item.displayName, guestLabel) {
                CharacterAvatarUtils.avatarFor(item.source, item.objectId, item.displayName, guestLabel)
            }
            if (avatar != null) {
                ActorAvatarImage(avatar = avatar, modifier = modifier.clip(CircleShape))
            } else {
                MentionPlaceholderAvatar(R.drawable.account_circle_48dp, modifier)
            }
        }

        SOURCE_FEDERATION -> MentionUrlAvatar(
            ApiUtils.getUrlForFederatedAvatar(
                suggestionContext.baseUrl,
                suggestionContext.roomToken,
                item.objectId.orEmpty(),
                if (isDark) 1 else 0,
                requestBigSize = true
            ),
            suggestionContext.credentials,
            modifier
        )

        else -> MentionUrlAvatar(
            ApiUtils.getUrlForAvatar(suggestionContext.baseUrl, item.objectId, true, isDark),
            suggestionContext.credentials,
            modifier
        )
    }
}

@Composable
private fun MentionPlaceholderAvatar(@DrawableRes iconRes: Int, modifier: Modifier) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = stringResource(R.string.avatar),
        tint = Color.Unspecified,
        modifier = modifier
    )
}

@Composable
private fun MentionUrlAvatar(url: String, credentials: String, modifier: Modifier) {
    if (LocalInspectionMode.current) {
        MentionPlaceholderAvatar(R.drawable.account_circle_48dp, modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(url, credentials) {
        ImageRequest.Builder(context)
            .data(url)
            .addHeader("Authorization", credentials)
            .crossfade(true)
            .transformations(CircleCropTransformation())
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.avatar),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.account_circle_48dp),
        error = painterResource(R.drawable.account_circle_48dp),
        modifier = modifier.clip(CircleShape)
    )
}

@Composable
private fun MentionSuggestionStatus(status: String?, modifier: Modifier) {
    if (status.isNullOrEmpty() || LocalInspectionMode.current) {
        return
    }
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
    AndroidView(
        factory = { ImageView(it) },
        update = { imageView ->
            val radiusPx = DisplayUtils.convertDpToPixel(STATUS_RADIUS_DP, imageView.context)
            imageView.setImageDrawable(StatusDrawable(status, "", radiusPx, surfaceArgb, imageView.context))
        },
        modifier = modifier
    )
}

/**
 * Teams use a "team/<id>" objectId that should not be exposed; show "Team" like the web client does.
 */
fun mentionSecondaryText(source: String?, objectId: String?, teamLabel: String): String =
    if (source == SOURCE_TEAMS) {
        teamLabel
    } else {
        "@$objectId"
    }

/**
 * Highlights every case-insensitive occurrence of [query] in [text].
 */
fun highlightQuery(text: String, query: String, color: Color): AnnotatedString {
    if (query.isEmpty()) {
        return AnnotatedString(text)
    }
    val lowerText = text.lowercase(Locale.getDefault())
    val lowerQuery = query.lowercase(Locale.getDefault())
    return buildAnnotatedString {
        append(text)
        var start = lowerText.indexOf(lowerQuery)
        while (start != -1) {
            val end = start + lowerQuery.length
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), start, end)
            start = lowerText.indexOf(lowerQuery, end)
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MentionSuggestionListPreview() {
    val context = LocalContext.current
    fun mention(id: String, label: String, source: String, status: String? = null, message: String? = null) =
        MentionAutocompleteItem(
            MentionDto(
                mentionId = null,
                id = id,
                label = label,
                source = source,
                status = status,
                statusIcon = null,
                statusMessage = message,
                roomToken = null
            ),
            context,
            "token"
        )
    MaterialTheme {
        MentionSuggestionList(
            items = listOf(
                mention("alice", "Alice Anders", "users", "online", "In a meeting"),
                mention("all", "Everyone", SOURCE_CALLS),
                mention("admins", "Admins", SOURCE_GROUPS),
                mention("team/abc", "Design", SOURCE_TEAMS),
                mention("guest/1", "Bob", SOURCE_GUESTS)
            ),
            query = "a",
            suggestionContext = MentionSuggestionContext("https://cloud.example.com", "", "token"),
            onItemClick = {}
        )
    }
}
