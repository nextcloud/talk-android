/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.network.HttpException
import com.nextcloud.talk.R
import com.nextcloud.talk.attachmentpreview.FileDescription
import com.nextcloud.talk.attachmentpreview.describeFile
import com.nextcloud.talk.chat.data.model.FileParameters
import com.nextcloud.talk.chat.data.model.decodeBlurhashPlaceholder
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.MimetypeUtils
import com.nextcloud.talk.utils.VideoThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

val LocalUploadProgressProvider = compositionLocalOf<(referenceId: String) -> Int?> { { null } }

// Local device URI of a just-finished-uploading message, keyed by referenceId. Used to bridge the gap
// between the upload placeholder disappearing and the server-side preview finishing its first load, so
// we show the image we already have on disk instead of a generic mimetype icon.
val LocalUploadedLocalPreviewProvider = compositionLocalOf<(referenceId: String) -> String?> { { null } }

private const val FILE_PLACEHOLDER_MESSAGE = "{file}"
private const val PREVIEW_MAX_RETRIES = 3
private const val PREVIEW_RETRY_DELAY_MS = 2_000L
private const val MEDIA_CROSSFADE_DURATION_MS = 300
private const val TAG = "MediaMessage"

// bubbleRadiusBig (ChatMessageScaffold.kt) minus mediaInset below, so the media's own rounded
// corner nests concentrically inside the bubble's corner instead of a visibly mismatched arc.
private val mediaRadiusBig = 6.dp

// bubbleRadiusSmall (2.dp) minus mediaInset would go negative, so this can't nest concentrically
// like mediaRadiusBig does - a small fixed rounding instead of a hard 0 corner still looks better
// than a perfectly sharp edge sitting inside the bubble's own (slightly rounded) grouped corner.
private val mediaRadiusSmall = 2.dp

private val uploadSpinnerSize = 56.dp
private val uploadSpinnerStrokeWidth = 3.dp
private const val UPLOAD_SCRIM_ALPHA = 0.25f
private const val UPLOAD_SPINNER_TRACK_ALPHA = 0.3f

// Used to size the uploading-video placeholder before its real aspect ratio is known (or if it can't
// be read at all), so the bubble doesn't collapse to icon-size. 16:9 is the most common video shape.
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private const val VIDEO_PLACEHOLDER_BACKGROUND_ALPHA = 0.4f

private val playButtonCircleSize = 56.dp
private val playButtonIconSize = 32.dp
private const val PLAY_BUTTON_CIRCLE_ALPHA = 0.45f

// Portrait media (taller than wide) filling the full bubble width reads as oversized in the chat -
// shrink just that case, since landscape media is already reasonably sized at full width.
private const val PORTRAIT_WIDTH_FRACTION = 0.80f

private fun mediaWidthFraction(aspectRatio: Float?): Float =
    if (aspectRatio != null && aspectRatio < 1f) PORTRAIT_WIDTH_FRACTION else 1f

private val noPreviewMinSize = 160.dp

@Suppress("Detekt.LongMethod", "LongParameterList", "CyclomaticComplexMethod")
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
        remember {
            FileParameters(
                HashMap(
                    message.messageParameters.mapValues { (_, params) -> HashMap(params) }
                )
            )
        }

    val context = LocalContext.current
    val isVideo = typeContent.mimeType.startsWith(Mimetype.VIDEO_PREFIX)
    val hasServerPreview = !typeContent.previewUrl.isNullOrEmpty()

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

    // The server didn't generate a preview for this video (unsupported codec, previews disabled, ...)
    // - fall back to its first frame instead of a plain icon. Prefer the durable on-disk cache
    // (survives leaving/reopening the chat or an app restart, unlike the in-memory localPreviewUri
    // bridge, which only lives for the current upload); if it's not cached yet, re-extract from the
    // local file while we still have it and cache it for next time. Coil has no built-in video frame
    // decoding, so this reads the frame directly via MediaMetadataRetriever, same as the
    // uploading-placeholder state.
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

    // A video shown via its local first frame counts as "has a preview" too, so the filename caption
    // stays suppressed just like it would once the server's own preview becomes available.
    val hasPreview = hasServerPreview || localVideoFramePainter != null
    val hasExplicitCaption = message.plainMessage != FILE_PLACEHOLDER_MESSAGE
    val captionText = when {
        hasExplicitCaption -> message.message
        !hasPreview -> message.message
        else -> null
    }
    val hasCaption = captionText != null
    val mediaInset = 4.dp
    val mediaShape = remember(message.incoming, message.isGrouped, message.isGroupedWithNext) {
        shape(message.incoming, message.isGrouped, message.isGroupedWithNext)
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
                val scope = rememberCoroutineScope()
                val isGif = MimetypeUtils.isGif(typeContent.mimeType)
                // Every video gets a play button overlay, regardless of whether its preview came from
                // the server or our own local-first-frame fallback (or neither, yet).
                val showPlayButton = isVideo ||
                    (
                        !typeContent.previewUrl.isNullOrEmpty() &&
                            (
                                typeContent.mimeType.startsWith(Mimetype.AUDIO_PREFIX) ||
                                    (isGif && !typeContent.animateGif)
                                )
                        )

                var retryCount by remember(typeContent.previewUrl) { mutableIntStateOf(0) }
                var retryPending by remember(typeContent.previewUrl) { mutableStateOf(false) }
                val retryAwarePreviewUrl = remember(typeContent.previewUrl, retryCount) {
                    typeContent.previewUrl?.let { previewUrl ->
                        if (retryCount == 0) {
                            previewUrl
                        } else {
                            val delimiter = if (previewUrl.contains("?")) "&" else "?"
                            "$previewUrl${delimiter}retryAttempt=$retryCount"
                        }
                    }
                }

                val blurhashPainter = remember(typeContent.blurhash, typeContent.width, typeContent.height) {
                    decodeBlurhashPlaceholder(typeContent.blurhash, typeContent.width, typeContent.height)
                        ?.asImageBitmap()
                        ?.let { BitmapPainter(it) }
                }
                val serverAspectRatio = remember(typeContent.width, typeContent.height) {
                    val w = typeContent.width
                    val h = typeContent.height
                    if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h else null
                }
                val loadedImage = remember(retryAwarePreviewUrl, typeContent.isClassified) {
                    if (typeContent.isClassified || retryAwarePreviewUrl == null) {
                        // Passing an ImageRequest built with null data (rather than a null model) here
                        // would make Coil resolve its own null-data handling instead of ever showing the
                        // fallback painter passed to AsyncImage below.
                        null
                    } else {
                        load(
                            imageUri = retryAwarePreviewUrl,
                            context = context,
                            errorPlaceholderImage = typeContent.drawableResourceId,
                            animated = typeContent.animateGif
                        )
                    }
                }
                val fallbackPainter = painterResource(typeContent.drawableResourceId)

                // Prefer the local file over blurhash when both are available: blurhash is a rough
                // color-blob approximation meant for messages from other users where we don't have
                // the actual bytes - for our own just-uploaded file we already have the real image on
                // disk, so showing blurhash instead would be a needless downgrade.
                val ownUploadPlaceholder = localPreviewPainter ?: blurhashPainter ?: fallbackPainter

                // Created unconditionally (even for the local-video-frame branch below, where
                // loadedImage is always null and this just sits in Coil's cheap Empty state) so its
                // intrinsic size is available as a fallback aspect ratio - see loadedAspectRatio below.
                val loadedPainter = rememberAsyncImagePainter(
                    model = loadedImage,
                    onError = { state ->
                        val cause = state.result.throwable
                        val isServerError = cause is HttpException && cause.response.code in 500..599
                        if (
                            isServerError &&
                            !typeContent.previewUrl.isNullOrEmpty() &&
                            retryCount < PREVIEW_MAX_RETRIES &&
                            !retryPending
                        ) {
                            retryPending = true
                            scope.launch {
                                Log.d(
                                    TAG,
                                    "Preview returned HTTP ${(cause as HttpException).response.code}, " +
                                        "scheduling retry ${retryCount + 1}/$PREVIEW_MAX_RETRIES " +
                                        "for ${typeContent.previewUrl}"
                                )
                                delay(PREVIEW_RETRY_DELAY_MS)
                                retryCount++
                                retryPending = false
                            }
                        }
                    }
                )
                val isLoaded = loadedPainter.state is AsyncImagePainter.State.Success
                val loadedAlpha by animateFloatAsState(
                    targetValue = if (isLoaded) 1f else 0f,
                    animationSpec = tween(durationMillis = MEDIA_CROSSFADE_DURATION_MS),
                    label = "mediaLoadedAlpha"
                )

                // The server doesn't always report width/height (observed for some file types, e.g.
                // screenshots) - without either value, the two matchParentSize() crossfade layers below
                // have nothing to size the bubble by and it collapses to zero height, hiding the image
                // even though it loaded successfully. Falling back to the loaded image's own intrinsic
                // size once available fixes that case without affecting the normal (server-reported)
                // path, which always takes priority when present.
                val loadedIntrinsicSize = loadedPainter.intrinsicSize
                val loadedAspectRatio = if (
                    loadedIntrinsicSize.isSpecified &&
                    loadedIntrinsicSize.width > 0f &&
                    loadedIntrinsicSize.height > 0f
                ) {
                    loadedIntrinsicSize.width / loadedIntrinsicSize.height
                } else {
                    null
                }
                val aspectRatio = serverAspectRatio ?: loadedAspectRatio

                val showsGenericIcon = localVideoFramePainter == null && aspectRatio == null

                val mediaModifier = Modifier
                    .fillMaxWidth()
                    .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
                    .padding(mediaInset)
                    .clip(mediaShape)

                Box(
                    modifier = Modifier
                        .fillMaxWidth(mediaWidthFraction(aspectRatio))
                        .then(
                            if (showsGenericIcon) {
                                Modifier.defaultMinSize(minWidth = noPreviewMinSize, minHeight = noPreviewMinSize)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val messageLongClickHandler = LocalMessageLongClickHandler.current
                    val clickableModifier = mediaModifier.combinedClickable(
                        onClick = { onImageClick(message.id) },
                        onLongClick = { messageLongClickHandler(message.id) }
                    )

                    if (showsGenericIcon) {
                        Icon(
                            painter = fallbackPainter,
                            contentDescription = stringResource(R.string.media_message_content_description),
                            modifier = Modifier
                                .size(120.dp)
                                .padding(mediaInset)
                                .align(Alignment.Center)
                                .combinedClickable(
                                    onClick = { onImageClick(message.id) },
                                    onLongClick = { messageLongClickHandler(message.id) }
                                ),
                            tint = Color.Unspecified
                        )
                    } else if (localVideoFramePainter != null) {
                        Image(
                            painter = localVideoFramePainter,
                            contentDescription = stringResource(R.string.media_message_content_description),
                            modifier = clickableModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Own explicit crossfade instead of relying on Coil's built-in one: the
                        // placeholder is a Compose-supplied Painter (not a Coil-managed Drawable), so
                        // Coil's crossfade transition doesn't reliably fade from what's actually on
                        // screen - it can jump straight to the loaded image, reading as a flash rather
                        // than a fade. Keeping the placeholder as a permanent base layer and fading the
                        // loaded image in on top guarantees a smooth, controllable transition instead.
                        Box(modifier = clickableModifier) {
                            Image(
                                painter = ownUploadPlaceholder,
                                contentDescription = stringResource(R.string.media_message_content_description),
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                            Image(
                                painter = loadedPainter,
                                contentDescription = stringResource(R.string.media_message_content_description),
                                modifier = Modifier
                                    .matchParentSize()
                                    .alpha(loadedAlpha),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (showPlayButton) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(playButtonCircleSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = PLAY_BUTTON_CIRCLE_ALPHA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
                                contentDescription = stringResource(R.string.media_message_content_play),
                                modifier = Modifier.size(playButtonIconSize),
                                tint = Color.White
                            )
                        }
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

@Suppress("Detekt.LongMethod")
@Composable
fun UploadingMediaMessage(
    typeContent: MessageTypeContent.UploadingMedia,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onCancelUpload: (referenceId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val getProgress = LocalUploadProgressProvider.current
    val progress = getProgress(message.referenceId.orEmpty())
    val isFailed = message.statusIcon == MessageStatusIcon.FAILED
    val isSent = message.statusIcon == MessageStatusIcon.SENT
    val hasCaption = typeContent.caption != null
    val isImage = typeContent.mimeType?.startsWith(Mimetype.IMAGE_PREFIX) == true
    val isVideo = typeContent.mimeType?.startsWith(Mimetype.VIDEO_PREFIX) == true

    val mediaInset = 4.dp
    val mediaShape = remember(message.incoming, message.isGrouped, message.isGroupedWithNext) {
        shape(message.incoming, message.isGrouped, message.isGroupedWithNext)
    }

    // Read locally so the placeholder is already sized the same way the final MediaMessage will be
    // (portrait shrunk to mediaWidthFraction) - otherwise the bubble would visibly resize once the
    // real message replaces this placeholder.
    val imageAspectRatio by produceState<Float?>(initialValue = null, key1 = typeContent.localFileUri) {
        value = if (isImage) {
            withContext(Dispatchers.IO) {
                describeFile(context, typeContent.localFileUri, compress = false).aspectRatio
            }
        } else {
            null
        }
    }

    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        includePadding = false,
        captionText = typeContent.caption,
        forceTimeOverlay = !hasCaption,
        content = {
            // Not fillMaxWidth(): for image/video, whichever content renders below decides the
            // width itself (shrinking for portrait), and this wraps to match - forcing full width
            // here would leave empty space beside a narrower image instead of shrinking the bubble.
            Column {
                Box(
                    modifier = if (isImage || isVideo) Modifier else Modifier.fillMaxWidth()
                ) {
                    if (isImage && typeContent.localFileUri.isNotEmpty()) {
                        val ratio = imageAspectRatio
                        AsyncImage(
                            model = typeContent.localFileUri.toUri(),
                            contentDescription = typeContent.fileName,
                            modifier = Modifier
                                .fillMaxWidth(mediaWidthFraction(ratio))
                                .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
                                .padding(mediaInset)
                                .clip(mediaShape)
                                .blur(4.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else if (isVideo && typeContent.localFileUri.isNotEmpty()) {
                        UploadingVideoPreview(
                            typeContent = typeContent,
                            referenceId = message.referenceId,
                            mediaInset = mediaInset,
                            mediaShape = mediaShape
                        )
                    } else {
                        Icon(
                            painter = painterResource(typeContent.drawableResourceId),
                            contentDescription = typeContent.fileName,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(mediaInset)
                                .align(Alignment.Center),
                            tint = Color.Unspecified
                        )
                    }

                    if (!isSent && !isFailed) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = UPLOAD_SCRIM_ALPHA))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(uploadSpinnerSize)
                        ) {
                            if (progress != null) {
                                CircularProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = UPLOAD_SPINNER_TRACK_ALPHA),
                                    strokeWidth = uploadSpinnerStrokeWidth
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = UPLOAD_SPINNER_TRACK_ALPHA),
                                    strokeWidth = uploadSpinnerStrokeWidth
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

                if (isFailed) {
                    Text(
                        text = stringResource(R.string.nc_upload_failed_notification_title),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = androidx.compose.ui.graphics.Color.Red
                    )
                }
            }
        }
    )
}

/**
 * Sized to the video's real aspect ratio (read locally from the file being uploaded, so it matches
 * what the final sent message will look like) with its first frame as a blurred thumbnail. Falls
 * back to a fixed 16:9 box with the generic file icon while that's being read, or if it can't be
 * read at all.
 */
@Composable
private fun UploadingVideoPreview(
    typeContent: MessageTypeContent.UploadingMedia,
    referenceId: String?,
    mediaInset: Dp,
    mediaShape: RoundedCornerShape
) {
    val context = LocalContext.current
    val videoDescription by produceState<FileDescription?>(
        initialValue = null,
        key1 = typeContent.localFileUri
    ) {
        value = withContext(Dispatchers.IO) {
            describeFile(context, typeContent.localFileUri, compress = false).also { description ->
                description.videoThumbnail?.let { bitmap ->
                    referenceId?.let { VideoThumbnailCache.put(context, it, bitmap) }
                }
            }
        }
    }
    val aspectRatio = videoDescription?.aspectRatio ?: DEFAULT_VIDEO_ASPECT_RATIO
    val thumbnail = videoDescription?.videoThumbnail
    val widthFraction = mediaWidthFraction(aspectRatio)

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail.asImageBitmap(),
            contentDescription = typeContent.fileName,
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .aspectRatio(aspectRatio)
                .padding(mediaInset)
                .clip(mediaShape)
                .blur(4.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .aspectRatio(aspectRatio)
                .padding(mediaInset)
                .clip(mediaShape)
                .background(Color.Black.copy(alpha = VIDEO_PLACEHOLDER_BACKGROUND_ALPHA))
        ) {
            Icon(
                painter = painterResource(typeContent.drawableResourceId),
                contentDescription = typeContent.fileName,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center),
                tint = Color.Unspecified
            )
        }
    }
}

// Mirrors ChatMessageScaffold's own bubble-shape logic (groupedSideTop/groupedSideBottom) exactly,
// so the media's clip always nests inside whichever corner radius the bubble actually rendered for
// this specific message's grouping state - a fixed shape here would only ever match one of the two
// possible bubble shapes and visibly mismatch on the other.
fun shape(incoming: Boolean, isGrouped: Boolean, isGroupedWithNext: Boolean): RoundedCornerShape {
    val groupedSideTop = if (isGrouped) mediaRadiusSmall else mediaRadiusBig
    val groupedSideBottom = if (isGroupedWithNext) mediaRadiusSmall else mediaRadiusBig
    return if (incoming) {
        RoundedCornerShape(
            topStart = groupedSideTop,
            topEnd = mediaRadiusBig,
            bottomEnd = mediaRadiusBig,
            bottomStart = groupedSideBottom
        )
    } else {
        RoundedCornerShape(
            topStart = mediaRadiusBig,
            topEnd = groupedSideTop,
            bottomEnd = groupedSideBottom,
            bottomStart = mediaRadiusBig
        )
    }
}

private fun previewUploadingContent(mimeType: String? = "image/jpeg") =
    MessageTypeContent.UploadingMedia(
        localFileUri = "",
        fileName = "photo.jpg",
        caption = null,
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
                fileName = "document.pdf",
                caption = null,
                mimeType = "application/pdf",
                drawableResourceId = R.drawable.ic_mimetype_application_pdf
            ),
            message = previewUploadingMessage()
        )
    }
}
