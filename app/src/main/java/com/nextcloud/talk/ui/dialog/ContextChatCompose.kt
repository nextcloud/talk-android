/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.ui.ComposeChatAdapter
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@Suppress("FunctionNaming", "LongMethod", "StaticFieldLeak")
class ContextChatCompose(val bundle: Bundle) {

    companion object {
        const val LIMIT = 50
        const val HALF_ALPHA = 0.5f
    }

    @AutoInjector(NextcloudTalkApplication::class)
    inner class ContextChatComposeViewModel : ViewModel() {
        @Inject
        lateinit var viewThemeUtils: ViewThemeUtils

        @Inject
        lateinit var chatViewModel: ChatViewModel

        @Inject
        lateinit var userManager: UserManager

        init {
            NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
            val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)!!
            val baseUrl = bundle.getString(BundleKeys.KEY_BASE_URL)!!
            val token = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
            val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!

            chatViewModel.getContextForChatMessages(credentials, baseUrl, token, messageId, LIMIT)
        }
    }

    private fun Context.requireActivity(): Activity {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        throw IllegalStateException("No activity was present but it is required.")
    }

    @Composable
    fun GetDialogView(
        shouldDismiss: MutableState<Boolean>,
        context: Context,
        contextViewModel: ContextChatComposeViewModel = ContextChatComposeViewModel()
    ) {
        if (shouldDismiss.value) {
            context.requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return
        }

        context.requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        val colorScheme = contextViewModel.viewThemeUtils.getColorScheme(context)
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
                Surface {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(top = 16.dp)
                    ) {
                        val user = contextViewModel.userManager.currentUser.blockingGet()
                        val shouldShow = !user.hasSpreedFeatureCapability("chat-get-context") ||
                            !user.hasSpreedFeatureCapability("federation-v1")
                        Row(
                            modifier = Modifier.align(Alignment.Start),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                shouldDismiss.value = true
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    stringResource(R.string.close),
                                    modifier = Modifier
                                        .size(24.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.Center) {
                                val name = bundle.getString(BundleKeys.KEY_CONVERSATION_NAME)!!
                                Text(name, fontSize = 24.sp)
                            }
                            // Spacer(modifier = Modifier.weight(1f))
                            // val cInt = context.resources.getColor(R.color.high_emphasis_text, null)
                            // Icon(
                            //     painterResource(R.drawable.ic_call_black_24dp),
                            //     "",
                            //     tint = Color(cInt),
                            //     modifier = Modifier
                            //         .padding()
                            //         .padding(end = 16.dp)
                            //         .alpha(HALF_ALPHA)
                            // )
                            //
                            // Icon(
                            //     painterResource(R.drawable.ic_baseline_videocam_24),
                            //     "",
                            //     tint = Color(cInt),
                            //     modifier = Modifier
                            //         .padding()
                            //         .alpha(HALF_ALPHA)
                            // )
                            //
                            // ComposeChatMenu(colorScheme.background, false)
                        }
                        if (shouldShow) {
                            Icon(
                                Icons.Filled.Info,
                                "Info Icon",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Text(
                                stringResource(R.string.nc_capabilities_failed),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            val contextState = contextViewModel
                                .chatViewModel
                                .getContextChatMessages
                                .asFlow()
                                .collectAsState(listOf())
                            val messagesJson = contextState.value
                            val messages = messagesJson.map(ChatMessageJson::asModel)
                            val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!
                            val adapter = ComposeChatAdapter(messagesJson, messageId)
                            SideEffect {
                                adapter.addMessages(messages.toMutableList(), true)
                            }
                            adapter.GetView()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ComposeChatMenu(backgroundColor: Color, enabled: Boolean = true) {
        var expanded by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.wrapContentSize(Alignment.TopStart)
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(backgroundColor)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nc_search)) },
                    onClick = {
                        expanded = false
                    },
                    enabled = enabled
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nc_conversation_menu_conversation_info)) },
                    onClick = {
                        expanded = false
                    },
                    enabled = enabled
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nc_shared_items)) },
                    onClick = {
                        expanded = false
                    },
                    enabled = enabled
                )
            }
        }
    }
}
