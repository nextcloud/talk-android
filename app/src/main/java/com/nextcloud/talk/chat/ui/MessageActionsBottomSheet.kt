/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.emojipicker.EmojiPickerPanel
import com.nextcloud.talk.emojipicker.SharedPreferencesRecentEmojiProvider
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.SpreedFeatures
import java.util.Date
import kotlinx.coroutines.launch

private const val TAG = "MessageActionsSheet"
private const val AGE_THRESHOLD_FOR_EDIT_MESSAGE = 86400000L
private const val AGE_THRESHOLD_FOR_DELETE_MESSAGE = 21600000L
private const val MAX_RECENTS = 8
private const val ACTOR_BOTS = "bots"
private const val RECENT_REACTIONS_PREFS = "recent_reaction_emojis"

private val defaultReactionEmojis = listOf("👍", "👎", "❤️", "😂", "😕", "😢", "🙏", "🔥")

data class MessageActionsState(
    val showEmojiBar: Boolean,
    val selfReactions: Set<String>,
    val showEditInfo: Boolean,
    val lastEditedBy: String,
    val lastEditedAt: String,
    val showReply: Boolean,
    val showReplyPrivately: Boolean,
    val showOpenThread: Boolean,
    val showForward: Boolean,
    val showEdit: Boolean,
    val showCopy: Boolean,
    val showCopyMessageLink: Boolean,
    val showMarkAsUnread: Boolean,
    val showRemind: Boolean,
    val showPin: Boolean,
    val isPinned: Boolean,
    val showTranslate: Boolean,
    val showShareToNote: Boolean,
    val showShare: Boolean,
    val showSave: Boolean,
    val showOpenInFiles: Boolean,
    val showDelete: Boolean
)

@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
internal fun buildMessageActionsState(
    message: ChatMessage,
    user: User?,
    conversation: ConversationModel?,
    hasChatPermission: Boolean,
    hasReactPermission: Boolean,
    spreedCapabilities: SpreedCapability,
    isOnline: Boolean,
    dateUtils: DateUtils,
    conversationThreadId: Long?
): MessageActionsState {
    val messageType = message.getCalculateMessageType()
    val messageHasFileAttachment = ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE == messageType
    val messageHasRegularText = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == messageType && !message.isDeleted
    val messageHasCaptions = messageHasFileAttachment && message.message != "{file}" && !message.isDeleted

    val isOlderThanTwentyFourHours = message.createdAt
        .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_EDIT_MESSAGE))
    val isOlderThanSixHours = message.createdAt
        .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_DELETE_MESSAGE))

    val isUserAllowedByPrivileges = if (user == null) {
        false
    } else if (message.actorId == user.userId) {
        true
    } else if (conversation != null) {
        ConversationUtils.canModerate(conversation, spreedCapabilities)
    } else {
        false
    }

    val isNoTimeLimitOnNoteToSelf =
        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.EDIT_MESSAGES_NOTE_TO_SELF) &&
            conversation?.type == ConversationEnums.ConversationType.NOTE_TO_SELF
    val isMessageBotOneToOne = message.actorType == ACTOR_BOTS &&
        (message.isOneToOneConversation || message.isFormerOneToOneConversation) &&
        !isOlderThanTwentyFourHours
    val messageIsEditable = hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.EDIT_MESSAGES) &&
        (messageHasRegularText || messageHasCaptions) &&
        !isOlderThanTwentyFourHours &&
        isUserAllowedByPrivileges &&
        hasChatPermission
    val isMessageEditable = isNoTimeLimitOnNoteToSelf || messageIsEditable || isMessageBotOneToOne

    val hasDeleteMessagesUnlimitedCapability =
        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.DELETE_MESSAGES_UNLIMITED)
    val showMessageDeletionButton = when {
        !isUserAllowedByPrivileges -> false
        !hasDeleteMessagesUnlimitedCapability && isOlderThanSixHours -> false
        message.systemMessageType != ChatMessage.SystemMessageType.DUMMY -> false
        message.isDeleted -> false
        !hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.DELETE_MESSAGES) -> false
        !hasChatPermission -> false
        else -> true
    }

    val canPin = message.isOneToOneConversation ||
        (conversation != null && ConversationUtils.isParticipantOwnerOrModerator(conversation))
    val isConversationReadOnly = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY ==
        conversation?.conversationReadOnlyState
    val isReactable = !message.isCommandMessage && !message.isDeletedCommentMessage && !message.isDeleted

    val showEmojiBar = hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.REACTIONS) &&
        hasReactPermission &&
        !isConversationReadOnly &&
        isReactable

    val editedTimestamp = message.lastEditTimestamp ?: 0L
    val showEditInfo = editedTimestamp != 0L && !message.isDeleted
    val lastEditedBy = if (showEditInfo) {
        message.lastEditActorDisplayName ?: ""
    } else {
        ""
    }
    val lastEditedAt = if (showEditInfo) {
        dateUtils.getLocalDateTimeStringFromTimestamp(editedTimestamp * DateConstants.SECOND_DIVIDER)
    } else {
        ""
    }

    val hasUserId = user?.userId?.isNotEmpty() == true && user.userId != "?"
    val hasUserActorId = message.actorType == "users" && message.actorId != conversation?.actorId

    val isClassifiedRoom = conversation != null && ConversationUtils.isClassified(conversation, spreedCapabilities)

    return MessageActionsState(
        showEmojiBar = showEmojiBar,
        selfReactions = message.reactionsSelf?.toSet() ?: emptySet(),
        showEditInfo = showEditInfo,
        lastEditedBy = lastEditedBy,
        lastEditedAt = lastEditedAt,
        showReply = message.replyable && hasChatPermission,
        showReplyPrivately = message.replyable &&
            hasUserId &&
            hasUserActorId &&
            conversation?.type != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
            isOnline &&
            !isClassifiedRoom,
        showOpenThread = message.isThread && conversationThreadId == null,
        showForward = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == messageType &&
            !(message.isDeletedCommentMessage || message.isDeleted) &&
            isOnline &&
            !isClassifiedRoom,
        showEdit = isMessageEditable,
        showCopy = !message.isDeleted,
        showCopyMessageLink = !message.isDeleted &&
            ChatMessage.MessageType.SYSTEM_MESSAGE != messageType,
        showMarkAsUnread = hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CHAT_READ_MARKER) &&
            ChatMessage.MessageType.SYSTEM_MESSAGE != messageType &&
            isOnline,
        showRemind = !message.isDeleted &&
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.REMIND_ME_LATER) &&
            isOnline,
        showPin = !message.isDeleted &&
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.PINNED_MESSAGES) &&
            isOnline &&
            canPin,
        isPinned = conversation?.lastPinnedId == message.jsonMessageId.toLong(),
        showTranslate = !message.isDeleted &&
            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == messageType &&
            CapabilitiesUtil.isTranslationsSupported(spreedCapabilities) &&
            isOnline,
        showShareToNote = !message.isDeleted &&
            !ConversationUtils.isNoteToSelfConversation(conversation) &&
            isOnline &&
            !isClassifiedRoom,
        showShare = messageHasFileAttachment || (messageHasRegularText && isOnline),
        showSave = messageHasFileAttachment,
        showOpenInFiles = messageHasFileAttachment && isOnline,
        showDelete = showMessageDeletionButton && isOnline
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsBottomSheet(
    actionsState: MessageActionsState,
    onEmojiClick: (String) -> Unit,
    onReply: () -> Unit,
    onReplyPrivately: () -> Unit,
    onOpenThread: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onCopyMessageLink: () -> Unit,
    onMarkAsUnread: () -> Unit,
    onRemind: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onTranslate: () -> Unit,
    onShareToNote: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpenInFiles: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recentEmojiProvider = remember(context) {
        SharedPreferencesRecentEmojiProvider(context, RECENT_REACTIONS_PREFS)
    }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState()
    val backToActions: () -> Unit = {
        showEmojiPicker = false
        // Return to the actions list at its normal peek height, not fully expanded.
        scope.launch { sheetState.partialExpand() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (showEmojiPicker) {
            // The sheet's own Dialog window has its own back dispatcher, separate from the
            // host Activity's - this BackHandler must be composed inside the sheet's content
            // to intercept back before the sheet's onDismissRequest closes the whole thing.
            BackHandler(onBack = backToActions)
            EmojiPickerSheetContent(
                recentEmojiProvider = recentEmojiProvider,
                onEmojiSelected = { emoji ->
                    onEmojiClick(emoji)
                    onDismiss()
                },
                onBack = backToActions
            )
        } else {
            MessageActionsSheetContent(
                actionsState = actionsState,
                recentEmojiProvider = recentEmojiProvider,
                onEmojiClick = onEmojiClick,
                onMoreEmojiClick = { showEmojiPicker = true },
                onReply = onReply,
                onReplyPrivately = onReplyPrivately,
                onOpenThread = onOpenThread,
                onForward = onForward,
                onEdit = onEdit,
                onCopy = onCopy,
                onCopyMessageLink = onCopyMessageLink,
                onMarkAsUnread = onMarkAsUnread,
                onRemind = onRemind,
                onPin = onPin,
                onUnpin = onUnpin,
                onTranslate = onTranslate,
                onShareToNote = onShareToNote,
                onShare = onShare,
                onSave = onSave,
                onOpenInFiles = onOpenInFiles,
                onDelete = onDelete,
                onDismiss = onDismiss
            )
        }
    }
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MessageActionsSheetContent(
    actionsState: MessageActionsState,
    recentEmojiProvider: RecentEmojiProvider,
    onEmojiClick: (String) -> Unit,
    onMoreEmojiClick: () -> Unit,
    onReply: () -> Unit,
    onReplyPrivately: () -> Unit,
    onOpenThread: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onCopyMessageLink: () -> Unit,
    onMarkAsUnread: () -> Unit,
    onRemind: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onTranslate: () -> Unit,
    onShareToNote: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpenInFiles: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        if (actionsState.showEmojiBar) {
            EmojiBar(
                selfReactions = actionsState.selfReactions,
                recentEmojiProvider = recentEmojiProvider,
                onEmojiClick = { emoji ->
                    onEmojiClick(emoji)
                    onDismiss()
                },
                onMoreEmojiClick = onMoreEmojiClick
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (actionsState.showEditInfo) {
                EditedInfo(
                    editedBy = actionsState.lastEditedBy,
                    editedAt = actionsState.lastEditedAt
                )
            }
            if (actionsState.showReply) {
                MessageActionItem(
                    iconRes = R.drawable.ic_reply,
                    text = stringResource(R.string.nc_reply),
                    onClick = {
                        onReply()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showReplyPrivately) {
                MessageActionItem(
                    iconRes = R.drawable.ic_reply,
                    text = stringResource(R.string.nc_reply_privately),
                    onClick = {
                        onReplyPrivately()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showOpenThread) {
                MessageActionItem(
                    iconRes = R.drawable.outline_forum_24,
                    text = stringResource(R.string.open_thread),
                    onClick = {
                        onOpenThread()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showForward) {
                MessageActionItem(
                    iconRes = R.drawable.forward_24,
                    text = stringResource(R.string.nc_forward_message),
                    onClick = {
                        onForward()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showEdit) {
                MessageActionItem(
                    iconRes = R.drawable.ic_edit_24,
                    text = stringResource(R.string.nc_edit_message),
                    onClick = {
                        onEdit()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showCopy) {
                MessageActionItem(
                    iconRes = R.drawable.ic_content_copy,
                    text = stringResource(R.string.nc_copy_message),
                    onClick = {
                        onCopy()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showCopyMessageLink) {
                MessageActionItem(
                    iconRes = R.drawable.ic_open_in_new,
                    text = stringResource(R.string.nc_copy_message_link),
                    onClick = {
                        onCopyMessageLink()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showMarkAsUnread) {
                MessageActionItem(
                    iconRes = R.drawable.ic_mark_chat_unread_24px,
                    text = stringResource(R.string.nc_mark_as_unread),
                    onClick = {
                        onMarkAsUnread()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showRemind) {
                MessageActionItem(
                    iconRes = R.drawable.ic_timer_black_24dp,
                    text = stringResource(R.string.nc_remind),
                    onClick = {
                        onRemind()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showPin) {
                if (actionsState.isPinned) {
                    MessageActionItem(
                        iconRes = R.drawable.keep_off_24px,
                        text = stringResource(R.string.unpin_message),
                        onClick = {
                            onUnpin()
                            onDismiss()
                        }
                    )
                } else {
                    MessageActionItem(
                        iconRes = R.drawable.keep_24px,
                        text = stringResource(R.string.pin_message),
                        onClick = {
                            onPin()
                            onDismiss()
                        }
                    )
                }
            }
            if (actionsState.showTranslate) {
                MessageActionItem(
                    iconRes = R.drawable.ic_baseline_translate_24,
                    text = stringResource(R.string.translate),
                    onClick = {
                        onTranslate()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showShareToNote) {
                MessageActionItem(
                    iconRes = R.drawable.ic_edit_note_24,
                    text = stringResource(R.string.add_to_notes),
                    onClick = {
                        onShareToNote()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showShare) {
                MessageActionItem(
                    iconRes = R.drawable.ic_share_action,
                    text = stringResource(R.string.share),
                    onClick = {
                        onShare()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showSave) {
                MessageActionItem(
                    iconRes = R.drawable.baseline_download_24,
                    text = stringResource(R.string.nc_save_message),
                    onClick = {
                        onSave()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showOpenInFiles) {
                MessageActionItem(
                    iconRes = R.drawable.ic_exit_to_app_black_24dp,
                    text = stringResource(R.string.open_in_files_app),
                    onClick = {
                        onOpenInFiles()
                        onDismiss()
                    }
                )
            }
            if (actionsState.showDelete) {
                MessageActionItem(
                    iconRes = R.drawable.ic_delete,
                    text = stringResource(R.string.nc_delete),
                    onClick = { showDeleteConfirmation = true }
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            onConfirm = {
                onDelete()
                onDismiss()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@Composable
internal fun EmojiBar(
    selfReactions: Set<String>,
    recentEmojiProvider: RecentEmojiProvider,
    onEmojiClick: (String) -> Unit,
    onMoreEmojiClick: () -> Unit
) {
    var emojis by remember { mutableStateOf(defaultReactionEmojis) }

    LaunchedEffect(recentEmojiProvider) {
        val recents = recentEmojiProvider.getRecentEmojiList()
        emojis = (recents + defaultReactionEmojis).distinct().take(MAX_RECENTS)
    }

    val startPadding = dimensionResource(R.dimen.standard_padding)
    val emojiButtonSize = dimensionResource(R.dimen.reaction_bottom_sheet_layout_size)
    val emojiSpacing = dimensionResource(R.dimen.standard_quarter_margin)
    val moreButtonWidth = dimensionResource(R.dimen.activity_row_layout_height)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Slot width per emoji when using Arrangement.spacedBy(emojiSpacing):
        //   each emoji contributes its own size plus one spacing gap before the next item.
        // Available space = maxWidth - startPadding - moreButtonWidth.
        // N ≤ (available) / (emojiButtonSize + emojiSpacing)
        val slotWidth = emojiButtonSize + emojiSpacing
        val availableForEmojis = maxWidth - startPadding - moreButtonWidth
        val maxVisible = maxOf(0, (availableForEmojis / slotWidth).toInt())

        Row(
            modifier = Modifier.padding(start = startPadding),
            horizontalArrangement = Arrangement.spacedBy(emojiSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            emojis.take(maxVisible).forEach { unicodeEmoji ->
                EmojiButton(
                    emoji = unicodeEmoji,
                    isSelected = selfReactions.contains(unicodeEmoji),
                    onClick = {
                        onEmojiClick(unicodeEmoji)
                        recentEmojiProvider.recordSelection(unicodeEmoji)
                    }
                )
            }
            MoreEmojiButton(onClick = onMoreEmojiClick)
        }
    }
}

@Composable
private fun EmojiButton(emoji: String, isSelected: Boolean, onClick: () -> Unit) {
    val size = dimensionResource(R.dimen.reaction_bottom_sheet_layout_size)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.size(size)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 20.sp)
        }
    }
}

@Composable
private fun MoreEmojiButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .width(dimensionResource(R.dimen.activity_row_layout_height))
            .height(dimensionResource(R.dimen.activity_row_layout_height))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dots_horizontal),
                contentDescription = stringResource(R.string.emoji_more)
            )
        }
    }
}

@Composable
private fun EmojiPickerSheetContent(
    recentEmojiProvider: RecentEmojiProvider,
    onEmojiSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val selectedTabColor = MaterialTheme.colorScheme.primary.toArgb()
    val unselectedTabColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val hintTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                EmojiPickerPanel(ContextThemeWrapper(ctx, R.style.ThemeOverlay_App_EmojiPicker)).apply {
                    searchEnabled = true
                    backspaceEnabled = false
                    setRecentEmojiProvider(recentEmojiProvider)
                    onEmojiPicked = onEmojiSelected
                    applyTheme(backgroundColor, selectedTabColor, unselectedTabColor, hintTextColor, textColor)
                }
            }
        )
    }
}

@Composable
internal fun MessageActionItem(iconRes: Int, text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.bottom_sheet_item_height)),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_dialog_padding)),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_dialog_padding)))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun EditedInfo(editedBy: String, editedAt: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimensionResource(R.dimen.standard_dialog_padding),
                end = dimensionResource(R.dimen.standard_dialog_padding),
                top = dimensionResource(R.dimen.standard_half_margin)
            )
    ) {
        if (editedBy.isNotEmpty()) {
            Text(
                text = editedBy,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (editedAt.isNotEmpty()) {
            Text(
                text = editedAt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete_black_24dp),
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.nc_delete_message)) },
        text = { Text(stringResource(R.string.message_delete_are_you_sure)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.nc_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nc_cancel))
            }
        }
    )
}

@Preview(showBackground = true, name = "Light — emoji bar")
@Preview(showBackground = true, name = "Dark — emoji bar", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEmojiBar() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            EmojiBar(
                selfReactions = setOf("👍", "❤️"),
                recentEmojiProvider = SharedPreferencesRecentEmojiProvider(
                    LocalContext.current,
                    RECENT_REACTIONS_PREFS
                ),
                onEmojiClick = {},
                onMoreEmojiClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light — actions")
@Preview(showBackground = true, name = "Dark — actions", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "RTL · Arabic", locale = "ar")
@Composable
private fun PreviewMessageActionsSheetContent() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val previewState = MessageActionsState(
        showEmojiBar = true,
        selfReactions = setOf("👍", "❤️"),
        showEditInfo = true,
        lastEditedBy = "Alice",
        lastEditedAt = "12:30",
        showReply = true,
        showReplyPrivately = true,
        showOpenThread = false,
        showForward = true,
        showEdit = true,
        showCopy = true,
        showCopyMessageLink = true,
        showMarkAsUnread = true,
        showRemind = true,
        showPin = true,
        isPinned = false,
        showTranslate = true,
        showShareToNote = true,
        showShare = true,
        showSave = false,
        showOpenInFiles = false,
        showDelete = true
    )
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            MessageActionsSheetContent(
                actionsState = previewState,
                recentEmojiProvider = SharedPreferencesRecentEmojiProvider(
                    LocalContext.current,
                    RECENT_REACTIONS_PREFS
                ),
                onEmojiClick = {},
                onMoreEmojiClick = {},
                onReply = {},
                onReplyPrivately = {},
                onOpenThread = {},
                onForward = {},
                onEdit = {},
                onCopy = {},
                onCopyMessageLink = {},
                onMarkAsUnread = {},
                onRemind = {},
                onPin = {},
                onUnpin = {},
                onTranslate = {},
                onShareToNote = {},
                onShare = {},
                onSave = {},
                onOpenInFiles = {},
                onDelete = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light — pinned")
@Composable
private fun PreviewMessageActionsSheetPinned() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            MessageActionsSheetContent(
                actionsState = MessageActionsState(
                    showEmojiBar = false,
                    selfReactions = emptySet(),
                    showEditInfo = false,
                    lastEditedBy = "",
                    lastEditedAt = "",
                    showReply = false,
                    showReplyPrivately = false,
                    showOpenThread = false,
                    showForward = false,
                    showEdit = false,
                    showCopy = true,
                    showCopyMessageLink = true,
                    showMarkAsUnread = false,
                    showRemind = false,
                    showPin = true,
                    isPinned = true,
                    showTranslate = false,
                    showShareToNote = false,
                    showShare = false,
                    showSave = false,
                    showOpenInFiles = false,
                    showDelete = false
                ),
                recentEmojiProvider = SharedPreferencesRecentEmojiProvider(
                    LocalContext.current,
                    RECENT_REACTIONS_PREFS
                ),
                onEmojiClick = {},
                onMoreEmojiClick = {},
                onReply = {},
                onReplyPrivately = {},
                onOpenThread = {},
                onForward = {},
                onEdit = {},
                onCopy = {},
                onCopyMessageLink = {},
                onMarkAsUnread = {},
                onRemind = {},
                onPin = {},
                onUnpin = {},
                onTranslate = {},
                onShareToNote = {},
                onShare = {},
                onSave = {},
                onOpenInFiles = {},
                onDelete = {},
                onDismiss = {}
            )
        }
    }
}
