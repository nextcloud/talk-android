/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationcreation.viewmodel.RoomUIState
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.bundle.BundleKeys

/**
 * Reacts to the outcome of creating a conversation: reports what could not be done, hands public
 * conversations to the caller so their link can be shared, and opens every other one right away.
 */
@Composable
fun CreationResultEffect(
    creationState: RoomUIState,
    context: Context,
    onPublicConversation: (token: String, hasPassword: Boolean) -> Unit,
    onHandled: () -> Unit
) {
    LaunchedEffect(creationState) {
        when (val state = creationState) {
            is RoomUIState.Error -> {
                Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                onHandled()
            }

            is RoomUIState.Success -> {
                val conversation = state.conversation
                val roomToken = conversation?.token
                if (roomToken == null) {
                    onHandled()
                } else {
                    if (conversation.invalidParticipants?.isNotEmpty() == true) {
                        Toast.makeText(
                            context,
                            R.string.nc_conversation_created_invalid_participants,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    if (conversation.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL) {
                        onPublicConversation(roomToken, conversation.hasPassword)
                        onHandled()
                    } else {
                        onHandled()
                        openConversation(context, roomToken)
                    }
                }
            }

            else -> Unit
        }
    }
}

@Suppress("LongParameterList")
@Composable
fun ShareCreatedConversation(
    roomToken: String,
    password: String?,
    currentUser: User,
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.nc_conversation_created_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.nc_conversation_created_public))
                if (!password.isNullOrEmpty()) {
                    TextButton(onClick = { copyPassword(context, roomToken, password) }) {
                        Text(text = stringResource(R.string.nc_copy_password))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    (context as? Activity)?.let {
                        ShareUtils.shareConversationLink(
                            it,
                            currentUser.baseUrl,
                            roomToken,
                            CapabilitiesUtil.canGeneratePrettyURL(currentUser)
                        )
                    }
                }
            ) {
                Text(text = stringResource(R.string.nc_share_link))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.nc_open_conversation))
            }
        }
    )
}

fun openConversation(context: Context, roomToken: String) {
    val bundle = Bundle()
    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
    val chatIntent = Intent(context, ChatActivity::class.java)
    chatIntent.putExtras(bundle)
    chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    context.startActivity(chatIntent)
}

private fun copyPassword(context: Context, roomToken: String, password: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(roomToken, password)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.nc_password_copied), Toast.LENGTH_SHORT).show()
}
