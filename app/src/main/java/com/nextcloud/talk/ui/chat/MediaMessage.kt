/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load

private const val FILE_PLACEHOLDER_MESSAGE = "{file}"

private val MEDIA_RADIUS_BIG = 8.dp
private val MEDIA_RADIUS_SMALL = 2.dp

@Composable
fun MediaMessage(
    typeContent: MessageTypeContent.Media,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onImageClick: (Int) -> Unit
) {
    val captionText = message.message.takeUnless { it == FILE_PLACEHOLDER_MESSAGE }
    val hasCaption = captionText != null
    val mediaInset = 4.dp
    val mediaShape = if (message.incoming) {
        RoundedCornerShape(
            topStart = MEDIA_RADIUS_SMALL,
            topEnd = MEDIA_RADIUS_BIG,
            bottomEnd = MEDIA_RADIUS_BIG,
            bottomStart = MEDIA_RADIUS_BIG
        )
    } else {
        RoundedCornerShape(
            topStart = MEDIA_RADIUS_BIG,
            topEnd = MEDIA_RADIUS_SMALL,
            bottomEnd = MEDIA_RADIUS_BIG,
            bottomStart = MEDIA_RADIUS_BIG
        )
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
                    val loadedImage = load(
                        imageUri = typeContent.previewUrl,
                        context = context,
                        errorPlaceholderImage = typeContent.drawableResourceId
                    )

                    AsyncImage(
                        model = loadedImage,
                        contentDescription = "image preview",
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
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    )
}
