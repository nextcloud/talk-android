/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.util.Linkify
import android.util.Patterns
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ScheduleSend
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.InsertEmoticon
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.viewmodels.ScheduledMessagesViewModel
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.models.json.opengraph.Reference
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_THREAD_ID
import com.nextcloud.talk.utils.message.MessageUtils
import com.vanniktech.emoji.EmojiEditText
import com.vanniktech.emoji.EmojiPopup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("LongMethod", "LargeClass", "TooManyFunctions", "COMPOSE_APPLIER_CALL_MISMATCH")
class ScheduledMessagesActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var dateUtils: DateUtils

    private lateinit var scheduledMessagesViewModel: ScheduledMessagesViewModel

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private val roomToken: String by lazy {
        intent.getStringExtra(ROOM_TOKEN).orEmpty()
    }

    private val conversationName: String by lazy {
        intent.getStringExtra(CONVERSATION_NAME).orEmpty()
    }

    private val threadId: Long? by lazy {
        if (intent.hasExtra(THREAD_ID)) {
            intent.getLongExtra(THREAD_ID, 0L)
        } else {
            null
        }
    }

    private val threadTitle: String by lazy {
        intent.getStringExtra(THREAD_TITLE).orEmpty()
    }

    private val isThreadView: Boolean by lazy {
        (threadId ?: 0L) > 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        scheduledMessagesViewModel = ViewModelProvider(this, viewModelFactory)[ScheduledMessagesViewModel::class.java]

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val currentUser by scheduledMessagesViewModel.currentUserState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                scheduledMessagesViewModel.loadCurrentUser()
            }
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                currentUser?.let { user ->
                    ScheduledMessagesScreen(
                        conversationName = conversationName,
                        scheduledMessagesViewModel = scheduledMessagesViewModel,
                        dateUtils = dateUtils,
                        viewThemeUtils = viewThemeUtils,
                        onBack = { finish() },
                        onLoadScheduledMessages = { loadScheduledMessages(user) },
                        onSendNow = { message ->
                            sendNow(message, user)
                        },
                        onReschedule = { message, sendAt, sendWithoutNotification ->
                            reschedule(message, sendAt, sendWithoutNotification, user)
                        },
                        onEdit = { message, sendAt ->
                            edit(message, sendAt, user)
                        },
                        onDeleteScheduledMessage = { message -> deleteScheduledMessage(message, user) },
                        onOpenParentMessage = { messageId ->
                            openParentMessage(messageId)
                        },
                        onOpenThread = { threadId ->
                            openThread(threadId)
                        },
                        threadTitle = threadTitle,
                        isThreadView = isThreadView
                        },
                        onCopyScheduledMessage = { message ->
                            copyScheduledMessage(message)
                        }
                    )
                }
            }
        }
    }

    private fun openThread(threadId: Long) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(KEY_ROOM_TOKEN, roomToken)
            putExtra(KEY_THREAD_ID, threadId)
        }
        startActivity(intent)
    }

    private fun loadScheduledMessages(user: User) {
        val scheduledMessagesUrl = if (isThreadView) {
            ApiUtils.getUrlForScheduledMessages(user.baseUrl, roomToken) + "?threadId=${threadId ?: 0L}"
        } else {
            ApiUtils.getUrlForScheduledMessages(user.baseUrl, roomToken)
        }
        scheduledMessagesViewModel.loadScheduledMessages(
            user.getCredentials(),
            scheduledMessagesUrl
        )
    }

    private fun sendNow(message: ChatMessage, user: User) {
        scheduledMessagesViewModel.sendNow(
            credentials = user.getCredentials(),
            sendUrl = ApiUtils.getUrlForChat(
                1,
                user.baseUrl,
                roomToken
            ),
            message = message.message.orEmpty(),
            displayName = user.displayName ?: "",
            replyTo = message.parentMessageId?.toIntOrZero() ?: 0,
            sendWithoutNotification = message.silent,
            threadTitle = message.threadTitle,
            deleteUrl = ApiUtils.getUrlForScheduledMessage(
                user.baseUrl,
                roomToken,
                message.token
            )
        )
    }

    private fun reschedule(message: ChatMessage, sendAt: Int, sendWithoutNotification: Boolean, user: User) {
        scheduledMessagesViewModel.reschedule(
            credentials = user.getCredentials(),
            url = ApiUtils.getUrlForScheduledMessage(
                user.baseUrl,
                roomToken,
                message.token
            ),
            message = message.message.orEmpty(),
            sendAt = sendAt,
            sendWithoutNotification = sendWithoutNotification
        )
    }

    private fun edit(message: ChatMessage, sendAt: Int, user: User) {
        scheduledMessagesViewModel.edit(
            credentials = user.getCredentials(),
            url = ApiUtils.getUrlForScheduledMessage(
                user.baseUrl,
                roomToken,
                message.token
            ),
            message = message.message.orEmpty(),
            sendAt = sendAt,
            sendWithoutNotification = message.silent
        )
    }

    private fun deleteScheduledMessage(message: ChatMessage, user: User) {
        scheduledMessagesViewModel.deleteScheduledMessage(
            user.getCredentials(),
            ApiUtils.getUrlForScheduledMessage(
                user.baseUrl,
                roomToken,
                message.token
            )
        )
    }

    private fun copyScheduledMessage(message: ChatMessage) {
        val parsedMessage = ChatUtils.getParsedMessage(message.message, message.messageParameters)
            .orEmpty()
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(
            resources.getString(R.string.nc_app_product_name),
            parsedMessage
        )
        clipboardManager.setPrimaryClip(clipData)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("LongParameterList", "LongMethod")
    @Composable
    private fun ScheduledMessagesScreen(
        conversationName: String,
        scheduledMessagesViewModel: ScheduledMessagesViewModel,
        dateUtils: DateUtils,
        viewThemeUtils: com.nextcloud.talk.ui.theme.ViewThemeUtils,
        onBack: () -> Unit,
        onLoadScheduledMessages: () -> Unit,
        onSendNow: (ChatMessage) -> Unit,
        onReschedule: (ChatMessage, Int, Boolean) -> Unit,
        onEdit: (ChatMessage, Int) -> Unit,
        onDeleteScheduledMessage: (ChatMessage) -> Unit,
        onOpenParentMessage: (Long?) -> Unit,
        onOpenThread: (Long) -> Unit,
        threadTitle: String,
        isThreadView: Boolean,
        onCopyScheduledMessage: (ChatMessage) -> Unit
    ) {
        val snackBarHostState = remember { SnackbarHostState() }
        val scheduledState by scheduledMessagesViewModel.getScheduledMessagesState.collectAsStateWithLifecycle()
        val sendNowState by scheduledMessagesViewModel.sendNowState.collectAsStateWithLifecycle()
        val rescheduleState by scheduledMessagesViewModel.rescheduleState.collectAsStateWithLifecycle()
        val editState by scheduledMessagesViewModel.editState.collectAsStateWithLifecycle()
        val deleteState by scheduledMessagesViewModel.deleteState.collectAsStateWithLifecycle()
        val genericErrorText = stringResource(R.string.nc_common_error_sorry)
        val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()

        var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
        var showActionsSheet by remember { mutableStateOf(false) }
        var showScheduleDialogFor by remember { mutableStateOf<ChatMessage?>(null) }

        var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
        var originalEditText by remember { mutableStateOf("") }
        var editValue by remember { mutableStateOf(TextFieldValue("")) }

        var rescheduleWithoutNotification by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { onLoadScheduledMessages() }

        LaunchedEffect(sendNowState) {
            when (sendNowState) {
                is ScheduledMessagesViewModel.SendNowMessageSuccessState -> {
                }

                is ScheduledMessagesViewModel.SendNowMessageErrorState -> {
                    snackBarHostState.showSnackbar(genericErrorText)
                }

                else -> Unit
            }
        }
        LaunchedEffect(rescheduleState) {
            when (rescheduleState) {
                is ScheduledMessagesViewModel.ScheduledMessageActionSuccessState -> onLoadScheduledMessages()
                is ScheduledMessagesViewModel.ScheduledMessageErrorState -> snackBarHostState.showSnackbar(
                    genericErrorText
                )

                else -> Unit
            }
        }

        LaunchedEffect(editState) {
            when (editState) {
                is ScheduledMessagesViewModel.ScheduledMessageActionSuccessState -> {
                    editingMessage = null
                    originalEditText = ""
                    editValue = TextFieldValue("")
                    onLoadScheduledMessages()
                }

                is ScheduledMessagesViewModel.ScheduledMessageErrorState -> snackBarHostState.showSnackbar(
                    genericErrorText
                )

                else -> Unit
            }
        }

        LaunchedEffect(deleteState) {
            when (deleteState) {
                is ScheduledMessagesViewModel.ScheduledMessageActionSuccessState -> onLoadScheduledMessages()
                is ScheduledMessagesViewModel.ScheduledMessageErrorState -> snackBarHostState.showSnackbar(
                    genericErrorText
                )

                else -> Unit
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.nc_scheduled_messages),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val title = if (isThreadView) threadTitle else conversationName
                            Text(
                                text = stringResource(
                                    R.string.nc_in_conversation,
                                    title
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackBarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(colorResource(R.color.bg_bottom_sheet))
            ) {
                when (val state = scheduledState) {
                    is ScheduledMessagesViewModel.GetScheduledMessagesSuccessState -> {
                        val parentMessages by scheduledMessagesViewModel
                            .parentMessages
                            .collectAsStateWithLifecycle()

                        val linkPreviews by scheduledMessagesViewModel
                            .linkPreviews
                            .collectAsStateWithLifecycle()

                        val visibleMessages = remember(state.messages, isThreadView, threadId) {
                            if (isThreadView) {
                                state.messages.filter { it.threadId == threadId }
                            } else {
                                state.messages
                            }
                        }

                        if (visibleMessages.isEmpty()) {
                        val linkPreviews by scheduledMessagesViewModel
                            .linkPreviews
                            .collectAsStateWithLifecycle()

                        if (state.messages.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.nc_scheduled_messages_empty),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            val zone = remember { ZoneId.systemDefault() }
                            val today = remember { LocalDate.now(zone) }

                            val sortedMessages = remember(visibleMessages) {
                                visibleMessages
                                    .sortedBy { it.sendAt?.toLong() ?: Long.MAX_VALUE }
                            }

                            val grouped = remember(sortedMessages) {
                                sortedMessages.groupBy { msg ->
                                    val sendAtSec = msg.sendAt?.toLong() ?: 0L
                                    Instant.ofEpochSecond(sendAtSec).atZone(zone).toLocalDate()
                                }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                reverseLayout = false
                            ) {
                                val datesInOrder = grouped.keys.sorted()

                                datesInOrder.forEach { date ->
                                    item(key = "header_$date") {
                                        ScheduledDayHeader(
                                            text = buildHeaderText(today = today, date = date)
                                        )
                                    }

                                    items(
                                        items = grouped[date].orEmpty(),
                                        key = { it.token ?: "" }
                                    ) { message ->

                                        LaunchedEffect(message.token, message.message) {
                                            val extractedLink = extractUrlForPreview(message)
                                            if (extractedLink != null) {
                                                scheduledMessagesViewModel.requestLinkPreview(
                                                    messageToken = message.token,
                                                    extractedLink = extractedLink
                                                )
                                            }
                                        }

                                        val parentId = message.parentMessageId
                                        val shouldShowParentPreview = !isThreadView ||
                                            (parentId != null && parentId != message.threadId)
                                        LaunchedEffect(parentId, shouldShowParentPreview) {
                                            if (parentId != null && shouldShowParentPreview) {
                                                scheduledMessagesViewModel.requestParentMessage(
                                                    token = roomToken,
                                                    parentMessageId = parentId,
                                                    threadId = message.threadId
                                                )
                                            }
                                        }
                                        val parentMessage = if (shouldShowParentPreview) {
                                            parentId?.let { parentMessages[it] }
                                        } else {
                                            null
                                        }

                                        val parentMessage = parentId?.let { parentMessages[it] }

                                        val linkPreview = message.token?.let { linkPreviews[it] }
                                        ScheduledMessageBubble(
                                            message = message,
                                            parentMessage = parentMessage,
                                            linkPreview = linkPreview,
                                            dateUtils = dateUtils,
                                            viewThemeUtils = viewThemeUtils,
                                            onClick = {
                                                if (isThreadView) {
                                                    return@ScheduledMessageBubble
                                                }
                                                val parentId = message.parentMessageId
                                                if (parentId != null) {
                                                    onOpenParentMessage(parentId)
                                                }
                                            },
                                            onLongPress = {
                                                selectedMessage = message
                                                showActionsSheet = true
                                            },
                                            isThreadView = isThreadView
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is ScheduledMessagesViewModel.GetScheduledMessagesErrorState -> {
                        ShowErrorText(isOnline)
                    }
                    else -> Spacer(modifier = Modifier.weight(1f))
                }

                OfflineStatusBanner(isOnline)

                if (editingMessage != null) {
                    ScheduledMessageEditRow(
                        editValue = editValue,
                        originalText = originalEditText,
                        onEditValueChange = { editValue = it },
                        onCancel = {
                            editingMessage = null
                            originalEditText = ""
                            editValue = TextFieldValue("")
                        },
                        onSend = {
                            val msg = editingMessage ?: return@ScheduledMessageEditRow
                            val scheduledAt = (msg.sendAt?.toLong() ?: msg.timestamp).toInt()
                            val newText = editValue.text
                            if (newText.isBlank() ||
                                newText.trim() == originalEditText.trim()
                            ) {
                                return@ScheduledMessageEditRow
                            }
                            onEdit(msg.copy(message = newText), scheduledAt)
                        }
                    )
                }
            }
        }

        if (showActionsSheet && selectedMessage != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val selectedTime = selectedMessage?.let { message ->
                val scheduledAt = message.sendAt?.toLong() ?: message.timestamp
                dateUtils.getLocalDateTimeStringFromTimestamp(scheduledAt * DateConstants.SECOND_DIVIDER)
            }.orEmpty()

            ModalBottomSheet(
                onDismissRequest = { showActionsSheet = false },
                sheetState = sheetState
            ) {
                ScheduledMessageActionsSheet(
                    scheduledTime = selectedTime,
                    onRescheduleWithNotification = {
                        rescheduleWithoutNotification = false
                        showScheduleDialogFor = selectedMessage
                        showActionsSheet = false
                    },
                    onRescheduleWithoutNotification = {
                        rescheduleWithoutNotification = true
                        showScheduleDialogFor = selectedMessage
                        showActionsSheet = false
                    },
                    onSendNow = {
                        val message = selectedMessage ?: return@ScheduledMessageActionsSheet
                        onSendNow(message)
                        showActionsSheet = false
                    },
                    onEdit = {
                        val message = selectedMessage ?: return@ScheduledMessageActionsSheet
                        val parsed =
                            ChatUtils.getParsedMessage(message.message, message.messageParameters).orEmpty()
                        editingMessage = message
                        originalEditText = parsed
                        editValue = TextFieldValue(parsed)
                        showActionsSheet = false
                    },
                    onDelete = {
                        val message = selectedMessage ?: return@ScheduledMessageActionsSheet
                        onDeleteScheduledMessage(message)
                        showActionsSheet = false
                    },
                    showOpenThreadAction = selectedMessage?.threadId != null && selectedMessage?.threadId!! > 0,
                    onOpenThread = {
                        val threadId = selectedMessage?.threadId ?: return@ScheduledMessageActionsSheet
                        if (isThreadView) {
                            return@ScheduledMessageActionsSheet
                        }
                        onOpenThread(threadId)
                        showActionsSheet = false
                    },
                    onCopy = {
                        val message = selectedMessage ?: return@ScheduledMessageActionsSheet
                        onCopyScheduledMessage(message)
                        showActionsSheet = false
                    }
                )
            }
        }

        showScheduleDialogFor?.let { message ->
            val shouldDismiss = remember { mutableStateOf(false) }
            ScheduleMessageCompose(
                initialMessage = message.message.orEmpty(),
                initialScheduledAt = message.sendAt?.toLong() ?: message.timestamp,
                viewThemeUtils = viewThemeUtils,
                onDismiss = {
                    shouldDismiss.value = true
                    showScheduleDialogFor = null
                },
                onSchedule = { scheduledTime, sendWithoutNotification ->
                    onReschedule(message, scheduledTime.toInt(), sendWithoutNotification)
                    shouldDismiss.value = true
                    showScheduleDialogFor = null
                },
                defaultSendWithoutNotification = rescheduleWithoutNotification
            ).GetScheduleDialog(shouldDismiss, LocalContext.current)
        }
    }

    @Composable
    fun OfflineStatusBanner(isOnline: Boolean) {
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.Red),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = stringResource(R.string.no_scheduled_messages_offline),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }

    @Composable
    private fun ScheduledDayHeader(text: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    private fun buildHeaderText(today: LocalDate, date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        val format = DateTimeFormatter.ofPattern("d MMMM")

        return when (days) {
            INT_0 -> "Today, ${date.format(format)}"
            INT_1 -> "Tomorrow, ${date.format(format)}"
            in INT_2..INT_6 -> "In $days days, ${date.format(format)}"
            else -> date.format(format)
        }
    }

    @Composable
    private fun ShowErrorText(isOnline: Boolean) {
        if (isOnline) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.nc_common_error_sorry),
                    modifier = Modifier
                        .padding(24.dp)
                )
            }
        }
    }

    @Suppress("ReturnCount")
    private fun extractUrlForPreview(message: ChatMessage): String? {
        val existingExtractedUrl = message.extractedUrlToPreview?.trim()
        if (!existingExtractedUrl.isNullOrBlank()) {
            return existingExtractedUrl
        }

        val messageText = ChatUtils.getParsedMessage(message.message, message.messageParameters).orEmpty()
        val matcher = Patterns.WEB_URL.matcher(messageText)
        while (matcher.find()) {
            val link = matcher.group()?.trim().orEmpty()
            if (link.startsWith("http://") || link.startsWith("https://")) {
                return link
            }
        }
        return null
    }

    @Composable
    @Suppress("LongMethod", "LongParameterList")
    private fun ScheduledMessageBubble(
        message: ChatMessage,
        parentMessage: ChatMessage?,
        linkPreview: Reference?,
        dateUtils: DateUtils,
        viewThemeUtils: com.nextcloud.talk.ui.theme.ViewThemeUtils,
        onClick: () -> Unit,
        onLongPress: () -> Unit,
        isThreadView: Boolean
    ) {
        val context = LocalContext.current
        val scheduledAt = message.sendAt?.toLong() ?: message.timestamp
        val timeText = dateUtils.getLocalTimeStringFromTimestamp(scheduledAt)
        val text = ChatUtils.getParsedMessage(message.message, message.messageParameters).orEmpty()

        val messageTextColor = LocalContentColor.current.toArgb()

        val bubbleColor = remember(context, message.isDeleted, viewThemeUtils) {
            Color(viewThemeUtils.talk.getOutgoingMessageBubbleColor(context, message.isDeleted, false))
        }

        val isClickable = remember(message.threadTitle, parentMessage, message.threadId, isThreadView) {
            val isThreadMessage = (message.threadId ?: 0L) > 0
            if (isThreadMessage) {
                false
            } else {
                (!isThreadView && !message.threadTitle.isNullOrBlank()) || parentMessage != null
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = bubbleColor,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .then(
                        if (isClickable) {
                            Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongPress
                            )
                        } else {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onLongPress
                            )
                        }
                    )
            ) {
                val strokeColor = MaterialTheme.colorScheme.primary
                Column(modifier = Modifier.padding(8.dp)) {
                    parentMessage?.let { parent ->
                        if (!isThreadView && !message.threadTitle.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_forum_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.threadTitle!!,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
                                .drawBehind {
                                    val strokeWidth = 3.dp.toPx()
                                    drawLine(
                                        color = strokeColor,
                                        start = Offset(strokeWidth / 2, 0f),
                                        end = Offset(strokeWidth / 2, size.height),
                                        strokeWidth = strokeWidth
                                    )
                                }
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parent.actorDisplayName ?: "Unknown",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                val parentMessage = ChatUtils.getParsedMessage(
                                    parent.message,
                                    parent.messageParameters
                                ).orEmpty()

                                AndroidView(
                                    factory = { androidContext ->
                                        TextView(androidContext).apply {
                                            setOnLongClickListener {
                                                onLongPress()
                                                true
                                            }
                                        }
                                    },
                                    update = { textView ->
                                        textView.setTextColor(messageTextColor)
                                        textView.text = MessageUtils(context).getRenderedMarkdownText(
                                            context,
                                            parentMessage,
                                            messageTextColor
                                        )
                                    }
                                )
                            }
                        }
                    }
                    val messageTextColor = LocalContentColor.current.toArgb()
                    val chatMessage = ChatUtils.getParsedMessage(
                        message.message,
                        message.messageParameters
                    ).orEmpty()

                    AndroidView(
                        factory = { androidContext ->
                            TextView(androidContext).apply {
                                movementMethod = NoLongClickMovementMethod.instance
                                linksClickable = true
                                setOnLongClickListener {
                                    onLongPress()
                                    true
                                }
                            }
                        },
                        update = { textView ->
                            textView.setTextColor(messageTextColor)
                            textView.text = MessageUtils(context).getRenderedMarkdownText(
                                context,
                                chatMessage,
                                messageTextColor
                            )
                            Linkify.addLinks(textView, Linkify.ALL)
                        }
                    )
                    LinkPreview(linkPreview)

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = timeText, style = MaterialTheme.typography.labelSmall)
                        if (message.silent) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LinkPreview(linkPreview: Reference?) {
        val openGraphObject = linkPreview?.openGraphObject ?: return

        Surface(
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = openGraphObject.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                openGraphObject.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                openGraphObject.link?.takeIf { it.isNotBlank() }?.let { link ->
                    Text(
                        text = link,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("LongMethod")
    @Composable
    fun ScheduledMessageEditRow(
        editValue: TextFieldValue,
        originalText: String,
        onEditValueChange: (TextFieldValue) -> Unit,
        onCancel: () -> Unit,
        onSend: () -> Unit
    ) {
        val rootView = LocalView.current
        val keyboardController = LocalSoftwareKeyboardController.current

        val emojiEditTextRef = remember { mutableStateOf<EmojiEditText?>(null) }
        var emojiPopup by remember { mutableStateOf<EmojiPopup?>(null) }
        var isEmojiOpen by remember { mutableStateOf(false) }

        val canSend =
            editValue.text.isNotBlank() &&
                editValue.text.trim() != originalText.trim()

        DisposableEffect(rootView) {
            onDispose {
                emojiPopup?.dismiss()
            }
        }

        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nc_edit_message_text),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        emojiPopup?.dismiss()
                        keyboardController?.hide()
                        onCancel()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null
                        )
                    }
                }

                Text(
                    text = originalText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val editText = emojiEditTextRef.value ?: return@IconButton

                        if (emojiPopup == null) {
                            emojiPopup = EmojiPopup(
                                rootView = rootView,
                                editText = editText,
                                onEmojiPopupShownListener = {
                                    isEmojiOpen = true
                                },
                                onEmojiPopupDismissListener = {
                                    isEmojiOpen = false
                                    keyboardController?.show()
                                }
                            )
                        }

                        emojiPopup?.toggle()
                    }) {
                        Icon(
                            imageVector = if (isEmojiOpen) {
                                Icons.Outlined.Keyboard
                            } else {
                                Icons.Outlined.InsertEmoticon
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight(),
                        factory = { context ->
                            EmojiEditText(context).apply {
                                setText(editValue.text)
                                setSelection(editValue.text.length)
                                hint = context.getString(R.string.nc_hint_enter_a_message)
                                setPadding(INT_24, INT_20, INT_24, INT_20)
                                emojiEditTextRef.value = this
                                addTextChangedListener {
                                    val text = it?.toString().orEmpty()
                                    if (text != editValue.text) {
                                        onEditValueChange(
                                            TextFieldValue(text, TextRange(text.length))
                                        )
                                    }
                                }
                            }
                        },
                        update = {
                            if (it.text.toString() != editValue.text) {
                                it.setText(editValue.text)
                            }
                        }
                    )

                    IconButton(
                        onClick = {
                            if (!canSend) return@IconButton
                            emojiEditTextRef.value?.clearFocus()
                            emojiPopup?.dismiss()
                            keyboardController?.hide()
                            onSend()
                        },
                        enabled = canSend
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }
        }
    }

    @Suppress("LongParameterList")
    @Composable
    private fun ScheduledMessageActionsSheet(
        scheduledTime: String,
        onRescheduleWithNotification: () -> Unit,
        onRescheduleWithoutNotification: () -> Unit,
        onSendNow: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        showOpenThreadAction: Boolean,
        onOpenThread: () -> Unit,
        onCopy: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.nc_scheduled_time),
                    fontSize = 13.sp,
                    color = colorResource(R.color.no_emphasis_text)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scheduledTime,
                    fontSize = 13.sp,
                    color = colorResource(R.color.no_emphasis_text)
                )
            }

            ActionRow(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.nc_copy_message),
                onClick = onCopy
            )

            ActionRow(
                icon = Icons.AutoMirrored.Outlined.ScheduleSend,
                text = stringResource(R.string.nc_reschedule_message_with_notification),
                onClick = onRescheduleWithNotification
            )
            ActionRow(
                icon = Icons.AutoMirrored.Outlined.ScheduleSend,
                text = stringResource(R.string.nc_reschedule_message_without_notification),
                onClick = onRescheduleWithoutNotification
            )
            ActionRow(
                icon = Icons.AutoMirrored.Outlined.Send,
                text = stringResource(R.string.nc_send_now),
                onClick = onSendNow
            )
            if (showOpenThreadAction && !isThreadView) {
                ActionRow(
                    icon = Icons.Outlined.Forum,
                    text = stringResource(R.string.open_thread),
                    onClick = onOpenThread
                )
            }
            ActionRow(icon = Icons.Outlined.Edit, text = stringResource(R.string.nc_edit), onClick = onEdit)
            ActionRow(icon = Icons.Outlined.Delete, text = stringResource(R.string.nc_delete), onClick = onDelete)
        }
    }

    private fun openParentMessage(messageId: Long?) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(KEY_ROOM_TOKEN, roomToken)

            messageId?.let { putExtra(BundleKeys.KEY_MESSAGE_ID, it.toString()) }
        }
        startActivity(intent)
    }

    @Composable
    private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorResource(R.color.high_emphasis_menu_icon)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = text, modifier = Modifier.weight(1f), color = colorResource(R.color.no_emphasis_text))
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    private fun PreviewActionRow() {
        MaterialTheme {
            ActionRow(icon = Icons.Outlined.Edit, text = "Edit", onClick = {})
        }
    }

    @Preview(showBackground = true)
    @Composable
    private fun PreviewEditRow() {
        var value by remember { mutableStateOf(TextFieldValue("Hello")) }
        MaterialTheme {
            ScheduledMessageEditRow(
                editValue = value,
                originalText = "Original message",
                onEditValueChange = { value = it },
                onCancel = {},
                onSend = {}
            )
        }
    }

    companion object {
        const val ROOM_TOKEN = "room_token"
        const val CONVERSATION_NAME = "conversation_name"
        const val THREAD_ID = "thread_id"
        const val THREAD_TITLE = "thread_title"
        const val INT_2: Int = 2
        const val INT_6: Int = 6
        const val INT_0: Int = 0
        const val INT_1: Int = 1
        const val INT_24: Int = 24
        const val INT_20: Int = 20
    }
}
