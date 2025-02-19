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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.DateUtils
import javax.inject.Inject

@Suppress("FunctionNaming")
@AutoInjector(NextcloudTalkApplication::class)
class ComposeChatAdapter {

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Inject
    lateinit var userManager: UserManager

    private val currentUser: User = userManager.currentUser.blockingGet()
    // private val messages = mutableListOf<ChatMessage>()

    // fun addToAdapter(message: ChatMessage) = messages.add(message)
    // fun addToAdapter(list: List<ChatMessage>) = messages.addAll(list)
    // fun clearAdapter() = messages::clear

    @Composable
    fun GetView(context: Context, messages: List<ChatMessage>) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                when (val type = message.getCalculateMessageType()) {
                    ChatMessage.MessageType.SYSTEM_MESSAGE -> {
                        // TODO
                    }

                    ChatMessage.MessageType.VOICE_MESSAGE -> {
                        // TODO
                    }

                    ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                        // TODO
                    }

                    ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
                        // TODO
                    }

                    ChatMessage.MessageType.POLL_MESSAGE -> {
                        // TODO
                    }

                    ChatMessage.MessageType.DECK_CARD -> {
                        // TODO
                    }

                    ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                        RegularTextMessage(context, message)
                    }

                    else -> {
                        Log.d("Julius", "Unknown message type: $type")
                    }
                }
            }
        }
    }

    @Composable
    private fun RegularTextMessage(context: Context, message: ChatMessage) {
        val incoming = message.actorId != currentUser.userId
        val color = if (incoming) Color.LightGray else Color.Green

        Row(modifier = Modifier.fillMaxWidth(1f)) {

            if (incoming) {
                Icon(
                    Icons.Filled.Person,
                    "",
                    modifier = Modifier
                        .size(24.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Surface(
                modifier = Modifier,
                color = color,
                shape = RoundedCornerShape(8.dp) // TODO create my own shapes from drawable
            ) {
                val timeString = DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
                Row(modifier = Modifier.padding(16.dp)) {
                    Text(message.text)
                    Text("($timeString)")
                }
            }
        }
    }
}
