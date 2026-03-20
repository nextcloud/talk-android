/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.content.Context
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageReactionUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import java.time.LocalDate

private val REGULAR_TEXT_SIZE = 16.sp
private val TIME_TEXT_SIZE = 12.sp
private val AUTHOR_TEXT_SIZE = 12.sp
private const val QUOTE_SHAPE_OFFSET = 6
private const val LINE_SPACING = 1.2f
private const val HALF_OPACITY = 127
private const val MESSAGE_LENGTH_THRESHOLD = 25
private const val ANIMATED_BLINK = 500

private val BUBBLE_RADIUS_BIG = 10.dp
private val BUBBLE_RADIUS_SMALL = 2.dp
private val REACTION_OVERLAP_OFFSET = (-4).dp

internal val LocalReactionClickHandler = compositionLocalOf<(Int, String) -> Unit> { { _, _ -> } }
internal val LocalReactionLongClickHandler = compositionLocalOf<(Int) -> Unit> { {} }

private enum class MetadataLayoutMode {
    CAPTION,
    OVERLAY,
    INLINE,
    BELOW
}

private fun resolveMetadataLayoutMode(
    captionText: String?,
    forceTimeOverlay: Boolean,
    showInlineMetadata: Boolean
): MetadataLayoutMode = when {
    captionText != null -> MetadataLayoutMode.CAPTION
    forceTimeOverlay -> MetadataLayoutMode.OVERLAY
    showInlineMetadata -> MetadataLayoutMode.INLINE
    else -> MetadataLayoutMode.BELOW
}

private fun shouldShowTimeNextToContent(
    message: ChatMessageUi,
    conversationThreadId: Long?,
    forceTimeBelow: Boolean,
    forceTimeOverlay: Boolean
): Boolean {
    if (forceTimeBelow || forceTimeOverlay) return false
    val containsLinebreak = message.message.contains("\n") || message.message.contains("\r")
    return (message.message.length < MESSAGE_LENGTH_THRESHOLD) &&
        !isFirstMessageOfThreadInNormalChat(message, conversationThreadId) &&
        !containsLinebreak
}

@Composable
fun MessageScaffold(
    uiMessage: ChatMessageUi,
    conversationThreadId: Long? = null,
    includePadding: Boolean = true,
    isOneToOneConversation: Boolean = true,
    captionText: String? = null,
    playAnimation: Boolean = false,
    forceTimeBelow: Boolean = false,
    forceTimeOverlay: Boolean = false,
    bubbleColor: Color? = null,
    content: @Composable () -> Unit
) {
    val incoming = uiMessage.incoming
    val resolvedBubbleColor = bubbleColor ?: run {
        val context = LocalContext.current
        if (incoming) {
            val colorRes = if (uiMessage.isDeleted) {
                R.color.bg_message_list_incoming_bubble_deleted
            } else {
                R.color.bg_message_list_incoming_bubble
            }
            Color(getColorFromTheme(context, colorRes))
        } else {
            if (LocalInspectionMode.current) {
                colorScheme.primaryContainer
            } else {
                val viewThemeUtils = LocalViewThemeUtils.current
                val colorInt = viewThemeUtils.talk
                    .getOutgoingMessageBubbleColor(context, uiMessage.isDeleted, false)

                if (uiMessage.isDeleted) {
                    Color(ColorUtils.setAlphaComponent(colorInt, HALF_OPACITY))
                } else {
                    Color(colorInt)
                }
            }
        }
    }

    val showInlineMetadata = shouldShowTimeNextToContent(
        message = uiMessage,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = forceTimeBelow,
        forceTimeOverlay = forceTimeOverlay
    )
    val metadataLayoutMode = resolveMetadataLayoutMode(
        captionText = captionText,
        forceTimeOverlay = forceTimeOverlay,
        showInlineMetadata = showInlineMetadata
    )

    val shape = if (incoming) {
        RoundedCornerShape(
            topStart = BUBBLE_RADIUS_SMALL,
            topEnd = BUBBLE_RADIUS_BIG,
            bottomEnd = BUBBLE_RADIUS_BIG,
            bottomStart = BUBBLE_RADIUS_BIG
        )
    } else {
        RoundedCornerShape(
            topStart = BUBBLE_RADIUS_BIG,
            topEnd = BUBBLE_RADIUS_SMALL,
            bottomEnd = BUBBLE_RADIUS_BIG,
            bottomStart = BUBBLE_RADIUS_BIG
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End
    ) {
        MessageLeadingDecoration(
            uiMessage = uiMessage,
            isOneToOneConversation = isOneToOneConversation
        )
        MessageBubbleWithReactions(
            uiMessage = uiMessage,
            incoming = incoming,
            includePadding = includePadding,
            isOneToOneConversation = isOneToOneConversation,
            shape = shape,
            resolvedBubbleColor = resolvedBubbleColor,
            metadataLayoutMode = metadataLayoutMode,
            captionText = captionText,
            showInlineMetadata = showInlineMetadata,
            content = content
        )
    }
}

@Composable
private fun RowScope.MessageLeadingDecoration(
    uiMessage: ChatMessageUi,
    isOneToOneConversation: Boolean
) {
    if (uiMessage.incoming && isOneToOneConversation) {
        val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
        val loadedImage = loadImage(uiMessage.avatarUrl, LocalContext.current, errorPlaceholderImage)
        AsyncImage(
            model = loadedImage,
            contentDescription = stringResource(R.string.user_avatar),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterVertically)
                .padding(end = 8.dp)
        )
    } else if (uiMessage.incoming) {
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun MessageBubbleWithReactions(
    uiMessage: ChatMessageUi,
    incoming: Boolean,
    includePadding: Boolean,
    isOneToOneConversation: Boolean,
    shape: RoundedCornerShape,
    resolvedBubbleColor: Color,
    metadataLayoutMode: MetadataLayoutMode,
    captionText: String?,
    showInlineMetadata: Boolean,
    content: @Composable () -> Unit
) {
    val bubbleModifier = Modifier
        .defaultMinSize(60.dp, 40.dp)
        .widthIn(60.dp, 280.dp)

    Column(horizontalAlignment = if (incoming) Alignment.Start else Alignment.End) {
        Surface(
            modifier = bubbleModifier,
            color = resolvedBubbleColor,
            shape = shape
        ) {
            MessageBubbleContent(
                uiMessage = uiMessage,
                includePadding = includePadding,
                isOneToOneConversation = isOneToOneConversation,
                metadataLayoutMode = metadataLayoutMode,
                captionText = captionText,
                showInlineMetadata = showInlineMetadata,
                content = content
            )
        }

        MessageReactions(uiMessage = uiMessage)
    }
}

@Composable
private fun MessageBubbleContent(
    uiMessage: ChatMessageUi,
    includePadding: Boolean,
    isOneToOneConversation: Boolean,
    metadataLayoutMode: MetadataLayoutMode,
    captionText: String?,
    showInlineMetadata: Boolean,
    content: @Composable () -> Unit
) {
    val bubbleContentModifier = if (includePadding) {
        Modifier.padding(10.dp, 4.dp, 10.dp, 4.dp)
    } else {
        Modifier
    }

    Column(
        modifier = bubbleContentModifier,
        verticalArrangement = Arrangement.Center
    ) {
        MessageHeader(
            uiMessage = uiMessage,
            includePadding = includePadding,
            isOneToOneConversation = isOneToOneConversation
        )
        MessageBodyWithMetadata(
            uiMessage = uiMessage,
            metadataLayoutMode = metadataLayoutMode,
            captionText = captionText,
            showInlineMetadata = showInlineMetadata,
            content = content
        )
    }
}

@Composable
private fun MessageHeader(
    uiMessage: ChatMessageUi,
    includePadding: Boolean,
    isOneToOneConversation: Boolean
) {
    uiMessage.parentMessage?.let {
        CommonMessageQuote(it)
    }

    if (uiMessage.incoming && isOneToOneConversation) {
        // we only need padding for the author if we did not already apply a padding
        val authorStartPadding = if (includePadding) 0.dp else 10.dp
        Text(
            uiMessage.actorDisplayName,
            fontSize = AUTHOR_TEXT_SIZE,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(authorStartPadding, 4.dp, 6.dp, 4.dp)
        )
    }

    ThreadTitle(uiMessage, padding = if (includePadding) 0.dp else 8.dp)
}

@Composable
private fun ColumnScope.MessageBodyWithMetadata(
    uiMessage: ChatMessageUi,
    metadataLayoutMode: MetadataLayoutMode,
    captionText: String?,
    showInlineMetadata: Boolean,
    content: @Composable () -> Unit
) {
    when (metadataLayoutMode) {
        MetadataLayoutMode.CAPTION -> {
            content()
            CaptionWithMetadata(
                captionText = captionText.orEmpty(),
                uiMessage = uiMessage,
                showInlineMetadata = showInlineMetadata
            )
        }

        MetadataLayoutMode.OVERLAY -> {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
                OverlayMetadataBadge(uiMessage = uiMessage)
            }
        }

        MetadataLayoutMode.INLINE -> {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.padding(bottom = 5.dp)) {
                    content()
                }
                MessageMetadata(uiMessage)
            }
        }

        MetadataLayoutMode.BELOW -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                content()
            }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 8.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageMetadata(uiMessage)
            }
        }
    }
}

@Composable
private fun BoxScope.OverlayMetadataBadge(uiMessage: ChatMessageUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 8.dp, end = 8.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        MessageMetadata(uiMessage = uiMessage, color = Color.White)
    }
}

@Composable
private fun ColumnScope.MessageReactions(uiMessage: ChatMessageUi) {
    if (uiMessage.reactions.isEmpty()) {
        return
    }

    val onReactionClick = LocalReactionClickHandler.current
    val onReactionLongClick = LocalReactionLongClickHandler.current

    Row(
        modifier = Modifier
            .align(Alignment.Start)
            .padding(start = dimensionResource(R.dimen.message_bubble_corners_horizontal_padding))
            .offset(y = REACTION_OVERLAP_OFFSET)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        uiMessage.reactions.forEach { reaction ->
            MessageReactionChip(
                messageId = uiMessage.id,
                incoming = uiMessage.incoming,
                reaction = reaction,
                onReactionClick = onReactionClick,
                onReactionLongClick = onReactionLongClick
            )
        }
    }
}

@Composable
private fun MessageReactionChip(
    messageId: Int,
    incoming: Boolean,
    reaction: MessageReactionUi,
    onReactionClick: (Int, String) -> Unit,
    onReactionLongClick: (Int) -> Unit
) {
    val themedColors = LocalViewThemeUtils.current.getColorScheme(LocalContext.current)

    val backgroundColor = if (reaction.isSelfReaction) {
        themedColors.primaryContainer
    } else {
        colorResource(R.color.bg_message_list_incoming_bubble)
    }
    val borderColor = if (reaction.isSelfReaction) {
        themedColors.primary
    } else {
        themedColors.surface
    }
    val textColor = if (incoming || reaction.isSelfReaction) {
        colorResource(R.color.high_emphasis_text)
    } else {
        themedColors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onReactionClick(messageId, reaction.emoji) },
                onLongClick = { onReactionLongClick(messageId) }
            )
            .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = reaction.emoji,
            color = textColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = reaction.amount.toString(),
            color = textColor,
            fontSize = AUTHOR_TEXT_SIZE
        )
    }
}

@Composable
private fun MessageMetadata(
    uiMessage: ChatMessageUi,
    color: Color = colorScheme.onSurfaceVariant
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .padding(
                start = 10.dp,
                end = 10.dp
            )
    ) {
        TimeDisplay(uiMessage, color)
        if (!uiMessage.incoming) {
            ReadStatus(uiMessage, color)
        }
    }
}

@Composable
private fun ColumnScope.CaptionWithMetadata(
    captionText: String,
    uiMessage: ChatMessageUi,
    showInlineMetadata: Boolean
) {
    if (showInlineMetadata) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            EnrichedText(
                uiMessage,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MessageMetadata(uiMessage)
            }
        }
    } else {
        EnrichedText(
            uiMessage,
            modifier = Modifier
                // .widthIn(20.dp, 280.dp)
                .padding(start = 8.dp, end = 8.dp)
        )
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MessageMetadata(uiMessage)
        }
    }
}

@Composable
fun CommonMessageQuote(
    message: ChatMessageUi
) {
    val color = colorResource(R.color.high_emphasis_text)
    Row(
        modifier = Modifier
            .drawWithCache {
                onDrawWithContent {
                    drawLine(
                        color = color,
                        start = Offset(0f, this.size.height / QUOTE_SHAPE_OFFSET),
                        end = Offset(0f, this.size.height - (this.size.height / QUOTE_SHAPE_OFFSET)),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    drawContent()
                }
            }
            .padding(8.dp)
    ) {
        Column {
            Text(message.actorDisplayName, fontSize = AUTHOR_TEXT_SIZE)
            EnrichedText(
                message,
                Modifier.padding(start = 10.dp)
            )
        }
    }
}

private fun getColorFromTheme(context: Context, resourceId: Int): Int {
    val isDarkMode = DisplayUtils.isAppThemeDarkMode(context)
    val nightConfig = android.content.res.Configuration()
    nightConfig.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
    val nightContext = context.createConfigurationContext(nightConfig)

    return if (isDarkMode) {
        nightContext.getColor(resourceId)
    } else {
        context.getColor(resourceId)
    }
}

@Composable
fun TimeDisplay(
    message: ChatMessageUi,
    color: Color = colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current
    val timeString = remember(message.timestamp) {
        try {
            DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
        } catch (e: Exception) {
            "10:00"
        }
    }
    Text(
        timeString,
        fontSize = TIME_TEXT_SIZE,
        textAlign = TextAlign.Center,
        color = color
    )
}

@Composable
fun ReadStatus(
    message: ChatMessageUi,
    color: Color = colorScheme.onSurfaceVariant
) {
    val icon = when (message.statusIcon) {
        MessageStatusIcon.FAILED -> painterResource(R.drawable.baseline_error_outline_24)
        MessageStatusIcon.SENDING -> painterResource(R.drawable.baseline_schedule_24)
        MessageStatusIcon.READ -> painterResource(R.drawable.ic_check_all)
        MessageStatusIcon.SENT -> painterResource(R.drawable.ic_check)
    }

    Icon(
        painter = icon,
        contentDescription = "",
        modifier = Modifier
            .padding(start = 4.dp)
            .size(16.dp),
        tint = color
    )
}

@Composable
fun ThreadTitle(message: ChatMessageUi, padding: androidx.compose.ui.unit.Dp = 0.dp) {
    if (isFirstMessageOfThreadInNormalChat(message)) {
        Row(modifier = Modifier.padding(horizontal = padding)) {
            val read = painterResource(R.drawable.outline_forum_24)
            Icon(
                read,
                "",
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
                    .align(Alignment.CenterVertically)
            )
            Text(
                text = message.threadTitle,
                fontSize = REGULAR_TEXT_SIZE,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EnrichedText(
    message: ChatMessageUi,
    modifier: Modifier
) {
    val annotated = remember(message.message) {
        buildAnnotatedString {
            appendMarkdownWithLinks(message.message)
        }
    }

    Text(
        modifier = modifier,
        text = annotated,
        style = TextStyle(
            fontSize = REGULAR_TEXT_SIZE,
            lineHeight = REGULAR_TEXT_SIZE * LINE_SPACING
        )
    )
}

fun AnnotatedString.Builder.appendMarkdownWithLinks(text: String) {
    val regex = Regex(
        pattern = """(\*\*.*?\*\*|\*.*?\*|`.*?`|\[.*?\]\(.*?\)|https?://\S+)"""
    )

    var lastIndex = 0

    for (match in regex.findAll(text)) {
        val range = match.range

        // Append normal text before match
        if (lastIndex < range.first) {
            append(text.substring(lastIndex, range.first))
        }

        val token = match.value

        when {
            // **bold**
            token.startsWith("**") -> {
                val content = token.removeSurrounding("**")
                val start = length
                append(content)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start,
                    length
                )
            }

            // *italic*
            token.startsWith("*") -> {
                val content = token.removeSurrounding("*")
                val start = length
                append(content)
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    start,
                    length
                )
            }

            // `code`
            token.startsWith("`") -> {
                val content = token.removeSurrounding("`")
                val start = length
                append(content)
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.LightGray
                    ),
                    start,
                    length
                )
            }

            // [text](url)
            token.startsWith("[") -> {
                val textPart = token.substringAfter("[").substringBefore("]")
                val url = token.substringAfter("(").substringBefore(")")

                val start = length
                append(textPart)

                addStyle(
                    SpanStyle(
                        color = Color.Blue,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    length
                )

                addLink(
                    LinkAnnotation.Url(url),
                    start,
                    length
                )
            }

            // plain URL
            token.startsWith("http") -> {
                val start = length
                append(token)

                addStyle(
                    SpanStyle(
                        color = Color.Blue,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    length
                )

                addLink(
                    LinkAnnotation.Url(token),
                    start,
                    length
                )
            }
        }

        lastIndex = range.last + 1
    }

    // Append remaining text
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

fun isFirstMessageOfThreadInNormalChat(message: ChatMessageUi, conversationThreadId: Long? = null): Boolean =
    conversationThreadId == null && message.isThread

@Composable
private fun Modifier.withCustomAnimation(incoming: Boolean, shape: RoundedCornerShape): Modifier {
    val infiniteTransition = rememberInfiniteTransition()
    val borderColor by infiniteTransition.animateColor(
        initialValue = colorScheme.primary,
        targetValue = colorScheme.background,
        animationSpec = infiniteRepeatable(
            animation = tween(ANIMATED_BLINK, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    return this.border(
        width = 4.dp,
        color = borderColor,
        shape = shape
    )
}

@Preview(showBackground = true, name = "Incoming Message")
@Preview(showBackground = true, name = "Incoming Message Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageScaffoldIncomingPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        val uiMessage = ChatMessageUi(
            id = 1,
            text = "Hello! How are you?",
            message = "Hello! How are you?",
            renderMarkdown = false,
            actorDisplayName = "John Doe",
            isThread = false,
            threadTitle = "",
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = null
        )
        MessageScaffold(uiMessage = uiMessage) {
            EnrichedText(
                uiMessage,
                Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Incoming Message")
@Preview(showBackground = true, name = "Incoming Message Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageScaffoldIncomingLongPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        val uiMessage = ChatMessageUi(
            id = 1,
            text = "Hello! How are youuuuuuuuuuuuuuuuuuuuuuuuuu?",
            message = "Hello! How are youuuuuuuuuuuuuuuuuuuuuuuuuuuuuu?",
            renderMarkdown = false,
            actorDisplayName = "John Doe",
            isThread = false,
            threadTitle = "",
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = null
        )
        MessageScaffold(uiMessage = uiMessage) {
            EnrichedText(
                uiMessage,
                Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Outgoing Message")
@Preview(showBackground = true, name = "Outgoing Message Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageScaffoldOutgoingPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        val uiMessage = ChatMessageUi(
            id = 2,
            text = "I'm doing great, thanks!",
            message = "I'm doing great, thanks!",
            renderMarkdown = false,
            actorDisplayName = "Me",
            isThread = false,
            threadTitle = "",
            incoming = false,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.READ,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = null
        )
        MessageScaffold(uiMessage = uiMessage) {
            EnrichedText(
                uiMessage,
                Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "Quoted Message")
@Composable
private fun CommonMessageQuotePreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        val uiMessage = ChatMessageUi(
            id = 3,
            text = "This is a quoted message",
            message = "This is a quoted message",
            renderMarkdown = false,
            actorDisplayName = "Original Author",
            isThread = false,
            threadTitle = "",
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = null
        )
        CommonMessageQuote(
            message = uiMessage
        )
    }
}
