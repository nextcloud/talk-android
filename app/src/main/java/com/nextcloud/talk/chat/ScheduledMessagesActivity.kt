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
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
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
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.ui.model.toScheduledMessageUiModel
import com.nextcloud.talk.chat.viewmodels.ScheduledMessagesViewModel
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.ui.chat.ChatMessageCallbacks
import com.nextcloud.talk.ui.chat.ChatMessageContext
import com.nextcloud.talk.ui.chat.ChatMessageView
import com.nextcloud.talk.ui.chat.DateHeader
import com.nextcloud.talk.ui.chat.DateHeaderLabel
import com.nextcloud.talk.ui.chat.LocalShowThreadButton
import com.nextcloud.talk.ui.chat.formatTime
import com.nextcloud.talk.ui.theme.LocalMessageUtils
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_THREAD_ID
import com.vanniktech.emoji.EmojiEditText
import com.vanniktech.emoji.EmojiPopup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val SCHEDULED_THREAD_ID = -1L

private const val STICKY_HEADER_SCROLL_DELAY = 1200L

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
                CompositionLocalProvider(
                    LocalViewThemeUtils provides viewThemeUtils,
                    LocalMessageUtils provides messageUtils,
                    LocalShowThreadButton provides false
                ) {
                    ColoredStatusBar()
                    currentUser?.let { user ->
                        ScheduledMessagesScreen(
                            user = user,
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
                            isThreadView = isThreadView,
                            onCopyScheduledMessage = { message ->
                                copyScheduledMessage(message)
                            }
                        )
                    }
                } // CompositionLocalProvider
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
        user: User,
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
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subTitle = if (isThreadView) threadTitle else conversationName
                            Text(
                                text = stringResource(R.string.nc_in_conversation, subTitle),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                                contentDescription = stringResource(R.string.back_button)
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

                        val visibleMessages = remember(state.messages, isThreadView, threadId) {
                            if (isThreadView) {
                                state.messages.filter { it.threadId == threadId }
                            } else {
                                state.messages
                            }
                        }

                        if (visibleMessages.isEmpty()) {
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

                            val listState = rememberLazyListState()

                            val keyToDate = remember(grouped) {
                                buildMap {
                                    grouped.forEach { (date, messages) ->
                                        put("header_$date", date)
                                        messages.forEach { msg -> put(msg.token ?: "", date) }
                                    }
                                }
                            }

                            val isNearTop by remember(listState) {
                                derivedStateOf { listState.firstVisibleItemIndex <= 2 }
                            }

                            val stickyDateHeaderText by remember(listState, keyToDate) {
                                derivedStateOf {
                                    val firstKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
                                    firstKey?.let { keyToDate[it] }?.let { formatTime(it) } ?: ""
                                }
                            }

                            var stickyDateHeader by remember { mutableStateOf(false) }

                            LaunchedEffect(listState, isNearTop) {
                                if (!isNearTop) {
                                    snapshotFlow { listState.isScrollInProgress }
                                        .collectLatest { scrolling ->
                                            if (scrolling) {
                                                stickyDateHeader = true
                                            } else {
                                                delay(STICKY_HEADER_SCROLL_DELAY)
                                                stickyDateHeader = false
                                            }
                                        }
                                } else {
                                    stickyDateHeader = false
                                }
                            }

                            val stickyDateHeaderAlpha by animateFloatAsState(
                                targetValue = if (stickyDateHeader && stickyDateHeaderText.isNotEmpty()) 1f else 0f,
                                animationSpec = tween(durationMillis = if (stickyDateHeader) 500 else 1000),
                                label = ""
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    reverseLayout = false
                                ) {
                                    val datesInOrder = grouped.keys.sorted()

                                    datesInOrder.forEach { date ->
                                        item(key = "header_$date") {
                                            Box(modifier = Modifier.padding(vertical = 6.dp)) {
                                                DateHeader(date)
                                            }
                                        }

                                        items(
                                            items = grouped[date].orEmpty(),
                                            key = { it.token ?: "" }
                                        ) { message ->

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

                                            val delayedThreadLabel =
                                                stringResource(R.string.nc_scheduled_thread)
                                            val replyToThreadLabel = stringResource(
                                                R.string.nc_reply_to_thread,
                                                message.threadTitle.orEmpty()
                                            )
                                            val uiMessage = remember(
                                                message,
                                                parentMessage,
                                                delayedThreadLabel,
                                                replyToThreadLabel
                                            ) {
                                                val base =
                                                    message.toScheduledMessageUiModel(user, roomToken, parentMessage)
                                                val isThreadReply =
                                                    !isThreadView && (message.threadId ?: 0L) > 0
                                                when {
                                                    SCHEDULED_THREAD_ID == message.threadId -> base.copy(
                                                        isThread = true,
                                                        threadTitle = delayedThreadLabel,
                                                        content = MessageTypeContent.RegularText
                                                    )

                                                    isThreadReply -> base.copy(
                                                        isThread = true,
                                                        threadTitle = replyToThreadLabel,
                                                        threadTitleIconRes = R.drawable.ic_reply
                                                    )

                                                    else -> base
                                                }
                                            }
                                            Box(modifier = Modifier.padding(bottom = 2.dp)) {
                                                ChatMessageView(
                                                    message = uiMessage,
                                                    context = ChatMessageContext(
                                                        hasChatPermission = false,
                                                        conversationThreadId = if (isThreadView) threadId else null
                                                    ),
                                                    callbacks = ChatMessageCallbacks(
                                                        onLongClick = { _ ->
                                                            selectedMessage = message
                                                            showActionsSheet = true
                                                        },
                                                        onQuotedMessageClick = { _ ->
                                                            val msgParentId = message.parentMessageId
                                                            val isThreadMessage = (message.threadId ?: 0L) > 0
                                                            when {
                                                                isThreadMessage && !isThreadView -> Unit
                                                                isThreadView && msgParentId != null ->
                                                                    openThreadParentMessage(
                                                                        msgParentId,
                                                                        message.threadId
                                                                    )

                                                                msgParentId != null -> onOpenParentMessage(msgParentId)
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                DateHeaderLabel(
                                    text = stickyDateHeaderText,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 2.dp)
                                        .alpha(stickyDateHeaderAlpha)
                                )
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
                        imageVector = ImageVector.vectorResource(R.drawable.ic_edit),
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
                            imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_close_24),
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
                                ImageVector.vectorResource(R.drawable.ic_baseline_keyboard_24)
                            } else {
                                ImageVector.vectorResource(R.drawable.ic_insert_emoticon_black_24dp)
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
                            imageVector = ImageVector.vectorResource(R.drawable.ic_check),
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
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.nc_scheduled_time),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scheduledTime,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            ActionRow(
                iconRes = R.drawable.ic_content_copy,
                text = stringResource(R.string.nc_copy_message),
                onClick = onCopy
            )
            ActionRow(
                iconRes = R.drawable.outline_schedule_send_24,
                text = stringResource(R.string.nc_reschedule_message_with_notification),
                onClick = onRescheduleWithNotification
            )
            ActionRow(
                iconRes = R.drawable.outline_schedule_send_24,
                text = stringResource(R.string.nc_reschedule_message_without_notification),
                onClick = onRescheduleWithoutNotification
            )
            ActionRow(
                iconRes = R.drawable.ic_send_24px,
                text = stringResource(R.string.nc_send_now),
                onClick = onSendNow
            )
            if (showOpenThreadAction && !isThreadView) {
                ActionRow(
                    iconRes = R.drawable.outline_forum_24,
                    text = stringResource(R.string.open_thread),
                    onClick = onOpenThread
                )
            }
            ActionRow(
                iconRes = R.drawable.ic_edit,
                text = stringResource(R.string.nc_edit),
                onClick = onEdit
            )
            ActionRow(
                iconRes = R.drawable.trashbin,
                text = stringResource(R.string.nc_delete),
                onClick = onDelete
            )
        }
    }

    private fun openParentMessage(messageId: Long?) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(KEY_ROOM_TOKEN, roomToken)

            messageId?.let { putExtra(BundleKeys.KEY_MESSAGE_ID, it.toString()) }
        }
        startActivity(intent)
    }

    private fun openThreadParentMessage(messageId: Long?, threadId: Long?) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(KEY_ROOM_TOKEN, roomToken)
            threadId?.let { putExtra(BundleKeys.KEY_THREAD_ID, it) }
            messageId?.let { putExtra(BundleKeys.KEY_MESSAGE_ID, it.toString()) }
        }
        startActivity(intent)
    }

    @Composable
    private fun ActionRow(@DrawableRes iconRes: Int, text: String, onClick: () -> Unit) {
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

    @Preview(name = "Light Mode", showBackground = true)
    @Preview(
        name = "Dark Mode",
        uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
    )
    @Preview(name = "RTL / Arabic", locale = "ar")
    @Composable
    private fun PreviewActionRow() {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            ActionRow(iconRes = R.drawable.ic_edit, text = "Edit", onClick = {})
        }
    }

    @Preview(name = "Light Mode")
    @Preview(
        name = "Dark Mode",
        uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
    )
    @Preview(name = "RTL / Arabic", locale = "ar")
    @Composable
    private fun PreviewEditRow() {
        var value by remember { mutableStateOf(TextFieldValue("Hello")) }
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            ScheduledMessageEditRow(
                editValue = value,
                originalText = "Original message",
                onEditValueChange = { value = it },
                onCancel = {},
                onSend = {}
            )
        }
    }

    @Preview()
    @Composable
    private fun OfflineStatusBannerPreview() {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            OfflineStatusBanner(isOnline = false)
        }
    }

    @Preview(name = "Light Mode", showBackground = true)
    @Preview(
        name = "Dark Mode",
        showBackground = true,
        uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
    )
    @Preview(name = "RTL / Arabic", showBackground = true, locale = "ar")
    @Composable
    private fun ShowErrorTextPreview() {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            ShowErrorText(isOnline = true)
        }
    }

    @Preview(name = "Light Mode")
    @Preview(
        name = "Dark Mode",
        uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
    )
    @Preview(name = "RTL / Arabic", locale = "ar")
    @Composable
    private fun ScheduledMessageActionsSheetPreview() {
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            ScheduledMessageActionsSheet(
                scheduledTime = "Thu, 27 Feb 2025 at 18:00",
                onRescheduleWithNotification = {},
                onRescheduleWithoutNotification = {},
                onSendNow = {},
                onEdit = {},
                onDelete = {},
                showOpenThreadAction = false,
                onOpenThread = {},
                onCopy = {}
            )
        }
    }

    companion object {
        const val ROOM_TOKEN = "room_token"
        const val CONVERSATION_NAME = "conversation_name"
        const val THREAD_ID = "thread_id"
        const val THREAD_TITLE = "thread_title"
        const val INT_24: Int = 24
        const val INT_20: Int = 20
    }
}
