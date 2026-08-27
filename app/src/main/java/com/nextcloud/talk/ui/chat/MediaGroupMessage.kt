/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.nextcloud.talk.R
import com.nextcloud.talk.attachmentpreview.describeFile
import com.nextcloud.talk.chat.data.model.FileParameters
import com.nextcloud.talk.chat.data.model.decodeBlurhashPlaceholder
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.ui.theme.mimetypeIconTint
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.MimetypeUtils
import com.nextcloud.talk.utils.VideoThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FILE_PLACEHOLDER_MESSAGE = "{file}"
private const val MAX_VISIBLE_GRID_TILES = 4
private const val THREE_TILE_LAYOUT = 3
private const val SQUARE_GRID_COLUMNS = 2
private const val SQUARE_GRID_ROWS = 2
private const val PLAY_BUTTON_ALPHA = 0.45f
private const val OVERFLOW_SCRIM_ALPHA = 0.45f
private const val OVERFLOW_TEXT_SIZE = 20
private const val UPLOAD_SCRIM_ALPHA = 0.25f
private const val UPLOAD_TRACK_ALPHA = 0.3f
private const val PERCENT = 100
private const val TILE_CROSSFADE_DURATION_MS = 300

// Matches MediaMessage's own mediaInset - so the grid, as a whole, nests inside the bubble's corner
// with the same inset gap a single image has, rather than sitting flush against the bubble edge.
private val mediaGroupInset = 4.dp

private val gridSpacing = 2.dp
private val gridTileShape = RoundedCornerShape(4.dp)
private val singleTileHeight = 220.dp
private val rowTileHeight = 160.dp
private val threeTileRowHeight = 200.dp
private val squareGridHeight = 220.dp
private val genericIconSize = 40.dp
private val gridPlayButtonSize = 36.dp
private val gridPlayIconSize = 20.dp

/**
 * Renders a run of file-share messages uploaded together in one batch (see
 * ChatViewModel.MediaGroupItem) as a single WhatsApp-style "album" bubble: image/video attachments
 * as an adaptive thumbnail grid, any other file types as a stacked list of rows below it. The
 * bubble itself is anchored on the group's last (newest) message, same as a single MediaMessage -
 * its timestamp, reactions, read status, reply target and long-press/swipe-reply all target that
 * representative message, same as web bases its combined message on the group's last real message.
 */
@Suppress("Detekt.LongMethod")
@Composable
fun MediaGroupMessage(
    messages: List<ChatMessageUi>,
    context: ChatMessageContext = ChatMessageContext(),
    callbacks: ChatMessageCallbacks = ChatMessageCallbacks()
) {
    val representative = messages.last()
    val mediaItems = messages.filter { it.isPreviewableMedia() }
    val fileItems = messages.filterNot { it.isPreviewableMedia() }

    val hasExplicitCaption = representative.plainMessage != FILE_PLACEHOLDER_MESSAGE
    val captionText = if (hasExplicitCaption) representative.message else null

    // Same corner-shape function MediaMessage uses for a single image, so the grid's own outer
    // corner nests inside the bubble's corner the same way a single image's does, instead of a
    // fixed, position-unaware radius.
    val groupShape = remember(representative.incoming, representative.isGrouped, representative.isGroupedWithNext) {
        shape(representative.incoming, representative.isGrouped, representative.isGroupedWithNext)
    }

    CompositionLocalProvider(
        LocalMessageLongClickHandler provides { id -> callbacks.onLongClick?.invoke(id) ?: Unit },
        LocalReactionClickHandler provides callbacks.onReactionClick,
        LocalReactionLongClickHandler provides callbacks.onReactionLongClick,
        LocalOpenThreadHandler provides callbacks.onOpenThreadClick,
        LocalQuotedMessageClickHandler provides callbacks.onQuotedMessageClick,
        LocalAvatarClickHandler provides callbacks.onAvatarClick
    ) {
        SwipeToReplyContainer(
            replyable = representative.replyable && context.hasChatPermission,
            onSwipeReply = { callbacks.onSwipeReply?.invoke(representative.id) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { callbacks.onLongClick?.invoke(representative.id) },
                        onDoubleClick = { callbacks.onLongClick?.invoke(representative.id) },
                        onLongClick = { callbacks.onLongClick?.invoke(representative.id) }
                    )
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    MessageScaffold(
                        uiMessage = representative,
                        isOneToOneConversation = context.isOneToOneConversation,
                        conversationThreadId = context.conversationThreadId,
                        includePadding = mediaItems.isEmpty(),
                        captionText = captionText,
                        forceTimeOverlay = captionText == null && fileItems.isEmpty(),
                        content = {
                            Column {
                                if (mediaItems.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .padding(mediaGroupInset)
                                            .clip(groupShape)
                                    ) {
                                        MediaGrid(
                                            items = mediaItems,
                                            chatViewDownloadingFileState = context.downloadingFileState,
                                            onItemClick = callbacks.onFileClick,
                                            onCancelUpload = callbacks.onCancelUpload
                                        )
                                    }
                                }
                                if (fileItems.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = if (mediaItems.isNotEmpty()) 4.dp else 0.dp
                                        )
                                    ) {
                                        fileItems.forEach { message ->
                                            GroupedFileRow(
                                                message = message,
                                                onClick = { callbacks.onFileClick(message.id) },
                                                onCancelUpload = callbacks.onCancelUpload
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun ChatMessageUi.isPreviewableMedia(): Boolean {
    val mimeType = when (val currentContent = content) {
        is MessageTypeContent.Media -> currentContent.mimeType
        is MessageTypeContent.UploadingMedia -> currentContent.mimeType.orEmpty()
        else -> ""
    }
    return mimeType.startsWith(Mimetype.IMAGE_PREFIX) || mimeType.startsWith(Mimetype.VIDEO_PREFIX)
}

@Suppress("Detekt.LongMethod", "LongParameterList")
@Composable
private fun MediaGrid(
    items: List<ChatMessageUi>,
    chatViewDownloadingFileState: List<String>,
    onItemClick: (Int) -> Unit,
    onCancelUpload: (String) -> Unit
) {
    val visible = items.take(MAX_VISIBLE_GRID_TILES)
    val overflowCount = items.size - visible.size

    when (visible.size) {
        1 -> {
            val message = visible[0]
            MediaGridTile(
                message = message,
                chatViewDownloadingFileState = chatViewDownloadingFileState,
                onCancelUpload = onCancelUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(singleTileHeight)
                    .clip(gridTileShape),
                onClick = { onItemClick(message.id) }
            )
        }

        2 -> {
            Row(
                modifier = Modifier.fillMaxWidth().height(rowTileHeight),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing)
            ) {
                visible.forEach { message ->
                    MediaGridTile(
                        message = message,
                        chatViewDownloadingFileState = chatViewDownloadingFileState,
                        onCancelUpload = onCancelUpload,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(gridTileShape),
                        onClick = { onItemClick(message.id) }
                    )
                }
            }
        }

        THREE_TILE_LAYOUT -> {
            Row(
                modifier = Modifier.fillMaxWidth().height(threeTileRowHeight),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing)
            ) {
                val big = visible[0]
                MediaGridTile(
                    message = big,
                    chatViewDownloadingFileState = chatViewDownloadingFileState,
                    onCancelUpload = onCancelUpload,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clip(gridTileShape),
                    onClick = { onItemClick(big.id) }
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    visible.drop(1).forEach { message ->
                        MediaGridTile(
                            message = message,
                            chatViewDownloadingFileState = chatViewDownloadingFileState,
                            onCancelUpload = onCancelUpload,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(gridTileShape),
                            onClick = { onItemClick(message.id) }
                        )
                    }
                }
            }
        }

        else -> {
            Column(
                modifier = Modifier.fillMaxWidth().height(squareGridHeight),
                verticalArrangement = Arrangement.spacedBy(gridSpacing)
            ) {
                for (rowIndex in 0 until SQUARE_GRID_ROWS) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                    ) {
                        for (columnIndex in 0 until SQUARE_GRID_COLUMNS) {
                            val index = rowIndex * SQUARE_GRID_COLUMNS + columnIndex
                            val message = visible[index]
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                MediaGridTile(
                                    message = message,
                                    chatViewDownloadingFileState = chatViewDownloadingFileState,
                                    onCancelUpload = onCancelUpload,
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight().clip(gridTileShape),
                                    onClick = { onItemClick(message.id) }
                                )
                                if (index == MAX_VISIBLE_GRID_TILES - 1 && overflowCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .clip(gridTileShape)
                                            .background(Color.Black.copy(alpha = OVERFLOW_SCRIM_ALPHA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$overflowCount",
                                            color = Color.White,
                                            fontSize = OVERFLOW_TEXT_SIZE.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dispatches a grid slot to the right renderer for the message's current state: already synced
// (Media) or still mid-upload (UploadingMedia) - a batch groups into one bubble immediately as it
// starts uploading rather than only once every file in it has synced, so both must render here.
@Composable
private fun MediaGridTile(
    message: ChatMessageUi,
    chatViewDownloadingFileState: List<String>,
    onCancelUpload: (String) -> Unit,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val messageLongClickHandler = LocalMessageLongClickHandler.current
    val clickableModifier = modifier.combinedClickable(
        onClick = onClick,
        onLongClick = { messageLongClickHandler(message.id) }
    )

    Box(modifier = clickableModifier, contentAlignment = Alignment.Center) {
        when (val typeContent = message.content) {
            is MessageTypeContent.Media ->
                SyncedMediaTileContent(message, typeContent, chatViewDownloadingFileState)
            is MessageTypeContent.UploadingMedia ->
                UploadingMediaTileContent(message, typeContent, onCancelUpload)
            else -> Unit
        }
    }
}

@Suppress("Detekt.LongMethod", "CyclomaticComplexMethod")
@Composable
private fun SyncedMediaTileContent(
    message: ChatMessageUi,
    typeContent: MessageTypeContent.Media,
    chatViewDownloadingFileState: List<String>
) {
    val context = LocalContext.current
    val fileParameters = remember(message.id) {
        FileParameters(HashMap(message.messageParameters.mapValues { (_, params) -> HashMap(params) }))
    }
    val isVideo = typeContent.mimeType.startsWith(Mimetype.VIDEO_PREFIX)
    val isGif = MimetypeUtils.isGif(typeContent.mimeType)
    val showPlayButton = isVideo || (isGif && !typeContent.animateGif)
    val hasServerPreview = !typeContent.previewUrl.isNullOrEmpty()

    // Bridges the gap right after this message synced but before the server has a preview link
    // ready for it (or we just haven't fetched it yet) - same as the single-message MediaMessage,
    // reusing the local file we already have on disk instead of falling back to the bare mimetype
    // icon, which would otherwise flash for a moment on every upload.
    val getLocalPreviewUri = LocalUploadedLocalPreviewProvider.current
    val localPreviewUri = if (typeContent.mimeType.startsWith(Mimetype.IMAGE_PREFIX) || isVideo) {
        message.referenceId?.let(getLocalPreviewUri)
    } else {
        null
    }
    val localPreviewPainter = if (!isVideo && !localPreviewUri.isNullOrEmpty()) {
        rememberAsyncImagePainter(model = localPreviewUri.toUri())
    } else {
        null
    }
    val localVideoFramePainter = if (isVideo && !hasServerPreview) {
        val refId = message.referenceId
        val videoFrame by produceState<Bitmap?>(initialValue = null, key1 = refId, key2 = localPreviewUri) {
            value = withContext(Dispatchers.IO) {
                refId?.let { VideoThumbnailCache.get(context, it) }
                    ?: localPreviewUri?.let { uri ->
                        describeFile(context, uri, compress = false).videoThumbnail?.also { bitmap ->
                            refId?.let { VideoThumbnailCache.put(context, it, bitmap) }
                        }
                    }
            }
        }
        videoFrame?.let { BitmapPainter(it.asImageBitmap()) }
    } else {
        null
    }

    val blurhashPainter = remember(typeContent.blurhash, typeContent.width, typeContent.height) {
        decodeBlurhashPlaceholder(typeContent.blurhash, typeContent.width, typeContent.height)
            ?.asImageBitmap()
            ?.let { BitmapPainter(it) }
    }
    val fallbackPainter = painterResource(typeContent.drawableResourceId)
    val basePainter = localVideoFramePainter ?: localPreviewPainter ?: blurhashPainter
    val showsGenericIcon = !hasServerPreview && basePainter == null

    val loadedImage = remember(typeContent.previewUrl, typeContent.isClassified) {
        if (typeContent.isClassified || typeContent.previewUrl.isNullOrEmpty()) {
            null
        } else {
            load(
                imageUri = typeContent.previewUrl,
                context = context,
                errorPlaceholderImage = typeContent.drawableResourceId,
                animated = typeContent.animateGif
            )
        }
    }

    if (showsGenericIcon) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Icon(
            painter = fallbackPainter,
            contentDescription = stringResource(R.string.media_message_content_description),
            modifier = Modifier.size(genericIconSize),
            tint = mimetypeIconTint(typeContent.drawableResourceId)
        )
    } else {
        if (basePainter != null) {
            Image(
                painter = basePainter,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
        }
        if (hasServerPreview) {
            // rememberAsyncImagePainter() paints the request's own placeholder (the generic
            // mimetype icon, set by load() below) while the real image is still loading - shown
            // unconditionally, that placeholder would sit fully opaque on top of basePainter above.
            // Only fading it in once actually loaded keeps the base layer visible until then.
            val loadedPainter = rememberAsyncImagePainter(model = loadedImage)
            val isLoaded = loadedPainter.state is AsyncImagePainter.State.Success
            val loadedAlpha by animateFloatAsState(
                targetValue = if (isLoaded) 1f else 0f,
                animationSpec = tween(durationMillis = TILE_CROSSFADE_DURATION_MS),
                label = "gridTileLoadedAlpha"
            )
            Image(
                painter = loadedPainter,
                contentDescription = stringResource(R.string.media_message_content_description),
                modifier = Modifier.fillMaxWidth().fillMaxHeight().alpha(loadedAlpha),
                contentScale = ContentScale.Crop
            )
        }
    }

    if (showPlayButton) {
        Box(
            modifier = Modifier
                .size(gridPlayButtonSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = PLAY_BUTTON_ALPHA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
                contentDescription = stringResource(R.string.media_message_content_play),
                modifier = Modifier.size(gridPlayIconSize),
                tint = Color.White
            )
        }
    }

    if (chatViewDownloadingFileState.contains(fileParameters.id)) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Suppress("Detekt.LongMethod")
@Composable
private fun UploadingMediaTileContent(
    message: ChatMessageUi,
    typeContent: MessageTypeContent.UploadingMedia,
    onCancelUpload: (String) -> Unit
) {
    val progress = LocalUploadProgressProvider.current(message.referenceId.orEmpty())
    val isSent = message.statusIcon == MessageStatusIcon.SENT
    val mimeType = typeContent.mimeType.orEmpty()
    val hasLocalPreview = (mimeType.startsWith(Mimetype.IMAGE_PREFIX) || mimeType.startsWith(Mimetype.VIDEO_PREFIX)) &&
        typeContent.localFileUri.isNotEmpty()

    if (hasLocalPreview) {
        Image(
            painter = rememberAsyncImagePainter(model = typeContent.localFileUri.toUri()),
            contentDescription = typeContent.fileName,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Icon(
            painter = painterResource(typeContent.drawableResourceId),
            contentDescription = typeContent.fileName,
            modifier = Modifier.size(genericIconSize),
            tint = mimetypeIconTint(typeContent.drawableResourceId)
        )
    }

    if (!isSent) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = UPLOAD_SCRIM_ALPHA))
        )
        Box(modifier = Modifier.size(gridPlayButtonSize), contentAlignment = Alignment.Center) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress / PERCENT.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = UPLOAD_TRACK_ALPHA),
                    strokeWidth = 2.dp
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = UPLOAD_TRACK_ALPHA),
                    strokeWidth = 2.dp
                )
            }
            IconButton(
                onClick = { onCancelUpload(message.referenceId.orEmpty()) },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.nc_cancel),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun GroupedFileRow(message: ChatMessageUi, onClick: () -> Unit, onCancelUpload: (String) -> Unit) {
    when (val content = message.content) {
        is MessageTypeContent.Media -> SyncedFileRow(message, onClick)
        is MessageTypeContent.UploadingMedia -> UploadingFileRow(message, content, onCancelUpload)
        else -> Unit
    }
}

@Composable
private fun SyncedFileRow(message: ChatMessageUi, onClick: () -> Unit) {
    val fileParameters = remember(message.id) {
        FileParameters(HashMap(message.messageParameters.mapValues { (_, params) -> HashMap(params) }))
    }
    val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(fileParameters.mimetype)
    val messageLongClickHandler = LocalMessageLongClickHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { messageLongClickHandler(message.id) }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(drawableResourceId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = mimetypeIconTint(drawableResourceId)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = fileParameters.name.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun UploadingFileRow(
    message: ChatMessageUi,
    typeContent: MessageTypeContent.UploadingMedia,
    onCancelUpload: (String) -> Unit
) {
    val messageLongClickHandler = LocalMessageLongClickHandler.current
    val isSent = message.statusIcon == MessageStatusIcon.SENT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { messageLongClickHandler(message.id) }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(typeContent.drawableResourceId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = mimetypeIconTint(typeContent.drawableResourceId)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = typeContent.fileName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (!isSent) {
            IconButton(onClick = { onCancelUpload(message.referenceId.orEmpty()) }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.nc_cancel))
            }
        }
    }
}
