/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("TooManyFunctions")

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import android.text.format.DateUtils
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.extensions.loadNoteToSelfAvatar
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.SpreedFeatures

private const val AVATAR_SIZE_DP = 48
private const val FAVORITE_OVERLAY_SIZE_DP = 16
private const val STATUS_OVERLAY_SIZE_DP = 18
private const val BADGE_OVERLAY_SIZE_DP = 18
private const val CALL_OVERLAY_SIZE_DP = 16
private const val STATUS_INTERNAL_SIZE_DP = 9f
private const val ICON_MSG_SIZE_DP = 14
private const val ICON_MSG_SPACING_DP = 2
private const val UNREAD_THRESHOLD = 1000
private const val UNREAD_BUBBLE_STROKE_DP = 1.5f
private const val MILLIS_PER_SECOND = 1_000L

private sealed class AvatarContent {
    data class Url(val url: String) : AvatarContent()
    data class Res(@param:DrawableRes val resId: Int) : AvatarContent()
    object System : AvatarContent()
    object NoteToSelf : AvatarContent()
}

private fun buildAvatarContent(model: ConversationModel, currentUser: User, isDark: Boolean): AvatarContent {
    val avatarVersion = model.avatarVersion.takeIf { it.isNotEmpty() }
    return when {
        model.objectType == ConversationEnums.ObjectType.SHARE_PASSWORD ->
            AvatarContent.Res(R.drawable.ic_circular_lock)

        model.objectType == ConversationEnums.ObjectType.FILE ->
            AvatarContent.Res(R.drawable.ic_avatar_document)

        model.type == ConversationEnums.ConversationType.ROOM_SYSTEM ->
            AvatarContent.System

        model.type == ConversationEnums.ConversationType.NOTE_TO_SELF ->
            AvatarContent.NoteToSelf

        model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ->
            AvatarContent.Url(ApiUtils.getUrlForAvatar(currentUser.baseUrl, model.name, false, isDark))

        else ->
            AvatarContent.Url(
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    1,
                    currentUser.baseUrl,
                    model.token,
                    isDark,
                    avatarVersion
                )
            )
    }
}

/** Groups the tap callbacks for [ConversationListItem] to keep the parameter count low. */
data class ConversationListItemCallbacks(val onClick: () -> Unit, val onLongClick: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListItem(
    model: ConversationModel,
    currentUser: User,
    callbacks: ConversationListItemCallbacks,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    val chatMessage = remember(model.lastMessage, currentUser) {
        model.lastMessage?.toDomainModel()?.also { it.activeUser = currentUser }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = callbacks.onClick, onLongClick = callbacks.onLongClick)
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        ConversationAvatar(
            model = model,
            currentUser = currentUser,
            modifier = Modifier
                .size(AVATAR_SIZE_DP.dp)
                .align(Alignment.CenterVertically)
        )
        Spacer(Modifier.width(16.dp))

        if (model.hasSensitive) {
            SensitiveContent(model = model, currentUser = currentUser)
        } else {
            FullContent(model = model, currentUser = currentUser, chatMessage = chatMessage, searchQuery = searchQuery)
        }
    }
}

@Composable
private fun RowScope.SensitiveContent(model: ConversationModel, currentUser: User) {
    Row(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = model.displayName,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (model.unreadMessages > 0) FontWeight.Bold else FontWeight.Normal,
            color = colorResource(R.color.conversation_item_header)
        )
        UnreadBubble(model = model, currentUser = currentUser)
    }
}

@Composable
private fun RowScope.FullContent(
    model: ConversationModel,
    currentUser: User,
    chatMessage: ChatMessage?,
    searchQuery: String = ""
) {
    Column(modifier = Modifier.weight(1f)) {
        ConversationNameRow(model = model, chatMessage = chatMessage, searchQuery = searchQuery)
        Spacer(Modifier.height(4.dp))
        ConversationLastMessageRow(
            model = model,
            currentUser = currentUser,
            chatMessage = chatMessage,
            searchQuery = searchQuery
        )
    }
}

@Composable
private fun ConversationAvatar(model: ConversationModel, currentUser: User, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        ConversationAvatarImage(
            model = model,
            currentUser = currentUser,
            modifier = Modifier
                .size(AVATAR_SIZE_DP.dp)
                .clip(CircleShape)
        )

        if (model.favorite) {
            FavoriteOverlay(
                modifier = Modifier
                    .size(FAVORITE_OVERLAY_SIZE_DP.dp)
                    .align(Alignment.TopEnd)
            )
        }

        if (model.hasCall) {
            ActiveCallOverlay(
                modifier = Modifier
                    .size(CALL_OVERLAY_SIZE_DP.dp)
                    .align(Alignment.TopEnd)
            )
        }

        if (model.type != ConversationEnums.ConversationType.ROOM_SYSTEM) {
            StatusOverlay(
                model = model,
                modifier = Modifier
                    .size(STATUS_OVERLAY_SIZE_DP.dp)
                    .align(Alignment.BottomEnd)
            )
        }

        PublicBadgeOverlay(
            model = model,
            modifier = Modifier
                .size(BADGE_OVERLAY_SIZE_DP.dp)
                .align(Alignment.BottomEnd)
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun ConversationAvatarImage(model: ConversationModel, currentUser: User, modifier: Modifier = Modifier) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val isDark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val credentials = remember(currentUser.id) {
        ApiUtils.getCredentials(currentUser.username, currentUser.token) ?: ""
    }
    val avatarContent = buildAvatarContent(model = model, currentUser = currentUser, isDark = isDark)

    when (avatarContent) {
        is AvatarContent.Url -> {
            if (isInPreview) {
                Box(modifier = modifier.background(Color.LightGray))
            } else {
                val request = remember(avatarContent.url, credentials) {
                    ImageRequest.Builder(context)
                        .data(avatarContent.url)
                        .diskCacheKey("${avatarContent.url}#v2")
                        .addHeader("Authorization", credentials)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.avatar),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.account_circle_96dp),
                    error = painterResource(R.drawable.account_circle_96dp),
                    modifier = modifier
                )
            }
        }

        is AvatarContent.Res -> {
            if (isInPreview) {
                Box(modifier = modifier.background(Color.LightGray))
            } else {
                AsyncImage(
                    model = avatarContent.resId,
                    contentDescription = stringResource(R.string.avatar),
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                )
            }
        }

        AvatarContent.System -> {
            if (isInPreview) {
                Box(
                    modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { loadSystemAvatar() }
                    },
                    modifier = modifier
                )
            }
        }

        AvatarContent.NoteToSelf -> {
            if (isInPreview) {
                Box(
                    modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_note_to_self),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { loadNoteToSelfAvatar() }
                    },
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun FavoriteOverlay(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_star_black_24dp),
        contentDescription = stringResource(R.string.starred),
        tint = colorResource(R.color.favorite_icon_tint),
        modifier = modifier
    )
}

@Composable
private fun ActiveCallOverlay(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_videocam_24px),
        contentDescription = null,
        tint = Color.Red,
        modifier = modifier
    )
}

@Composable
private fun StatusOverlay(model: ConversationModel, modifier: Modifier = Modifier) {
    val isInPreview = LocalInspectionMode.current

    if (isInPreview) {
        if (model.statusIcon != null) {
            Text(
                text = model.statusIcon!!,
                modifier = modifier,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            val drawableRes = when (model.status) {
                "online" -> R.drawable.online_status
                "away" -> R.drawable.ic_user_status_away
                "busy" -> R.drawable.ic_user_status_busy
                "dnd" -> R.drawable.ic_user_status_dnd
                else -> null
            }
            if (drawableRes != null) {
                Icon(
                    painter = painterResource(drawableRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = modifier
                )
            }
        }
    } else {
        val context = LocalContext.current
        val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    val sizePx = DisplayUtils.convertDpToPixel(STATUS_INTERNAL_SIZE_DP, ctx)
                    setImageDrawable(
                        StatusDrawable(model.status, model.statusIcon, sizePx, surfaceArgb, ctx)
                    )
                }
            },
            update = { imageView ->
                val sizePx = DisplayUtils.convertDpToPixel(STATUS_INTERNAL_SIZE_DP, context)
                imageView.setImageDrawable(
                    StatusDrawable(model.status, model.statusIcon, sizePx, surfaceArgb, context)
                )
            },
            modifier = modifier
        )
    }
}

@Composable
private fun PublicBadgeOverlay(model: ConversationModel, modifier: Modifier = Modifier) {
    val badgeRes = when {
        model.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
            R.drawable.ic_avatar_link

        model.remoteServer?.isNotEmpty() == true ->
            R.drawable.ic_avatar_federation

        else -> null
    } ?: return

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, CircleShape)
    ) {
        Icon(
            painter = painterResource(badgeRes),
            contentDescription = stringResource(R.string.nc_public_call_status),
            tint = colorResource(R.color.no_emphasis_text),
            modifier = Modifier
                .padding(1.dp)
                .fillMaxSize()
        )
    }
}

@Composable
private fun ConversationNameRow(model: ConversationModel, chatMessage: ChatMessage?, searchQuery: String = "") {
    val hasDraft = model.messageDraft?.messageText?.isNotBlank() == true
    val showDate = chatMessage != null || hasDraft
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildHighlightedText(model.displayName, searchQuery, primaryColor),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (model.unreadMessages > 0) FontWeight.Bold else FontWeight.Normal,
            color = colorResource(R.color.conversation_item_header)
        )
        if (showDate) {
            Spacer(Modifier.width(4.dp))
            val dateText = remember(model.lastActivity) {
                if (model.lastActivity > 0L) {
                    DateUtils.getRelativeTimeSpanString(
                        model.lastActivity * MILLIS_PER_SECOND,
                        System.currentTimeMillis(),
                        0L,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    ).toString()
                } else {
                    ""
                }
            }
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = colorResource(R.color.textColorMaxContrast)
            )
        }
    }
}

@Composable
private fun ConversationLastMessageRow(
    model: ConversationModel,
    currentUser: User,
    chatMessage: ChatMessage?,
    searchQuery: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LastMessageContent(
            model = model,
            currentUser = currentUser,
            chatMessage = chatMessage,
            searchQuery = searchQuery,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        UnreadBubble(model = model, currentUser = currentUser)
    }
}

@Composable
private fun UnreadBubble(model: ConversationModel, currentUser: User) {
    if (model.unreadMessages <= 0) return

    val text = if (model.unreadMessages >= UNREAD_THRESHOLD) {
        stringResource(R.string.tooManyUnreadMessages)
    } else {
        model.unreadMessages.toString()
    }

    val isOneToOne = model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
    val hasDmFlag = hasSpreedFeatureCapability(
        currentUser.capabilities?.spreedCapability,
        SpreedFeatures.DIRECT_MENTION_FLAG
    )
    val outlined = model.unreadMention && hasDmFlag && !model.unreadMentionDirect && !isOneToOne

    when {
        outlined -> OutlinedUnreadChip(text = text)
        isOneToOne || model.unreadMention -> FilledUnreadChip(text = text)
        else -> GreyUnreadChip(text = text)
    }
}

@Composable
private fun FilledUnreadChip(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OutlinedUnreadChip(text: String) {
    Box(
        modifier = Modifier
            .border(UNREAD_BUBBLE_STROKE_DP.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GreyUnreadChip(text: String) {
    Box(
        modifier = Modifier
            .background(colorResource(R.color.conversation_unread_bubble), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colorResource(R.color.conversation_unread_bubble_text),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
@Composable
private fun LastMessageContent(
    model: ConversationModel,
    currentUser: User,
    chatMessage: ChatMessage?,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    val isBold = model.unreadMessages > 0
    val fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
    val primaryColor = MaterialTheme.colorScheme.primary

    // Draft
    val draftText = model.messageDraft?.messageText?.takeIf { it.isNotBlank() }
    if (draftText != null) {
        val draftPrefixTemplate = stringResource(R.string.nc_draft_prefix)
        val fullLabel = remember(draftText, draftPrefixTemplate) {
            String.format(draftPrefixTemplate, draftText)
        }
        val prefixEnd = fullLabel.length - draftText.length
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                append(fullLabel.substring(0, prefixEnd))
            }
            append(buildHighlightedText(draftText, searchQuery, primaryColor))
        }
        Text(
            text = annotated,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = fontWeight,
            color = colorResource(R.color.textColorMaxContrast)
        )
        return
    }

    // No message
    if (chatMessage == null) {
        Text(text = "", modifier = modifier)
        return
    }

    // Deleted comment
    if (chatMessage.isDeletedCommentMessage) {
        val parsedText = ChatUtils.getParsedMessage(chatMessage.message, chatMessage.messageParameters) ?: ""
        val youPrefix = stringResource(R.string.nc_formatted_message_you, parsedText)
        val groupFormat = stringResource(R.string.nc_formatted_message)
        val guestLabel = stringResource(R.string.nc_guest)
        val displayText = when {
            chatMessage.actorId == currentUser.userId -> youPrefix
            model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> parsedText
            else -> {
                val actorName = chatMessage.actorDisplayName?.takeIf { it.isNotBlank() }
                    ?: if (chatMessage.actorType == "guests" || chatMessage.actorType == "emails") {
                        guestLabel
                    } else {
                        ""
                    }
                if (actorName.isBlank()) parsedText else String.format(groupFormat, actorName, parsedText)
            }
        }
        Text(
            text = buildHighlightedText(displayText, searchQuery, primaryColor),
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val msgType = chatMessage.getCalculateMessageType()

    // System message
    if (msgType == ChatMessage.MessageType.SYSTEM_MESSAGE ||
        model.type == ConversationEnums.ConversationType.ROOM_SYSTEM
    ) {
        val parsedText = ChatUtils.getParsedMessage(chatMessage.message, chatMessage.messageParameters) ?: ""
        Text(
            text = buildHighlightedText(parsedText, searchQuery, primaryColor),
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = fontWeight,
            color = colorResource(R.color.textColorMaxContrast)
        )
        return
    }

    // Attachment / special message types
    when (msgType) {
        ChatMessage.MessageType.VOICE_MESSAGE -> {
            val name = chatMessage.messageParameters?.get("file")?.get("name") ?: ""
            val prefix = authorPrefix(chatMessage, currentUser)
            AttachmentRow(
                authorPrefix = prefix,
                iconRes = R.drawable.baseline_mic_24,
                name = name,
                fontWeight = fontWeight,
                modifier = modifier,
                searchQuery = searchQuery
            )
            return
        }

        ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
            var name = chatMessage.message ?: ""
            name = if (name == "{file}") {
                chatMessage.messageParameters?.get("file")?.get("name") ?: ""
            } else {
                ChatUtils.getParsedMessage(name, chatMessage.messageParameters) ?: name
            }
            val mime = chatMessage.messageParameters?.get("file")?.get("mimetype")
            val icon = attachmentIconRes(mime)
            val prefix = authorPrefix(chatMessage, currentUser)
            AttachmentRow(
                authorPrefix = prefix,
                iconRes = icon,
                name = name,
                fontWeight = fontWeight,
                modifier = modifier,
                searchQuery = searchQuery
            )
            return
        }

        ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
            val name = chatMessage.messageParameters?.get("object")?.get("name") ?: ""
            val prefix = authorPrefix(chatMessage, currentUser)
            AttachmentRow(
                authorPrefix = prefix,
                iconRes = R.drawable.baseline_location_pin_24,
                name = name,
                fontWeight = fontWeight,
                modifier = modifier,
                searchQuery = searchQuery
            )
            return
        }

        ChatMessage.MessageType.POLL_MESSAGE -> {
            val name = chatMessage.messageParameters?.get("object")?.get("name") ?: ""
            val prefix = authorPrefix(chatMessage, currentUser)
            AttachmentRow(
                authorPrefix = prefix,
                iconRes = R.drawable.baseline_bar_chart_24,
                name = name,
                fontWeight = fontWeight,
                modifier = modifier,
                searchQuery = searchQuery
            )
            return
        }

        ChatMessage.MessageType.DECK_CARD -> {
            val name = chatMessage.messageParameters?.get("object")?.get("name") ?: ""
            val prefix = authorPrefix(chatMessage, currentUser)
            AttachmentRow(
                authorPrefix = prefix,
                iconRes = R.drawable.baseline_article_24,
                name = name,
                fontWeight = fontWeight,
                modifier = modifier,
                searchQuery = searchQuery
            )
            return
        }

        ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE,
        ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE,
        ChatMessage.MessageType.SINGLE_LINK_GIF_MESSAGE -> {
            val gifSelf = stringResource(R.string.nc_sent_a_gif_you)
            val gifOther = stringResource(R.string.nc_sent_a_gif, chatMessage.actorDisplayName ?: "")
            Text(
                text = if (chatMessage.actorId == currentUser.userId) gifSelf else gifOther,
                modifier = modifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = fontWeight,
                color = colorResource(R.color.textColorMaxContrast)
            )
            return
        }

        ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE -> {
            val imgSelf = stringResource(R.string.nc_sent_an_image_you)
            val imgOther = stringResource(R.string.nc_sent_an_image, chatMessage.actorDisplayName ?: "")
            Text(
                text = if (chatMessage.actorId == currentUser.userId) imgSelf else imgOther,
                modifier = modifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = fontWeight,
                color = colorResource(R.color.textColorMaxContrast)
            )
            return
        }

        ChatMessage.MessageType.SINGLE_LINK_VIDEO_MESSAGE -> {
            val vidSelf = stringResource(R.string.nc_sent_a_video_you)
            val vidOther = stringResource(R.string.nc_sent_a_video, chatMessage.actorDisplayName ?: "")
            Text(
                text = if (chatMessage.actorId == currentUser.userId) vidSelf else vidOther,
                modifier = modifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = fontWeight,
                color = colorResource(R.color.textColorMaxContrast)
            )
            return
        }

        ChatMessage.MessageType.SINGLE_LINK_AUDIO_MESSAGE -> {
            val audSelf = stringResource(R.string.nc_sent_an_audio_you)
            val audOther = stringResource(R.string.nc_sent_an_audio, chatMessage.actorDisplayName ?: "")
            Text(
                text = if (chatMessage.actorId == currentUser.userId) audSelf else audOther,
                modifier = modifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = fontWeight,
                color = colorResource(R.color.textColorMaxContrast)
            )
            return
        }

        else -> { /* fall through to regular text */ }
    }

    // Regular text message
    val parsedText = ChatUtils.getParsedMessage(chatMessage.message, chatMessage.messageParameters) ?: ""
    val youPrefix = stringResource(R.string.nc_formatted_message_you, parsedText)
    val groupFormat = stringResource(R.string.nc_formatted_message)
    val guestLabel = stringResource(R.string.nc_guest)
    val displayText = when {
        chatMessage.actorId == currentUser.userId -> youPrefix
        model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> parsedText
        else -> {
            val actorName = chatMessage.actorDisplayName?.takeIf { it.isNotBlank() }
                ?: if (chatMessage.actorType == "guests" || chatMessage.actorType == "emails") {
                    guestLabel
                } else {
                    ""
                }
            if (actorName.isBlank()) parsedText else String.format(groupFormat, actorName, parsedText)
        }
    }
    Text(
        text = buildHighlightedText(displayText, searchQuery, primaryColor),
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        color = colorResource(R.color.textColorMaxContrast)
    )
}

@Suppress("LongParameterList")
@Composable
private fun AttachmentRow(
    authorPrefix: String,
    @DrawableRes iconRes: Int?,
    name: String,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (authorPrefix.isNotBlank()) {
            Text(
                text = "$authorPrefix ",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = fontWeight,
                maxLines = 1,
                color = colorResource(R.color.textColorMaxContrast)
            )
        }
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ICON_MSG_SIZE_DP.dp)
            )
            Spacer(Modifier.width(ICON_MSG_SPACING_DP.dp))
        }
        Text(
            text = buildHighlightedText(name, searchQuery, primaryColor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = colorResource(R.color.textColorMaxContrast)
        )
    }
}

private fun authorPrefix(chatMessage: ChatMessage, currentUser: User): String =
    if (chatMessage.actorId == currentUser.userId) {
        "You:"
    } else {
        val name = chatMessage.actorDisplayName
        if (!name.isNullOrBlank()) "$name:" else ""
    }

private fun attachmentIconRes(mimetype: String?): Int? =
    when {
        mimetype == null -> null
        mimetype.contains("image") -> R.drawable.baseline_image_24
        mimetype.contains("video") -> R.drawable.baseline_video_24
        mimetype.contains("application") -> R.drawable.baseline_insert_drive_file_24
        mimetype.contains("audio") -> R.drawable.baseline_audiotrack_24
        mimetype.contains("text/vcard") -> R.drawable.baseline_contacts_24
        else -> null
    }

private fun previewUser(userId: String = "user1") =
    User(
        id = 1L,
        userId = userId,
        username = userId,
        baseUrl = "https://cloud.example.com",
        token = "token",
        displayName = "Test User",
        capabilities = null
    )

@Suppress("LongParameterList")
private fun previewModel(
    token: String = "abc",
    displayName: String = "Alice",
    type: ConversationEnums.ConversationType = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
    objectType: ConversationEnums.ObjectType = ConversationEnums.ObjectType.DEFAULT,
    unreadMessages: Int = 0,
    unreadMention: Boolean = false,
    unreadMentionDirect: Boolean = false,
    favorite: Boolean = false,
    hasCall: Boolean = false,
    hasSensitive: Boolean = false,
    hasArchived: Boolean = false,
    status: String? = null,
    statusIcon: String? = null,
    remoteServer: String? = null,
    lastMessage: ChatMessageJson? = null,
    messageDraft: MessageDraft? = null,
    lobbyState: ConversationEnums.LobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
    readOnlyState: ConversationEnums.ConversationReadOnlyState =
        ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE
) = ConversationModel(
    internalId = "1@$token",
    accountId = 1L,
    token = token,
    name = "testuser",
    displayName = displayName,
    description = "",
    type = type,
    participantType = Participant.ParticipantType.USER,
    sessionId = "s",
    actorId = "a",
    actorType = "users",
    objectType = objectType,
    notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
    conversationReadOnlyState = readOnlyState,
    lobbyState = lobbyState,
    lobbyTimer = 0L,
    canLeaveConversation = true,
    canDeleteConversation = true,
    unreadMentionDirect = unreadMentionDirect,
    notificationCalls = 0,
    avatarVersion = "",
    hasCustomAvatar = false,
    callStartTime = 0L,
    unreadMessages = unreadMessages,
    unreadMention = unreadMention,
    favorite = favorite,
    hasCall = hasCall,
    hasSensitive = hasSensitive,
    hasArchived = hasArchived,
    status = status,
    statusIcon = statusIcon,
    remoteServer = remoteServer,
    lastMessage = lastMessage,
    messageDraft = messageDraft,
    lastActivity = System.currentTimeMillis() / 1000L - 3600L
)

@Suppress("LongParameterList")
private fun previewMsg(
    actorId: String = "other",
    actorDisplayName: String = "Bob",
    message: String = "Hello there",
    messageType: String = "comment",
    systemMessageType: ChatMessage.SystemMessageType? = null,
    messageParameters: HashMap<String?, HashMap<String?, String?>>? = null
) = ChatMessageJson(
    id = 1L,
    actorId = actorId,
    actorDisplayName = actorDisplayName,
    message = message,
    messageType = messageType,
    systemMessageType = systemMessageType,
    messageParameters = messageParameters
)

@Composable
private fun PreviewWrapper(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            content()
        }
    }
}

// Section A - Conversation Type

@Preview(name = "A1 - 1:1 online")
@Composable
private fun PreviewOneToOne() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
                status = "online",
                lastMessage = previewMsg(message = "Hey, how are you?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "A2 - Group", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewGroup() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Project Team",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                lastMessage = previewMsg(message = "Meeting at 3pm")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "A3 - Group no avatar")
@Composable
private fun PreviewGroupNoAvatar() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Team Chat",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                lastMessage = previewMsg(message = "Anyone free?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "A4 - Public room", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPublicRoom() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Open Room",
                type = ConversationEnums.ConversationType.ROOM_PUBLIC_CALL,
                lastMessage = previewMsg(message = "Welcome everyone!")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "A5 - System room")
@Composable
private fun PreviewSystemRoom() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Nextcloud",
                type = ConversationEnums.ConversationType.ROOM_SYSTEM,
                lastMessage = previewMsg(message = "You joined the conversation", messageType = "system")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "A6 - Note to self", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNoteToSelf() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Personal notes",
                type = ConversationEnums.ConversationType.NOTE_TO_SELF,
                lastMessage = previewMsg(message = "Reminder: buy groceries")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "A7 - Former 1:1")
@Composable
private fun PreviewFormerOneToOne() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Deleted User",
                type = ConversationEnums.ConversationType.FORMER_ONE_TO_ONE,
                lastMessage = previewMsg(message = "Last message before leaving")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section B - ObjectType / Special Avatar

@Preview(name = "B8 - Password protected", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPasswordProtected() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Protected room",
                objectType = ConversationEnums.ObjectType.SHARE_PASSWORD,
                type = ConversationEnums.ConversationType.ROOM_PUBLIC_CALL,
                lastMessage = previewMsg(message = "Enter password to join")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "B9 - File room")
@Composable
private fun PreviewFileRoom() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "document.pdf",
                objectType = ConversationEnums.ObjectType.FILE,
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                lastMessage = previewMsg(message = "What do you think about this file?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "B10 - Phone temporary room", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPhoneNumberRoom() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "+49 170 1234567",
                objectType = ConversationEnums.ObjectType.PHONE_TEMPORARY,
                type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
                lastMessage = previewMsg(message = "Missed call")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section C - Federated

@Preview(name = "C11 - Federated")
@Composable
private fun PreviewFederated() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Remote Friend",
                type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
                remoteServer = "https://other.cloud.com",
                lastMessage = previewMsg(message = "Hi from another server!")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section D - Unread States

@Preview(name = "D12 - No unread", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNoUnread() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                unreadMessages = 0,
                lastMessage = previewMsg(message = "See you tomorrow")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "D13 - Unread few (5)")
@Composable
private fun PreviewUnreadFew() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                unreadMessages = 5,
                lastMessage = previewMsg(message = "Did you see this?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "D14 - Unread many (1500)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewUnreadMany() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Busy Channel",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                unreadMessages = 1500,
                lastMessage = previewMsg(message = "So many messages!")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "D15 - Unread mention group (outlined)")
@Composable
private fun PreviewUnreadMentionGroup() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Dev Team",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                unreadMessages = 3,
                unreadMention = true,
                unreadMentionDirect = false,
                lastMessage = previewMsg(message = "@user1 please review PR")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "D16 - Unread mention direct 1:1 (filled)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewUnreadMentionDirect1to1() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
                unreadMessages = 2,
                unreadMention = true,
                unreadMentionDirect = true,
                lastMessage = previewMsg(message = "Did you see my message?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "D17 - Unread mention group direct (filled)")
@Composable
private fun PreviewUnreadMentionGroupDirect() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Ops Team",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                unreadMessages = 7,
                unreadMention = true,
                unreadMentionDirect = true,
                lastMessage = previewMsg(message = "@user1 urgent!")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section E - Favorite

@Preview(name = "E18 - Favorite", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFavorite() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Best Friend",
                favorite = true,
                lastMessage = previewMsg(message = "Let's meet up!")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "E19 - Not favorite")
@Composable
private fun PreviewNotFavorite() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Colleague",
                favorite = false,
                lastMessage = previewMsg(message = "See the report?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section F - Status (1:1 only)

@Preview(name = "F20 - Status online", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewStatusOnline() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(displayName = "Alice", status = "online", lastMessage = previewMsg()),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "F21 - Status away")
@Composable
private fun PreviewStatusAway() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(displayName = "Bob", status = "away", lastMessage = previewMsg()),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "F22 - Status DND", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewStatusDnd() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(displayName = "Carol", status = "dnd", lastMessage = previewMsg()),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "F23 - Status offline")
@Composable
private fun PreviewStatusOffline() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(displayName = "Dave", status = "offline", lastMessage = previewMsg()),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "F24 - Status with emoji", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewStatusWithEmoji() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Eve",
                status = "online",
                statusIcon = "?",
                lastMessage = previewMsg(message = "Grabbing coffee")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section G - Last Message Types

@Preview(name = "G25 - Own regular text")
@Composable
private fun PreviewLastMessageOwnText() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                displayName = "?? Team",
                lastMessage = previewMsg(actorId = "user1", actorDisplayName = "Me", message = "Good morning!")
            ),
            currentUser = previewUser("user1"),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G26 - Other regular text", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageOtherText() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                displayName = "Team",
                lastMessage = previewMsg(actorId = "user2", actorDisplayName = "Alice", message = "Good morning!")
            ),
            currentUser = previewUser("user1"),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G27 - System message")
@Composable
private fun PreviewLastMessageSystem() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "Alice joined the call",
                    messageType = "system",
                    systemMessageType = ChatMessage.SystemMessageType.CALL_STARTED
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G28 - Voice message", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageVoice() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "voice-message.mp3",
                    messageType = "voice-message",
                    messageParameters = hashMapOf("file" to hashMapOf("name" to "voice_001.mp3"))
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G29 - Image attachment")
@Composable
private fun PreviewLastMessageImage() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "{file}",
                    messageParameters = hashMapOf(
                        "file" to hashMapOf("name" to "photo.jpg", "mimetype" to "image/jpeg")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G30 - Video attachment", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageVideo() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "{file}",
                    messageParameters = hashMapOf(
                        "file" to hashMapOf("name" to "clip.mp4", "mimetype" to "video/mp4")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G31 - Audio attachment")
@Composable
private fun PreviewLastMessageAudio() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "{file}",
                    messageParameters = hashMapOf(
                        "file" to hashMapOf("name" to "song.mp3", "mimetype" to "audio/mpeg")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G32 - File attachment", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageFile() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "{file}",
                    messageParameters = hashMapOf(
                        "file" to hashMapOf("name" to "report.pdf", "mimetype" to "application/pdf")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G33 - GIF message")
@Composable
private fun PreviewLastMessageGif() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(message = "https://giphy.com/gif.gif", messageType = "comment")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G34 - Location message", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageLocation() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "geo:48.8566,2.3522",
                    messageParameters = hashMapOf(
                        "object" to hashMapOf("name" to "Eiffel Tower", "type" to "geo-location")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G35 - Poll message")
@Composable
private fun PreviewLastMessagePoll() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Team",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                lastMessage = previewMsg(
                    message = "{object}",
                    messageParameters = hashMapOf(
                        "object" to hashMapOf("name" to "Best framework?", "type" to "talk-poll")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G36 - Deck card", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageDeck() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "{object}",
                    messageParameters = hashMapOf(
                        "object" to hashMapOf("name" to "Sprint backlog item", "type" to "deck-card")
                    )
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G37 - Deleted message")
@Composable
private fun PreviewLastMessageDeleted() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(
                    message = "You: Message deleted",
                    messageType = "comment_deleted"
                )
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G38 - No last message", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNoLastMessage() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(displayName = "New Conversation", lastMessage = null),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G39 - Draft")
@Composable
private fun PreviewDraft() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                messageDraft = MessageDraft(messageText = "I was going to say-")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "G49 - Text with emoji", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLastMessageTextWithEmoji() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                lastMessage = previewMsg(message = "Sch-nes Wochenende! ????")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section H - Call Status

@Preview(name = "H40 - Active call")
@Composable
private fun PreviewActiveCall() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Team Stand-up",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                hasCall = true,
                lastMessage = previewMsg(message = "Call in progress")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section I - Lobby / Read-only

@Preview(name = "I41 - Lobby active", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLobbyActive() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "VIP Room",
                type = ConversationEnums.ConversationType.ROOM_PUBLIC_CALL,
                lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY,
                lastMessage = previewMsg(message = "Waiting for moderator to let you in")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "I42 - Read only")
@Composable
private fun PreviewReadOnly() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Announcements",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                readOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY,
                lastMessage = previewMsg(message = "Important update posted")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section J - Sensitive

@Preview(name = "J43 - Sensitive (name only)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSensitive() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Confidential Project",
                hasSensitive = true,
                unreadMessages = 3,
                lastMessage = previewMsg(message = "This text should be hidden")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section K - Archived

@Preview(name = "K44 - Archived")
@Composable
private fun PreviewArchived() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "Old Project",
                hasArchived = true,
                lastMessage = previewMsg(message = "Project completed ?")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

// Section L - UI Variants

@Preview(
    name = "L45 - Dark mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewDarkMode() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(
                displayName = "Alice",
                unreadMessages = 4,
                lastMessage = previewMsg(message = "Good night!")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "L46 - Long name (truncation)")
@Composable
private fun PreviewLongName() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "This Is A Very Long Conversation Name That Should Be Truncated With Ellipsis",
                lastMessage = previewMsg(message = "Short message")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "L47 - Short content, no date", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewShortContent() =
    PreviewWrapper(darkTheme = true) {
        ConversationListItem(
            model = previewModel(displayName = "Hi", lastMessage = null),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }

@Preview(name = "L48 - RTL (Arabic)", locale = "ar")
@Composable
private fun PreviewRtl() =
    PreviewWrapper {
        ConversationListItem(
            model = previewModel(
                displayName = "??????",
                unreadMessages = 2,
                lastMessage = previewMsg(message = "????? ??? ????")
            ),
            currentUser = previewUser(),
            callbacks = ConversationListItemCallbacks(onClick = {}, onLongClick = {})
        )
    }
