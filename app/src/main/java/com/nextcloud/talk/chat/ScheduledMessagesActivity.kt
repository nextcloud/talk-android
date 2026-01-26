/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ScheduleSend
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.chat.viewmodels.ScheduledMessagesViewModel
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import java.time.Instant
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("LongMethod", "LargeClass")
class ScheduledMessagesActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var dateUtils: DateUtils

    private lateinit var scheduledMessagesViewModel: ScheduledMessagesViewModel

    private lateinit var messageInputViewModel: MessageInputViewModel

    private val roomToken: String by lazy {
        intent.getStringExtra(ROOM_TOKEN).orEmpty()
    }

    private val conversationName: String by lazy {
        intent.getStringExtra(CONVERSATION_NAME).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        scheduledMessagesViewModel = ViewModelProvider(this, viewModelFactory)[ScheduledMessagesViewModel::class.java]

        messageInputViewModel = ViewModelProvider(this, viewModelFactory)[MessageInputViewModel::class.java]

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val user = currentUserProviderOld.currentUser.blockingGet()
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                ScheduledMessagesScreen(
                    conversationName = conversationName,
                    scheduledMessagesViewModel = scheduledMessagesViewModel,
                    dateUtils = dateUtils,
                    viewThemeUtils = viewThemeUtils,
                    onBack = { finish() },
                    onLoadScheduledMessages = { loadScheduledMessages() },
                    onSendNow = { message ->
                        sendNow(message, user)
                    },
                    onReschedule = { message, sendAt ->
                        reschedule(message, sendAt, user)
                    },
                    onEdit = { message, sendAt ->
                        edit(message, sendAt, user)
                    },
                    onDeleteScheduledMessage = { message -> deleteScheduledMessage(message, user) }
                )
            }
        }
    }

    private fun loadScheduledMessages() {
        val user = currentUserProviderOld.currentUser.blockingGet()
        scheduledMessagesViewModel.loadScheduledMessages(
            user.getCredentials(),
            ApiUtils.getUrlForScheduledMessages(user.baseUrl, roomToken)
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

    private fun reschedule(message: ChatMessage, sendAt: Int, user: User) {
        scheduledMessagesViewModel.reschedule(
            credentials = user.getCredentials(),
            url = ApiUtils.getUrlForScheduledMessage(
                user.baseUrl,
                roomToken,
                message.token
            ),
            message = message.message.orEmpty(),
            sendAt = sendAt,
            replyTo = message.parentMessageId?.toInt(),
            sendWithoutNotification = message.silent,
            threadTitle = message.threadTitle,
            threadId = message.threadId
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
            replyTo = message.parentMessageId?.toInt(),
            sendWithoutNotification = message.silent,
            threadTitle = message.threadTitle,
            threadId = message.threadId
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
        onReschedule: (ChatMessage, Int) -> Unit,
        onEdit: (ChatMessage, Int) -> Unit,
        onDeleteScheduledMessage: (ChatMessage) -> Unit
    ) {
        val snackBarHostState = remember { SnackbarHostState() }
        val scheduledState by scheduledMessagesViewModel.getScheduledMessagesState.collectAsStateWithLifecycle()
        val sendNowState by scheduledMessagesViewModel.sendNowState.collectAsStateWithLifecycle()
        val rescheduleState by scheduledMessagesViewModel.rescheduleState.collectAsStateWithLifecycle()
        val editState by scheduledMessagesViewModel.editState.collectAsStateWithLifecycle()
        val deleteState by scheduledMessagesViewModel.deleteState.collectAsStateWithLifecycle()
        val genericErrorText = stringResource(R.string.nc_common_error_sorry)

        var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
        var showActionsSheet by remember { mutableStateOf(false) }
        var showScheduleDialogFor by remember { mutableStateOf<ChatMessage?>(null) }

        var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
        var originalEditText by remember { mutableStateOf("") }
        var editValue by remember { mutableStateOf(TextFieldValue("")) }

        LaunchedEffect(Unit) { onLoadScheduledMessages() }

        LaunchedEffect(sendNowState) {
            when (val state = sendNowState) {
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
                            Text(
                                text = stringResource(
                                    R.string.nc_in_conversation,
                                    conversationName
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
            snackbarHost = { SnackbarHost(snackBarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues)
                    .background(colorResource(R.color.bg_bottom_sheet))
            ) {
                when (val state = scheduledState) {
                    is ScheduledMessagesViewModel.GetScheduledMessagesSuccessState -> {
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

                            val sortedMessages = remember(state.messages) {
                                state.messages
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
                                    stickyHeader(key = "header_$date") {
                                        ScheduledDayHeader(
                                            text = buildHeaderText(today = today, date = date)
                                        )
                                    }

                                    items(
                                        items = grouped[date].orEmpty(),
                                        key = { it.token?: "" }
                                    ) { message ->
                                        ScheduledMessageBubble(
                                            message = message,
                                            dateUtils = dateUtils,
                                            viewThemeUtils = viewThemeUtils,
                                            onLongPress = {
                                                selectedMessage = message
                                                showActionsSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is ScheduledMessagesViewModel.GetScheduledMessagesErrorState -> {
                        Text(
                            text = genericErrorText,
                            modifier = Modifier
                                .padding(24.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }

                    else -> Spacer(modifier = Modifier.weight(1f))
                }

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
                    onReschedule = {
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
                        val parsed = ChatUtils.getParsedMessage(message.message, message.messageParameters).orEmpty()
                        editingMessage = message
                        originalEditText = parsed
                        editValue = TextFieldValue(parsed)
                        showActionsSheet = false
                    },
                    onDelete = {
                        val message = selectedMessage ?: return@ScheduledMessageActionsSheet
                        onDeleteScheduledMessage(message)
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
                onSchedule = { scheduledTime, _ ->
                    onReschedule(message, scheduledTime.toInt())
                    shouldDismiss.value = true
                    showScheduleDialogFor = null
                },
                defaultSendWithoutNotification = message.silent
            ).GetScheduleDialog(shouldDismiss, LocalContext.current)
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
    private fun ScheduledMessageBubble(
        message: ChatMessage,
        dateUtils: DateUtils,
        viewThemeUtils: com.nextcloud.talk.ui.theme.ViewThemeUtils,
        onLongPress: () -> Unit
    ) {
        val context = LocalContext.current
        val scheduledAt = message.sendAt?.toLong() ?: message.timestamp
        val timeText = dateUtils.getLocalTimeStringFromTimestamp(scheduledAt)
        val text = ChatUtils.getParsedMessage(message.message, message.messageParameters).orEmpty()

        val bubbleColor = remember(context, message.isDeleted, viewThemeUtils) {
            Color(viewThemeUtils.talk.getOutgoingMessageBubbleColor(context, message.isDeleted, false))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = bubbleColor,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
                modifier = Modifier.width(IntrinsicSize.Max)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (message.silent) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(
                                    12.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("LongMethod")
    @Composable
    private fun ScheduledMessageEditRow(
        editValue: TextFieldValue,
        originalText: String,
        onEditValueChange: (TextFieldValue) -> Unit,
        onCancel: () -> Unit,
        onSend: () -> Unit
    ) {
        var showEmojiPicker by remember { mutableStateOf(false) }
        val canSend = editValue.text.isNotBlank() && editValue.text.trim() != originalText.trim()

        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

        LaunchedEffect(showEmojiPicker) {
            if (showEmojiPicker) {
                keyboardController?.hide()
                focusManager.clearFocus()
            } else {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }

        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nc_edit_message_text),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        showEmojiPicker = false
                        onCancel()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_close_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = originalText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            showEmojiPicker = !showEmojiPicker
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_insert_emoticon_black_24dp),
                            contentDescription = null
                        )
                    }

                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { onEditValueChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.nc_hint_enter_a_message)) }
                    )

                    IconButton(
                        onClick = { if (canSend) onSend() },
                        enabled = canSend
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_24),
                            contentDescription = null,
                            tint = if (canSend) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }

                if (showEmojiPicker) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    val latestEditValue by rememberUpdatedState(editValue)
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        factory = { context ->
                            EmojiPickerView(context).apply {
                                setOnEmojiPickedListener { picked ->
                                    val editedEmoji = latestEditValue.text + picked.emoji
                                    onEditValueChange(
                                        TextFieldValue(editedEmoji, selection = TextRange(editedEmoji.length))
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ScheduledMessageActionsSheet(
        scheduledTime: String,
        onReschedule: () -> Unit,
        onSendNow: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ActionRow(icon = Icons.AutoMirrored.Outlined.ScheduleSend, text = scheduledTime, onClick = {})
            ActionRow(
                icon = Icons.Outlined.AccessTime,
                text = stringResource(R.string.nc_reschedule_message),
                onClick = onReschedule
            )
            ActionRow(
                icon = Icons.AutoMirrored.Outlined.Send,
                text = stringResource(R.string.nc_send_now),
                onClick = onSendNow
            )
            ActionRow(icon = Icons.Outlined.Edit, text = stringResource(R.string.nc_edit), onClick = onEdit)
            ActionRow(icon = Icons.Outlined.Delete, text = stringResource(R.string.nc_delete), onClick = onDelete)
        }
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
        const val INT_2: Int = 2
        const val INT_6: Int = 6
        const val INT_0: Int = 0
        const val INT_1: Int = 1
    }
}
