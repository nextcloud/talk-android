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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.utils.MimetypeUtils
import androidx.core.net.toUri

val LocalUploadProgressProvider = compositionLocalOf<(referenceId: String) -> Int?> { { null } }

private const val FILE_PLACEHOLDER_MESSAGE = "{file}"

private val mediaRadiusBig = 8.dp
private val mediaRadiusSmall = 2.dp

@Suppress("Detekt.LongMethod")
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
    val mediaShape = remember(message.incoming) {
        shape(message.incoming)
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
                val isGif = MimetypeUtils.isGif(typeContent.mimeType)
                val showPlayButton = !typeContent.previewUrl.isNullOrEmpty() &&
                    (
                        resourceName.contains("video") ||
                            resourceName.contains("audio") ||
                            (isGif && !typeContent.animateGif)
                        )

                Box(modifier = Modifier.fillMaxWidth()) {
                    val loadedImage = remember(typeContent.previewUrl) {
                        load(
                            imageUri = typeContent.previewUrl,
                            context = context,
                            errorPlaceholderImage = typeContent.drawableResourceId,
                            animated = typeContent.animateGif
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
                }
            }
        }
    )
}

@Suppress("Detekt.LongMethod")
@Composable
fun UploadingMediaMessage(
    typeContent: MessageTypeContent.UploadingMedia,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onCancelUpload: (referenceId: String) -> Unit = {}
) {
    val getProgress = LocalUploadProgressProvider.current
    val progress = getProgress(message.referenceId.orEmpty())
    val isFailed = message.statusIcon == MessageStatusIcon.FAILED
    val isSent = message.statusIcon == MessageStatusIcon.SENT

    val mediaInset = 4.dp
    val mediaShape = remember(message.incoming) {
        shape(message.incoming)
    }

    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        includePadding = false,
        captionText = typeContent.caption,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val isImage = typeContent.mimeType?.startsWith("image") == true
                    if (isImage && typeContent.localFileUri.isNotEmpty()) {
                        AsyncImage(
                            model = typeContent.localFileUri.toUri(),
                            contentDescription = typeContent.caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .blur(4.dp)
                                .padding(mediaInset)
                                .clip(mediaShape),
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        Icon(
                            painter = painterResource(typeContent.drawableResourceId),
                            contentDescription = typeContent.caption,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(mediaInset)
                                .align(Alignment.Center),
                            tint = Color.Unspecified
                        )
                    }

                    if (isSent) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    } else if (!isFailed) {
                        IconButton(
                            onClick = { onCancelUpload(message.referenceId.orEmpty()) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.nc_cancel),
                                tint = Color.White
                            )
                        }
                    }
                }

                if (isFailed) {
                    Text(
                        text = stringResource(R.string.nc_upload_failed_notification_title),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = androidx.compose.ui.graphics.Color.Red
                    )
                } else if (!isSent) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    )
}

fun shape(incoming: Boolean): RoundedCornerShape =
    if (incoming) {
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

private fun previewUploadingContent(mimeType: String? = "image/jpeg") =
    MessageTypeContent.UploadingMedia(
        localFileUri = "",
        caption = "photo.jpg",
        mimeType = mimeType,
        drawableResourceId = R.drawable.ic_mimetype_image
    )

private fun previewUploadingMessage(statusIcon: MessageStatusIcon = MessageStatusIcon.SENDING) =
    ChatMessageUi(
        id = 0,
        message = "{file}",
        plainMessage = "photo.jpg",
        renderMarkdown = false,
        actorDisplayName = "Jane Doe",
        isThread = false,
        threadTitle = "",
        threadReplies = 0,
        incoming = false,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = statusIcon,
        timestamp = System.currentTimeMillis() / 1000,
        date = java.time.LocalDate.now(),
        content = previewUploadingContent(),
        reactions = emptyList(),
        referenceId = "preview-ref-id"
    )

@Suppress("MagicNumber")
@ChatMessagePreviews
@Composable
private fun UploadingMediaMessageProgressPreview() {
    PreviewContainer {
        CompositionLocalProvider(LocalUploadProgressProvider provides { 42 }) {
            UploadingMediaMessage(
                typeContent = previewUploadingContent(),
                message = previewUploadingMessage()
            )
        }
    }
}

@ChatMessagePreviews
@Composable
private fun UploadingMediaMessageIndeterminatePreview() {
    PreviewContainer {
        UploadingMediaMessage(
            typeContent = previewUploadingContent(),
            message = previewUploadingMessage()
        )
    }
}

@ChatMessagePreviews
@Composable
private fun UploadingMediaMessageFailedPreview() {
    PreviewContainer {
        UploadingMediaMessage(
            typeContent = previewUploadingContent(),
            message = previewUploadingMessage(statusIcon = MessageStatusIcon.FAILED)
        )
    }
}

@ChatMessagePreviews
@Composable
private fun UploadingMediaMessageSentPreview() {
    PreviewContainer {
        UploadingMediaMessage(
            typeContent = previewUploadingContent(),
            message = previewUploadingMessage(statusIcon = MessageStatusIcon.SENT)
        )
    }
}

@ChatMessagePreviews
@Composable
private fun UploadingMediaMessageNonImagePreview() {
    PreviewContainer {
        UploadingMediaMessage(
            typeContent = MessageTypeContent.UploadingMedia(
                localFileUri = "",
                caption = "document.pdf",
                mimeType = "application/pdf",
                drawableResourceId = R.drawable.ic_mimetype_application_pdf
            ),
            message = previewUploadingMessage()
        )
    }
}
