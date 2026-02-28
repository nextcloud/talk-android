/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contextchat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextcloud.talk.R
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.ui.ComposeChatAdapter
import com.nextcloud.talk.utils.preview.ComposePreviewUtils

@Composable
fun ContextChatView(context: Context, contextViewModel: ContextChatViewModel) {
    val contextChatMessagesState = contextViewModel.getContextChatMessagesState.collectAsState().value

    when (contextChatMessagesState) {
        ContextChatViewModel.ContextChatRetrieveUiState.None -> {}
        is ContextChatViewModel.ContextChatRetrieveUiState.Success -> {
            ContextChatSuccessView(
                visible = true,
                context = context,
                contextChatRetrieveUiStateSuccess = contextChatMessagesState,
                onDismiss = {
                    contextViewModel.clearContextChatState()
                }
            )
        }

        is ContextChatViewModel.ContextChatRetrieveUiState.Error -> {
            ContextChatErrorView()
        }
    }
}

@Composable
fun ContextChatErrorView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            ImageVector.vectorResource(R.drawable.ic_info_24px),
            contentDescription = null
        )

        Text(
            stringResource(R.string.nc_capabilities_failed)
        )
    }
}

@Composable
fun ContextChatSuccessView(
    visible: Boolean,
    context: Context,
    contextChatRetrieveUiStateSuccess: ContextChatViewModel.ContextChatRetrieveUiState.Success,
    onDismiss: () -> Unit
) {
    val previewUtils = ComposePreviewUtils.getInstance(LocalContext.current)
    val colorScheme = previewUtils.viewThemeUtils.getColorScheme(context)

    if (visible) {
        MaterialTheme(colorScheme) {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface {
                    Column(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.Companion.align(Alignment.Companion.Start),
                            verticalAlignment = Alignment.Companion.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.ic_baseline_close_24),
                                    stringResource(R.string.close),
                                    modifier = Modifier.Companion
                                        .size(24.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(contextChatRetrieveUiStateSuccess.title ?: "", fontSize = 18.sp)

                                if (!contextChatRetrieveUiStateSuccess.subTitle.isNullOrEmpty()) {
                                    Text(contextChatRetrieveUiStateSuccess.subTitle, fontSize = 12.sp)
                                }
                            }

                            // This code was written back then but not needed yet, but it's not deleted yet
                            // because it may be used soon when further migrating to Compose...

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

                        val messages = contextChatRetrieveUiStateSuccess.messages.map(ChatMessageJson::asModel)
                        val messageId = contextChatRetrieveUiStateSuccess.messageId
                        val threadId = contextChatRetrieveUiStateSuccess.threadId
                        val isInspection = LocalInspectionMode.current
                        val adapter = ComposeChatAdapter(
                            messagesJson = contextChatRetrieveUiStateSuccess.messages,
                            messageId = messageId,
                            threadId = threadId,
                            utils = if (isInspection) previewUtils else null
                        )
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

@Preview(name = "Light Mode")
@Composable
fun ContextChatSuccessViewPreview(title: String = "Alice") {
    ContextChatSuccessView(
        visible = true,
        context = LocalContext.current,
        contextChatRetrieveUiStateSuccess = ContextChatViewModel.ContextChatRetrieveUiState.Success(
            messageId = "123",
            threadId = null,
            messages = emptyList(),
            title = title,
            subTitle = null
        ),
        onDismiss = {}
    )
}

@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ContextChatSuccessViewDarkPreview() {
    ContextChatSuccessViewPreview()
}

@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
fun ContextChatSuccessViewRtlPreview() {
    ContextChatSuccessViewPreview(title = "أليس")
}

@Preview(name = "Light Mode")
@Composable
fun ContextChatErrorViewPreview() {
    val context = LocalContext.current
    val colorScheme = ComposePreviewUtils.getInstance(context).viewThemeUtils.getColorScheme(context)
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ContextChatErrorView()
        }
    }
}

@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ContextChatErrorViewDarkPreview() {
    ContextChatErrorViewPreview()
}

@Preview(name = "RTL / Arabic", locale = "ar")
@Composable
fun ContextChatErrorViewRtlPreview() {
    ContextChatErrorViewPreview()
}

// This code was written back then but not needed yet, but it's not deleted yet
// because it may be used soon when further migrating to Compose...
@Composable
private fun ComposeChatMenu(backgroundColor: Color, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.Companion.wrapContentSize(Alignment.Companion.TopStart)
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_more_vert_24px),
                contentDescription = stringResource(R.string.nc_common_more_options)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.Companion.background(backgroundColor)
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
