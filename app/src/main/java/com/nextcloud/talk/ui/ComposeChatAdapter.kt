/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.DateUtils
import javax.inject.Inject

@Suppress("FunctionNaming")
@AutoInjector(NextcloudTalkApplication::class)
class ComposeChatAdapter {

    var incomingShape: RoundedCornerShape
    var outgoingShape: RoundedCornerShape
    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        incomingShape = RoundedCornerShape(2.dp, 20.dp, 20.dp, 20.dp)
        outgoingShape = RoundedCornerShape(20.dp, 2.dp, 20.dp, 20.dp)
    }

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var contactsViewModel: ContactsViewModel

    private val currentUser: User = userManager.currentUser.blockingGet()

    @Composable
    fun GetView(context: Context, messages: List<ChatMessage>) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // TODO figure out dates
            items(messages) { message ->
                when (val type = message.getCalculateMessageType()) {
                    ChatMessage.MessageType.SYSTEM_MESSAGE -> {
                        SystemMessage(context, message)
                    }

                    // TODO
                    ChatMessage.MessageType.VOICE_MESSAGE -> {
                        Log.d("Julius", "Voice Message: ${message.message}")
                    }

                    ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                        ImageMessage(context, message)
                    }

                    // TODO
                    ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
                        Log.d("Julius", "Geolocation Message: ${message.message}")
                    }

                    // TODO
                    ChatMessage.MessageType.POLL_MESSAGE -> {
                        Log.d("Julius", "Poll Message: ${message.message}")
                    }

                    // TODO
                    ChatMessage.MessageType.DECK_CARD -> {
                        Log.d("Julius", "Deck Card: ${message.message}")
                    }

                    ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                        TextMessage(context, message)
                    }

                    // TODO
                    ChatMessage.MessageType.SINGLE_LINK_MESSAGE -> {
                        Log.d("Julius", "Link Message: ${message.message}")
                    }

                    else -> {
                        Log.d("Julius", "Unknown message type: $type")
                    }
                }
            }
        }
    }

    @Composable
    private fun CommonMessageQuote(context: Context, message: ChatMessage) {
        val colorScheme = viewThemeUtils.getColorScheme(context)
        val color = Color.Red
        Row {
            // FIXME get this dumb shape to work
            Box(modifier = Modifier.padding(0.dp, 0.dp, 8.dp, 0.dp)) {}

            Column {
                Text(message.actorDisplayName!!, fontSize = 8.sp)
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
                EnrichedText(message.text)
            }
        }
    }

    @Composable
    private fun CommonMessageBody(
        context: Context,
        message: ChatMessage,
        content:
        @Composable
        (RowScope.() -> Unit)
    ) {
        val incoming = message.actorId != currentUser.userId
        val colorScheme = viewThemeUtils.getColorScheme(context)
        val color = if (incoming) {
            context.resources.getColor(R.color.bg_message_list_incoming_bubble, null)
        } else {
            colorScheme.surfaceVariant.toArgb()
        }
        val shape = if (incoming) incomingShape else outgoingShape

        Row(modifier = Modifier.fillMaxWidth(1f)) {
            if (incoming) {
                val imageUri = message.actorId?.let { contactsViewModel.getImageUri(it, true) }
                val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
                val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.user_avatar),
                    modifier = Modifier
                        .size(width = 45.dp, height = 45.dp)
                        .align(Alignment.CenterVertically)
                        .padding(8.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            Surface(
                modifier = Modifier.widthIn(40.dp, 140.dp),
                color = Color(color),
                shape = shape
            ) {
                val timeString = DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
                Column(modifier = Modifier.padding(8.dp, 4.dp, 8.dp, 4.dp)) {
                    if (message.parentMessageId != null && !message.isDeleted) {
                        // TODO get parent message in non foolish way For now to test UI, I use the same message
                        CommonMessageQuote(context, message)
                    }

                    Text(message.actorDisplayName!!, fontSize = 8.sp)
                    Row {
                        content()
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(timeString, fontSize = 8.sp, modifier = Modifier.align(Alignment.Bottom))
                    }
                }
            }
        }
    }

    @Composable
    private fun EnrichedText(text: String) {
        // TODO -_- google has no out of box solution for converting spannable to annotated string.
        Text(text, fontSize = 12.sp)
    }

    @Composable
    private fun TextMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            EnrichedText(message.text)
        }
    }

    @Composable
    private fun SystemMessage(context: Context, message: ChatMessage) {
        // TODO crap I forgot this has wrapping -_- dang it. I'll have to handle that later
        val timeString = DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message.text, fontSize = 8.sp, modifier = Modifier.padding(8.dp))
            Text(timeString, fontSize = 6.sp)
        }
    }

    @Composable
    private fun ImageMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            val imageUri = message.imageUrl
            val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
            val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
            AsyncImage(
                model = loadedImage,
                contentDescription = stringResource(R.string.nc_sent_an_image),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(8.dp)
                    .fillMaxHeight()
            )
            Text(message.text, fontSize = 12.sp, modifier = Modifier.widthIn(20.dp, 140.dp))
        }
    }
}
