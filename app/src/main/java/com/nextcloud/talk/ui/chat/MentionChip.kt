/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import org.greenrobot.eventbus.EventBus

val mentionParameterTypes = setOf("user", "guest", "call", "user-group", "email", "circle")

val mentionAvatarSize = 20.dp
val mentionIconSize = 20.dp

private const val MIN_CHIP_LABEL_LENGTH = 4
private const val MAX_CHIP_LABEL_LENGTH = 22
private const val CHIP_FIXED_OVERHEAD_DP = 33f
private const val CHIP_CHAR_WIDTH_EM = 0.56f
private const val CHIP_SINGLE_LINE_HEIGHT_DP = 24f
private const val CHIP_MULTILINE_HEIGHT_DP = 26f
private const val CHIP_FALLBACK_FONT_SIZE_SP = 16f

private val chipVerticalPadding = 2.dp
private val multilineChipVerticalPadding = 3.dp

data class MentionChipModel(
    val id: String,
    val rawId: String,
    val name: String,
    val type: String,
    val isFederated: Boolean,
    val isSelfMention: Boolean,
    val isClickableUserMention: Boolean,
    val avatarUrl: String?
)

fun parseMentionChipModel(
    key: String,
    messageParameters: Map<String, Map<String, String>>,
    activeUserId: String?,
    activeUserBaseUrl: String?,
    roomToken: String?
): MentionChipModel? =
    messageParameters[key]
        ?.takeIf { it["type"] in mentionParameterTypes }
        ?.let { parameter ->
            val type = parameter["type"]!!
            val rawId = parameter["id"].orEmpty()
            val name = parameter["name"].orEmpty()
            val server = parameter["server"]
            val isFederated = !server.isNullOrEmpty()
            val mentionId = if (isFederated) "$rawId@$server" else rawId
            val isSelfMention = rawId == activeUserId
            val avatarUrl = resolveMentionAvatarUrl(
                rawId = rawId,
                name = name,
                type = type,
                mentionId = mentionId,
                isFederated = isFederated,
                activeUserBaseUrl = activeUserBaseUrl,
                roomToken = roomToken
            )

            MentionChipModel(
                id = mentionId,
                rawId = rawId,
                name = name,
                type = type,
                isFederated = isFederated,
                isSelfMention = isSelfMention,
                isClickableUserMention = type == "user" && !isSelfMention && !isFederated,
                avatarUrl = avatarUrl
            )
        }

@Suppress("LongParameterList")
fun resolveMentionAvatarUrl(
    rawId: String,
    name: String,
    type: String,
    mentionId: String,
    isFederated: Boolean,
    activeUserBaseUrl: String?,
    roomToken: String?
): String? {
    val baseUrl = activeUserBaseUrl ?: return null
    return when {
        isFederated && !roomToken.isNullOrEmpty() -> ApiUtils.getUrlForFederatedAvatar(
            baseUrl = baseUrl,
            token = roomToken,
            cloudId = mentionId,
            darkTheme = 0,
            requestBigSize = false
        )
        type == "guest" || type == "email" -> ApiUtils.getUrlForGuestAvatar(
            baseUrl = baseUrl,
            name = name,
            requestBigSize = true
        )
        type == "call" || type == "user-group" || type == "circle" -> null
        rawId.isNotEmpty() -> ApiUtils.getUrlForAvatar(baseUrl, rawId, false, false)
        else -> null
    }
}

fun resolveMentionFallbackIcon(mention: MentionChipModel): Int =
    when {
        mention.type == "call" && mention.name.startsWith("+") -> R.drawable.icon_circular_phone
        mention.type == "call" -> R.drawable.ic_circular_group_mentions
        mention.type == "user-group" -> R.drawable.ic_circular_group_mentions
        mention.type == "circle" -> R.drawable.icon_circular_team
        mention.isSelfMention -> R.drawable.mention_chip
        else -> R.drawable.accent_circle
    }

fun estimateMentionChipWidthInEm(label: String, fontSizeSp: Float): Float {
    val clampedLength = label.length.coerceIn(MIN_CHIP_LABEL_LENGTH, MAX_CHIP_LABEL_LENGTH)
    return CHIP_FIXED_OVERHEAD_DP / fontSizeSp + (clampedLength * CHIP_CHAR_WIDTH_EM)
}

fun buildMentionInlineContent(
    mention: MentionChipModel,
    textStyle: TextStyle,
    isMultilineLayout: Boolean
): InlineTextContent {
    val fontSizeSp = if (textStyle.fontSize.isSpecified) textStyle.fontSize.value else CHIP_FALLBACK_FONT_SIZE_SP
    val width = estimateMentionChipWidthInEm(mention.name, fontSizeSp)
    val heightDp = if (isMultilineLayout) CHIP_MULTILINE_HEIGHT_DP else CHIP_SINGLE_LINE_HEIGHT_DP
    val heightEm = heightDp / fontSizeSp
    return InlineTextContent(
        placeholder = Placeholder(
            width = width.em,
            height = heightEm.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom
        )
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart
        ) {
            MentionChip(
                mention = mention,
                textStyle = textStyle,
                isMultilineLayout = isMultilineLayout
            )
        }
    }
}

fun AnnotatedString.Builder.appendMentionChip(
    inlineId: String,
    mention: MentionChipModel,
    inlineContent: MutableMap<String, InlineTextContent>,
    textStyle: TextStyle,
    isMultilineLayout: Boolean
) {
    inlineContent[inlineId] = buildMentionInlineContent(mention, textStyle, isMultilineLayout)
    appendInlineContent(inlineId, "@${mention.name}")
}

fun AnnotatedString.Builder.appendBoldToken(text: String) {
    val start = length
    append(text)
    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
}

@Composable
fun MentionChip(mention: MentionChipModel, textStyle: TextStyle, isMultilineLayout: Boolean) {
    val context = LocalContext.current
    val viewThemeUtils = LocalViewThemeUtils.current
    val density = LocalDensity.current
    val chipCornerRadius = dimensionResource(R.dimen.standard_padding)
    val chipTextSize = with(density) {
        if (textStyle.fontSize.isSpecified) {
            textStyle.fontSize
        } else {
            dimensionResource(R.dimen.chat_text_size).value.sp
        }
    }
    val backgroundColor = if (mention.isSelfMention) {
        viewThemeUtils.getColorScheme(context).primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (mention.isSelfMention) {
        viewThemeUtils.getColorScheme(context).onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val fallbackIcon = resolveMentionFallbackIcon(mention)
    val verticalPadding = if (isMultilineLayout) multilineChipVerticalPadding else chipVerticalPadding

    Row(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(chipCornerRadius))
            .clickable(enabled = mention.isClickableUserMention) {
                EventBus.getDefault().post(UserMentionClickEvent(mention.id))
            }
            .padding(start = verticalPadding, top = verticalPadding, end = 4.dp, bottom = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MentionChipIcon(mention = mention, fallbackIcon = fallbackIcon)

        Text(
            text = mention.name,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(end = 3.dp),
            style = textStyle.copy(
                color = textColor,
                fontSize = chipTextSize,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Normal,
                fontFamily = FontFamily.Default
            )
        )
    }
}

@Composable
fun MentionChipIcon(mention: MentionChipModel, fallbackIcon: Int) {
    if (mention.avatarUrl != null) {
        val context = LocalContext.current
        val loadedImage = remember(mention.avatarUrl) { loadImage(mention.avatarUrl, context, fallbackIcon) }
        AsyncImage(model = loadedImage, contentDescription = null, modifier = Modifier.size(mentionAvatarSize))
    } else {
        Icon(
            painter = painterResource(fallbackIcon),
            contentDescription = null,
            modifier = Modifier.size(mentionIconSize),
            tint = Color.Unspecified
        )
    }
}
