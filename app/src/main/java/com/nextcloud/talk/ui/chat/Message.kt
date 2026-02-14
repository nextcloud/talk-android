package com.nextcloud.talk.ui.chat

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.emoji2.widget.EmojiTextView
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.theme.LocalMessageUtils
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils

private val REGULAR_TEXT_SIZE = 16.sp
private val TIME_TEXT_SIZE = 12.sp
private val AUTHOR_TEXT_SIZE = 12.sp
private const val QUOTE_SHAPE_OFFSET = 6
private const val LINE_SPACING = 1.2f
private const val INT_8 = 8
private const val HALF_OPACITY = 127
private const val MESSAGE_LENGTH_THRESHOLD = 25
private const val ANIMATED_BLINK = 500

@Composable
fun CommonMessageBody(
    message: ChatMessage,
    conversationThreadId: Long? = null,
    includePadding: Boolean = true,
    playAnimation: Boolean = false,
    content: @Composable () -> Unit
) {
    fun shouldShowTimeNextToContent(message: ChatMessage): Boolean {
        val containsLinebreak = message.message?.contains("\n") ?: false ||
            message.message?.contains("\r") ?: false

        return ((message.message?.length ?: 0) < MESSAGE_LENGTH_THRESHOLD) &&
            !isFirstMessageOfThreadInNormalChat(message, conversationThreadId) &&
            message.messageParameters.isNullOrEmpty() &&
            !containsLinebreak
    }

    val incoming = message.incoming
    val color = if (incoming) {
        if (message.isDeleted) {
            getColorFromTheme(LocalContext.current, R.color.bg_message_list_incoming_bubble_deleted)
        } else {
            getColorFromTheme(LocalContext.current, R.color.bg_message_list_incoming_bubble)
        }
    } else {
        val viewThemeUtils = LocalViewThemeUtils.current

        val outgoingBubbleColor = viewThemeUtils.talk
            .getOutgoingMessageBubbleColor(LocalContext.current, message.isDeleted, false)

        if (message.isDeleted) {
            ColorUtils.setAlphaComponent(outgoingBubbleColor, HALF_OPACITY)
        } else {
            outgoingBubbleColor
        }
    }

    val shape = if (incoming) {
        RoundedCornerShape(
            2.dp,
            20.dp,
            20.dp,
            20.dp
        )
    } else {
        RoundedCornerShape(20.dp, 2.dp, 20.dp, 20.dp)
    }

    val rowModifier = Modifier
    // val rowModifier = if (message.id == messageId && playAnimation) {
    //     Modifier.withCustomAnimation(incoming, shape)
    // } else {
    //     Modifier
    // }

    Row(
        modifier = rowModifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End
    ) {
        if (incoming) {
            val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
            val loadedImage = loadImage(message.avatarUrl, LocalContext.current, errorPlaceholderImage)
            AsyncImage(
                model = loadedImage,
                contentDescription = stringResource(R.string.user_avatar),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp)
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier
                .defaultMinSize(60.dp, 40.dp)
                .widthIn(60.dp, 280.dp)
                .heightIn(40.dp, 450.dp),
            color = Color(color),
            shape = shape
        ) {
            val modifier = if (includePadding) {
                Modifier.padding(16.dp, 4.dp, 16.dp, 4.dp)
            } else {
                Modifier
            }

            Column(modifier = modifier) {
                // TODO implement CommonMessageQuote usage
                // if (messages != null &&
                //     message.parentMessageId != null &&
                //     !message.isDeleted &&
                //     message.parentMessageId.toString() != threadId
                // ) {
                //     messages!!
                //         .find { it.parentMessageId == message.parentMessageId }
                //         .let { CommonMessageQuote(LocalContext.current, it!!) }
                // }

                if (incoming) {
                    Text(
                        message.actorDisplayName.toString(),
                        fontSize = AUTHOR_TEXT_SIZE,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                ThreadTitle(message)

                if (shouldShowTimeNextToContent(message)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        content()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp, start = 8.dp)
                        ) {
                            TimeDisplay(message)
                            ReadStatus(message)
                        }
                    }
                } else {
                    content()
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeDisplay(message)
                        ReadStatus(message)
                    }
                }
            }
        }
    }
}

@Composable
fun CommonMessageQuote(context: Context, message: ChatMessage, incoming: Boolean) {
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
            Text(message.actorDisplayName!!, fontSize = AUTHOR_TEXT_SIZE)
            val imageUri = message.imageUrl
            if (imageUri != null) {
                val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
                val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.nc_sent_an_image),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight()
                )
            }
            EnrichedText(
                message
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
fun TimeDisplay(message: ChatMessage) {
    val timeString = DateUtils(LocalContext.current)
        .getLocalTimeStringFromTimestamp(message.timestamp)
    Text(
        timeString,
        fontSize = TIME_TEXT_SIZE,
        textAlign = TextAlign.Center,
        color = colorScheme.onSurfaceVariant
    )
}

@Composable
fun ReadStatus(message: ChatMessage) {
    if (message.readStatus == ReadStatus.NONE) {
        val read = painterResource(R.drawable.ic_check_all)
        Icon(
            read,
            "",
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp),
            tint = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ThreadTitle(message: ChatMessage) {
    if (isFirstMessageOfThreadInNormalChat(message)) {
        Row {
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
                text = message.threadTitle ?: "",
                fontSize = REGULAR_TEXT_SIZE,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EnrichedText(message: ChatMessage) {
    val viewThemeUtils = LocalViewThemeUtils.current
    val messageUtils = LocalMessageUtils.current

    AndroidView(factory = { ctx ->
        var processedMessageText = messageUtils.enrichChatMessageText(
            context = ctx,
            message = message,
            incoming = message.incoming,
            viewThemeUtils = viewThemeUtils
        )

        processedMessageText = messageUtils.processMessageParameters(
            themingContext = ctx,
            viewThemeUtils = viewThemeUtils,
            spannedText = processedMessageText!!,
            message = message,
            itemView = null
        )

        EmojiTextView(ctx).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            setLineSpacing(0F, LINE_SPACING)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            text = processedMessageText
            setPadding(0, INT_8, 0, 0)
        }
    }, modifier = Modifier)
}

fun isFirstMessageOfThreadInNormalChat(message: ChatMessage, conversationThreadId: Long? = null): Boolean =
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
