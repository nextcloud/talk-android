/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.chat.GetView

// @Suppress("FunctionNaming", "TooManyFunctions", "LongMethod", "StaticFieldLeak", "LargeClass")
// class ComposeChatAdapter(
//     var messages: List<ChatMessage>? = null,
//     var messageId: String? = null,
//     var threadId: String? = null,
//     private val utils: ComposePreviewUtils? = null
// ) {

//     interface PreviewAble {
//         val viewThemeUtils: ViewThemeUtils
//         val messageUtils: MessageUtils
//         val contactsViewModel: ContactsViewModel
//         val chatViewModel: ChatViewModel
//         val context: Context
//         val userManager: UserManager
//     }
//
//     @AutoInjector(NextcloudTalkApplication::class)
//     inner class ComposeChatAdapterViewModel :
//         ViewModel(),
//         PreviewAble {
//
//         @Inject
//         override lateinit var viewThemeUtils: ViewThemeUtils
//
//         @Inject
//         override lateinit var messageUtils: MessageUtils
//
//         @Inject
//         override lateinit var contactsViewModel: ContactsViewModel
//
//         @Inject
//         override lateinit var chatViewModel: ChatViewModel
//
//         @Inject
//         override lateinit var context: Context
//
//         @Inject
//         override lateinit var userManager: UserManager
//
//         init {
//             sharedApplication?.componentApplication?.inject(this)
//         }
//     }
//
//     class ComposeChatAdapterPreviewViewModel(
//         override val viewThemeUtils: ViewThemeUtils,
//         override val messageUtils: MessageUtils,
//         override val contactsViewModel: ContactsViewModel,
//         override val chatViewModel: ChatViewModel,
//         override val context: Context,
//         override val userManager: UserManager
//     ) : ViewModel(),
//         PreviewAble
//
//     companion object {
//         val TAG: String = ComposeChatAdapter::class.java.simpleName
//     }
//
//     val viewModel: PreviewAble =
//         if (utils != null) {
//             ComposeChatAdapterPreviewViewModel(
//                 utils.viewThemeUtils,
//                 utils.messageUtils,
//                 utils.contactsViewModel,
//                 utils.chatViewModel,
//                 utils.context,
//                 utils.userManager
//             )
//         } else {
//             ComposeChatAdapterViewModel()
//         }
//
//     val items = mutableStateListOf<ChatMessage>()
//     val currentUser: User = viewModel.userManager.currentUser.blockingGet()
//     val colorScheme = viewModel.viewThemeUtils.getColorScheme(viewModel.context)
//     val highEmphasisColorInt = if (DisplayUtils.isAppThemeDarkMode(viewModel.context)) {
//         Color.White.toArgb()
//     } else {
//         Color.Black.toArgb()
//     }
//     val highEmphasisColor = Color(highEmphasisColorInt)
//
//     fun addMessages(messages: MutableList<ChatMessage>, append: Boolean) {
//         if (messages.isEmpty()) return
//
//         val processedMessages = messages.toMutableList()
//         if (items.isNotEmpty()) {
//             if (append) {
//                 processedMessages.add(items.first())
//             } else {
//                 processedMessages.add(items.last())
//             }
//         }
//
//         if (append) items.addAll(processedMessages) else items.addAll(0, processedMessages)
//     }
// }

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
@Suppress("MagicNumber", "LongMethod")
fun AllMessageTypesPreview() {
    val sampleMessages = remember {
        listOf(
            // Text Messages
            ChatMessage().apply {
                jsonMessageId = 1
                actorId = "user1"
                message = "I love Nextcloud"
                timestamp = System.currentTimeMillis()
                actorDisplayName = "User1"
                messageType = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE.name
            },
            ChatMessage().apply {
                jsonMessageId = 2
                actorId = "user1_id"
                message = "I love Nextcloud"
                timestamp = System.currentTimeMillis()
                actorDisplayName = "User2"
                messageType = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE.name
            },
            ChatMessage().apply {
                jsonMessageId = 3
                actorId = "user1_id"
                message = "This is a really really really really really really really really really long message"
                timestamp = System.currentTimeMillis()
                actorDisplayName = "User2"
                messageType = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE.name
            },
            ChatMessage().apply {
                jsonMessageId = 4
                actorId = "user1_id"
                message = "some \n linebreak"
                timestamp = System.currentTimeMillis()
                actorDisplayName = "User2"
                messageType = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE.name
            },
            ChatMessage().apply {
                jsonMessageId = 5
                actorId = "user1_id"
                threadTitle = "Thread title"
                isThread = true
                message = "Content of a first thread message"
                timestamp = System.currentTimeMillis()
                actorDisplayName = "User2"
                messageType = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE.name
            },
            ChatMessage().apply {
                jsonMessageId = 6
                actorId = "user1_id"
                threadTitle = "looooooooooooong Thread title"
                isThread = true
                message = "Content"
                timestamp = System.currentTimeMillis()
                actorDisplayName = "User2"
                messageType = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE.name
            }
        )
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            GetView(
                messages = sampleMessages,
                messageIdToBlink = "",
                user = null
            )
        }
    }
}
