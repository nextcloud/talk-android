/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.FileParameters
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load

private const val FILE_PLACEHOLDER_MESSAGE = "{file}"

private val mediaRadiusBig = 8.dp
private val mediaRadiusSmall = 2.dp

@Suppress("Detekt.LongMethod", "LongParameterList")
@Composable
fun MediaMessage(
    typeContent: MessageTypeContent.Media,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    chatViewDownloadingFileState: List<String>,
    onImageClick: (Int) -> Unit
) {
    val fileParameters =
        remember { FileParameters(message.messageParameters as HashMap<String?, HashMap<String?, String?>>?) }

    val captionText = message.message.takeUnless { it == FILE_PLACEHOLDER_MESSAGE }
    val hasCaption = captionText != null
    val mediaInset = 4.dp
    val mediaShape = remember(message.incoming) {
        if (message.incoming) {
            RoundedCornerShape(
                topStart = mediaRadiusSmall,
                topEnd = mediaRadiusBig,
                bottomEnd = mediaRadiusBig,
                bottomStart = mediaRadiusBig
            )
        } else {
            RoundedCornerShape(
                topStart = mediaRadiusBig,
                topEnd = mediaRadiusSmall,
                bottomEnd = mediaRadiusBig,
                bottomStart = mediaRadiusBig
            )
        }
    }

    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        includePadding = false,
        captionText = captionText,
        forceTimeOverlay = !hasCaption,
        content = {
            Column {
                val context = LocalContext.current
                val resourceName = context.resources.getResourceEntryName(typeContent.drawableResourceId)
                val showPlayButton = !typeContent.previewUrl.isNullOrEmpty() &&
                    (resourceName.contains("video") || resourceName.contains("audio"))

                Box(modifier = Modifier.fillMaxWidth()) {
                    val loadedImage = remember(typeContent.previewUrl) {
                        load(
                            imageUri = typeContent.previewUrl,
                            context = context,
                            errorPlaceholderImage = typeContent.drawableResourceId
                        )
                    }

                    AsyncImage(
                        model = loadedImage,
                        contentDescription = stringResource(R.string.media_message_content_description),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(mediaInset)
                            .clip(mediaShape)
                            .clickable { onImageClick(message.id) },
                        contentScale = ContentScale.FillWidth
                    )

                    if (showPlayButton) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
                            contentDescription = stringResource(R.string.media_message_content_play),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp),
                            tint = Color.White
                        )
                    }

                    if (chatViewDownloadingFileState.contains(fileParameters.id)) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    )
}
