/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import org.greenrobot.eventbus.EventBus

private val messageTokenRegex =
    Regex("""(\{[^{}]+\}|\*\*.*?\*\*|\*.*?\*|`.*?`|\[.*?]\(.*?\)|https?://\S+)""")

private val mentionParameterTypes = setOf("user", "guest", "call", "user-group", "email", "circle")

private val mentionAvatarSize = 20.dp
private val mentionIconSize = 20.dp

private const val MIN_CHIP_LABEL_LENGTH = 4
private const val MAX_CHIP_LABEL_LENGTH = 22
private const val CHIP_BASE_WIDTH_EM = 2.45f
private const val CHIP_CHAR_WIDTH_EM = 0.56f
private const val CHIP_HEIGHT_EM = 1.75f
private const val MULTILINE_CHIP_HEIGHT_EM = 1.95f

private val chipVerticalPadding = 2.dp
private val multilineChipVerticalPadding = 3.dp

private data class MentionChipModel(
    val id: String,
    val rawId: String,
    val name: String,
    val type: String,
    val isFederated: Boolean,
    val isSelfMention: Boolean,
    val isClickableUserMention: Boolean,
    val avatarUrl: String?
)

private data class MentionRichText(val annotated: AnnotatedString, val inlineContent: Map<String, InlineTextContent>)

@Composable
fun MentionEnrichedText(message: ChatMessageUi, modifier: Modifier = Modifier, textStyle: TextStyle) {
    var isMultilineLayout by remember(message.id, message.message) {
        mutableStateOf(message.message.contains("\n") || message.message.contains("\r"))
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val richText = remember(message, isMultilineLayout, linkColor, codeBackground, textStyle) {
        buildMentionRichText(
            message = message,
            linkColor = linkColor,
            codeBackground = codeBackground,
            textStyle = textStyle,
            isMultilineLayout = isMultilineLayout
        )
    }
    val resolvedTextStyle = if (richText.inlineContent.isEmpty()) {
        textStyle
    } else {
        textStyle.copy(lineHeight = TextUnit.Unspecified)
    }

    Text(
        modifier = modifier,
        text = richText.annotated,
        inlineContent = richText.inlineContent,
        style = resolvedTextStyle,
        onTextLayout = { textLayoutResult ->
            val isCurrentlyMultiline = textLayoutResult.lineCount > 1
            if (isMultilineLayout != isCurrentlyMultiline) {
                isMultilineLayout = isCurrentlyMultiline
            }
        }
    )
}

private fun buildMentionRichText(
    message: ChatMessageUi,
    linkColor: Color,
    codeBackground: Color,
    textStyle: TextStyle,
    isMultilineLayout: Boolean
): MentionRichText {
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    var mentionCounter = 0

    val annotated = buildAnnotatedString {
        var lastIndex = 0
        for (match in messageTokenRegex.findAll(message.plainMessage)) {
            val range = match.range
            if (lastIndex < range.first) {
                append(message.plainMessage.substring(lastIndex, range.first))
            }

            val token = match.value
            when {
                token.startsWith("{") && token.endsWith("}") -> {
                    val mention = token.toMentionChipModel(message)
                    if (mention == null) {
                        appendFallbackParameter(token, message.messageParameters)
                    } else {
                        val inlineId = "mention-${message.id}-$mentionCounter"
                        mentionCounter += 1
                        inlineContent[inlineId] = buildMentionInlineContent(
                            mention = mention,
                            textStyle = textStyle,
                            isMultilineLayout = isMultilineLayout
                        )
                        appendInlineContent(inlineId, "@${mention.name}")
                    }
                }

                token.startsWith(
                    "**"
                ) -> appendStyledToken(token.removeSurrounding("**"), SpanStyle(fontWeight = FontWeight.Bold))
                token.startsWith(
                    "*"
                ) -> appendStyledToken(token.removeSurrounding("*"), SpanStyle(fontStyle = FontStyle.Italic))
                token.startsWith("`") -> {
                    appendStyledToken(
                        token.removeSurrounding("`"),
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    )
                }

                token.startsWith("[") -> {
                    val textPart = token.substringAfter("[").substringBefore("]")
                    val url = token.substringAfter("(").substringBefore(")")
                    appendLinkedToken(textPart, url, linkColor)
                }

                token.startsWith("http") -> appendLinkedToken(token, token, linkColor)
            }

            lastIndex = range.last + 1
        }

        if (lastIndex < message.plainMessage.length) {
            append(message.plainMessage.substring(lastIndex))
        }
    }

    return MentionRichText(annotated = annotated, inlineContent = inlineContent)
}

private fun AnnotatedString.Builder.appendStyledToken(text: String, style: SpanStyle) {
    val start = length
    append(text)
    addStyle(style, start, length)
}

private fun AnnotatedString.Builder.appendLinkedToken(text: String, url: String, linkColor: Color) {
    val start = length
    append(text)
    addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, length)
    addLink(LinkAnnotation.Url(url), start, length)
}

private fun AnnotatedString.Builder.appendFallbackParameter(
    token: String,
    messageParameters: Map<String, Map<String, String>>
) {
    val key = token.removePrefix("{").removeSuffix("}")
    val replacementText = messageParameters[key]?.get("name")
    if (replacementText == null) {
        append(token)
    } else {
        append(replacementText)
    }
}

private fun buildMentionInlineContent(
    mention: MentionChipModel,
    textStyle: TextStyle,
    isMultilineLayout: Boolean
): InlineTextContent {
    val width = estimateMentionChipWidthInEm(mention.name)
    val placeholderHeight = if (isMultilineLayout) MULTILINE_CHIP_HEIGHT_EM else CHIP_HEIGHT_EM
    return InlineTextContent(
        placeholder = Placeholder(
            width = width.em,
            height = placeholderHeight.em,
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

private fun estimateMentionChipWidthInEm(label: String): Float {
    val clampedLength = label.length.coerceIn(MIN_CHIP_LABEL_LENGTH, MAX_CHIP_LABEL_LENGTH)
    return CHIP_BASE_WIDTH_EM + (clampedLength * CHIP_CHAR_WIDTH_EM)
}

@Composable
private fun MentionChip(mention: MentionChipModel, textStyle: TextStyle, isMultilineLayout: Boolean) {
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
        Color.White.copy(alpha = 0.87f)
    }
    val textColor = if (mention.isSelfMention) {
        colorResource(R.color.textColorOnPrimaryBackground)
    } else {
        colorResource(R.color.high_emphasis_text)
    }
    val fallbackIcon = resolveMentionFallbackIcon(mention)
    val verticalPadding = if (isMultilineLayout) multilineChipVerticalPadding else chipVerticalPadding

    Row(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(chipCornerRadius))
            .clickable(enabled = mention.isClickableUserMention) {
                EventBus.getDefault().post(UserMentionClickEvent(mention.id))
            }
            .padding(start = 4.dp, top = verticalPadding, end = 4.dp, bottom = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (mention.avatarUrl != null) {
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

        Text(
            text = mention.name,
            color = textColor,
            maxLines = 1,
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

private fun String.toMentionChipModel(message: ChatMessageUi): MentionChipModel? {
    val parameter = message.messageParameters[removePrefix("{").removeSuffix("}")]
    val type = parameter?.get("type")
    if (parameter == null || type == null || type !in mentionParameterTypes) {
        return null
    }

    val rawId = parameter["id"].orEmpty()
    val name = parameter["name"].orEmpty()
    val server = parameter["server"]
    val isFederated = !server.isNullOrEmpty()
    val mentionId = if (isFederated) "$rawId@$server" else rawId
    val isSelfMention = rawId == message.activeUserId
    val avatarUrl = resolveMentionAvatarUrl(message, rawId, name, type, mentionId, isFederated)

    return MentionChipModel(
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
private fun resolveMentionAvatarUrl(
    message: ChatMessageUi,
    rawId: String,
    mentionName: String,
    mentionType: String,
    mentionId: String,
    isFederated: Boolean
): String? {
    val baseUrl = message.activeUserBaseUrl ?: return null

    return when {
        isFederated && !message.roomToken.isNullOrEmpty() -> {
            ApiUtils.getUrlForFederatedAvatar(
                baseUrl = baseUrl,
                token = message.roomToken,
                cloudId = mentionId,
                darkTheme = 0,
                requestBigSize = false
            )
        }

        mentionType == "guest" || mentionType == "email" -> {
            ApiUtils.getUrlForGuestAvatar(baseUrl = baseUrl, name = mentionName, requestBigSize = true)
        }

        mentionType == "call" || mentionType == "user-group" || mentionType == "circle" -> null
        rawId.isNotEmpty() -> ApiUtils.getUrlForAvatar(baseUrl, rawId, false, false)
        else -> null
    }
}

private fun resolveMentionFallbackIcon(mention: MentionChipModel): Int =
    when {
        mention.type == "call" && mention.name.startsWith("+") -> R.drawable.icon_circular_phone
        mention.type == "call" -> R.drawable.ic_circular_group_mentions
        mention.type == "user-group" -> R.drawable.ic_circular_group_mentions
        mention.type == "circle" -> R.drawable.icon_circular_team
        mention.isSelfMention -> R.drawable.mention_chip
        else -> R.drawable.accent_circle
    }
