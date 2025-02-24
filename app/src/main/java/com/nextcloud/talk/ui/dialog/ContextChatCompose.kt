/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.asFlow
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.ui.ComposeChatAdapter
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContextChatCompose(val bundle: Bundle) {

    companion object {
        private const val LIMIT = 12
    }

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var viewModel: ChatViewModel

    @Inject
    lateinit var userManager: UserManager

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)!!
        val baseUrl = bundle.getString(BundleKeys.KEY_BASE_URL)!!
        val token = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
        val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!

        viewModel.getContextForChatMessages(credentials, baseUrl, token, messageId, LIMIT)
    }

    @Composable
    fun GetDialogView(shouldDismiss: MutableState<Boolean>, context: Context) {
        if (shouldDismiss.value) {
            return
        }

        val colorScheme = viewThemeUtils.getColorScheme(context)
        MaterialTheme(colorScheme) {
            Dialog(
                onDismissRequest = {
                    shouldDismiss.value = true
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        val user = userManager.currentUser.blockingGet()
                        if (!user.hasSpreedFeatureCapability("chat-get-context") ||
                            !user.hasSpreedFeatureCapability("federation-v1")
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                "Info Icon",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Text(
                                "Capabilities not found",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Row(modifier = Modifier.align(Alignment.Start)) {
                                IconButton(onClick = {
                                    shouldDismiss.value = true
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        "Exit",
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .size(48.dp)
                                    )
                                }
                                Column {
                                    // TODO get these from token from bundle
                                    Text("Conversation 1", fontSize = 24.sp)
                                    Text("This is description", fontSize = 12.sp)
                                }
                            }
                            val contextState = viewModel.getContextChatMessages.asFlow().collectAsState(listOf())
                            val messagesJson = contextState.value
                            val messages = messagesJson.map(ChatMessageJson::asModel)
                            ComposeChatAdapter(messagesJson).GetView(context, messages)
                        }
                    }
                }
            }
        }
    }
}
