/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.content.Context
import android.util.Log
import android.view.View.TEXT_ALIGNMENT_VIEW_START
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.message.MessageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

@Suppress("FunctionNaming")
@AutoInjector(NextcloudTalkApplication::class)
class ComposeChatAdapter(
    private var messagesJson: List<ChatMessageJson>? = null,
    private var messageId: String? = null
) {

    private var incomingShape: RoundedCornerShape
    private var outgoingShape: RoundedCornerShape
    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        incomingShape = RoundedCornerShape(2.dp, 20.dp, 20.dp, 20.dp)
        outgoingShape = RoundedCornerShape(20.dp, 2.dp, 20.dp, 20.dp)
    }

    // NOTE: I would like to optimize context and colorScheme by declaring them before hand. In fact, it's best
    //  practice to minimize new instances inside the view holders.
    companion object {
        private val REGULAR_TEXT_SIZE = 16.sp
        private val TIME_TEXT_SIZE = 12.sp
        private val AUTHOR_TEXT_SIZE = 12.sp
        private const val LONG_1000 = 1000
    }

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var contactsViewModel: ContactsViewModel

    private val currentUser: User = userManager.currentUser.blockingGet()

    @Composable
    fun GetView(context: Context, messages: List<ChatMessage>) {
        val listState = rememberLazyListState()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState,
            modifier = Modifier.padding(16.dp)
        ) {
            var lastDate = Date(0)

            items(messages) { message ->
                // FIXME date logic is a bit buggy, need to preprocess the message list
                //  to add Date items to it. Annoying, but needed
                val currentDate = Date(message.timestamp * LONG_1000)
                if (!DateUtils(context).isSameDate(lastDate, currentDate)) {
                    GenericDate(context, message)
                    lastDate = currentDate
                }

                when (val type = message.getCalculateMessageType()) {
                    ChatMessage.MessageType.SYSTEM_MESSAGE -> {
                        SystemMessage(context, message)
                    }

                    ChatMessage.MessageType.VOICE_MESSAGE -> {
                        VoiceMessage(context, message)
                    }

                    ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                        ImageMessage(context, message)
                    }

                    ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
                        GeolocationMessage(context, message)
                    }

                    ChatMessage.MessageType.POLL_MESSAGE -> {
                        PollMessage(context, message)
                    }

                    // TODO
                    ChatMessage.MessageType.DECK_CARD -> {
                        Log.d("Julius", "Deck Card: ${message.message}")
                    }

                    ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                        TextMessage(context, message)
                    }

                    ChatMessage.MessageType.SINGLE_LINK_MESSAGE -> {
                        LinkMessage(context, message)
                    }

                    else -> {
                        Log.d("Julius", "Unknown message type: $type")
                    }
                }

            }

        }

        val state = remember { derivedStateOf { listState.layoutInfo } }
        if (messageId != null && state.value.totalItemsCount > 0) {
            LaunchedEffect(Dispatchers.Main) {
                delay(50)
                val pos = (listState.layoutInfo.totalItemsCount / 2)
                listState.animateScrollToItem(pos)
            }
        }
    }

    @Composable
    private fun GenericDate(context: Context, message: ChatMessage) {
        val dateString = DateUtils(context).getLocalDateTimeStringFromTimestamp(message.timestamp * LONG_1000)
        val colorScheme = viewThemeUtils.getColorScheme(context)
        val color = colorScheme.onBackground
        Row(horizontalArrangement = Arrangement.Absolute.Center, verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            Text(dateString, fontSize = AUTHOR_TEXT_SIZE, modifier = Modifier.padding(8.dp), color = color)
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Composable
    private fun CommonMessageQuote(context: Context, message: ChatMessage) {
        val color = colorResource(R.color.high_emphasis_text)
        Row(
            modifier = Modifier
                .drawWithCache {
                    onDrawWithContent {
                        drawLine(
                            color = color,
                            start = Offset.Zero,
                            end = Offset(0f, this.size.height),
                            strokeWidth = 4f
                        )

                        drawContent()
                    }
                }
                .padding(8.dp)
                .padding(4.dp)
        ) {
            Column {
                Text(message.actorDisplayName!!, fontSize = AUTHOR_TEXT_SIZE)
                val imageUri = message.imageUrl
                if (imageUri != null) {
                    val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
                    val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
                    AsyncImage(
                        model = loadedImage,
                        contentDescription = stringResource(R.string.nc_sent_an_image),
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxHeight()
                    )
                }
                EnrichedText(message)
            }
        }
    }

    @Composable
    private fun CommonMessageBody(
        context: Context,
        message: ChatMessage,
        content:
        @Composable
        (RowScope.() -> Unit)
    ) {
        val incoming = message.actorId != currentUser.userId
        val colorScheme = viewThemeUtils.getColorScheme(context)
        val color = if (incoming) {
            context.resources.getColor(R.color.bg_message_list_incoming_bubble, null)
        } else {
            colorScheme.surfaceVariant.toArgb()
        }
        val shape = if (incoming) incomingShape else outgoingShape

        Row(modifier = Modifier.fillMaxWidth(1f)) {
            if (incoming) {
                val imageUri = message.actorId?.let { contactsViewModel.getImageUri(it, true) }
                val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
                val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.user_avatar),
                    modifier = Modifier
                        .size(width = 45.dp, height = 45.dp)
                        .align(Alignment.CenterVertically)
                        .padding(8.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            Surface(
                modifier = Modifier
                    .defaultMinSize(60.dp, 40.dp)
                    .widthIn(60.dp, 280.dp),
                color = Color(color),
                shape = shape
            ) {
                val timeString = DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
                Column(modifier = Modifier.padding(8.dp, 4.dp, 8.dp, 4.dp)) {
                    if (message.parentMessageId != null && !message.isDeleted) {
                        messagesJson?.let { list ->
                            list.find { it.parentMessage?.id == message.parentMessageId }
                                ?.parentMessage!!.asModel()
                                .let {
                                    CommonMessageQuote(context, it)
                                }
                        }
                    }

                    Text(message.actorDisplayName!!, fontSize = AUTHOR_TEXT_SIZE)
                    Row {
                        content()
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(timeString, fontSize = TIME_TEXT_SIZE, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }

    @Composable
    private fun EnrichedText(message: ChatMessage) {
        AndroidView(factory = { ctx ->
            val incoming = message.actorId != currentUser.userId
            val processedMessageText = messageUtils.enrichChatMessageText(
                ctx,
                message,
                incoming,
                viewThemeUtils
            )

            androidx.emoji2.widget.EmojiTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                setLineSpacing(0F, 1.2f)
                textAlignment = TEXT_ALIGNMENT_VIEW_START
                text = processedMessageText
            }
        }, modifier = Modifier)
    }

    @Composable
    private fun TextMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            EnrichedText(message)
        }
    }

    @Composable
    private fun SystemMessage(context: Context, message: ChatMessage) {
        // TODO crap I forgot this has wrapping -_- dang it. I'll have to handle that later
        val timeString = DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
        Row(horizontalArrangement = Arrangement.Absolute.Center, verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            Text(message.text, fontSize = AUTHOR_TEXT_SIZE, modifier = Modifier.padding(8.dp))
            Text(timeString, fontSize = TIME_TEXT_SIZE)
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Composable
    private fun ImageMessage(context: Context, message: ChatMessage) {
        // FIXME needs a different body when there is no caption
        CommonMessageBody(context, message) {
            Column {
                message.activeUser = currentUser
                val imageUri = message.imageUrl
                val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
                val loadedImage = load(imageUri, context, errorPlaceholderImage)
                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.nc_sent_an_image),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight()
                )
                Text(message.text, fontSize = 12.sp, modifier = Modifier.widthIn(20.dp, 140.dp))
            }
        }
    }

    @Composable
    private fun VoiceMessage(context: Context, message: ChatMessage) {
        val colorScheme = viewThemeUtils.getColorScheme(context)
        CommonMessageBody(context, message) {
            Icon(
                Icons.Filled.PlayArrow,
                "play",
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterVertically)
            )

            AndroidView(
                factory = { ctx ->
                    WaveformSeekBar(ctx).apply {
                        setWaveData(FloatArray(50) { Random.nextFloat() }) // READ ONLY for now
                        setColors(colorScheme.inversePrimary.toArgb(), colorScheme.onPrimaryContainer.toArgb())
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(180.dp)
                    .height(80.dp)
            )
        }
    }

    @Composable
    private fun GeolocationMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            Column {
                if (message.messageParameters != null && message.messageParameters!!.size > 0) {
                    for (key in message.messageParameters!!.keys) {
                        val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                        if (individualHashMap["type"] == "geo-location") {
                            val locationLon = individualHashMap["longitude"]
                            val locationLat = individualHashMap["latitude"]
                            val locationName = individualHashMap["name"]
                            val locationGeoLink = individualHashMap["id"]
                            val urlStringBuffer = StringBuffer("file:///android_asset/leafletMapMessagePreview.html")
                            urlStringBuffer.append(
                                "?mapProviderUrl=" + URLEncoder.encode(context.getString(R.string.osm_tile_server_url))
                            )
                            urlStringBuffer.append(
                                "&mapProviderAttribution=" + URLEncoder.encode(context.getString(R.string.osm_tile_server_attributation))
                            )
                            urlStringBuffer.append("&locationLat=" + URLEncoder.encode(locationLat))
                            urlStringBuffer.append("&locationLon=" + URLEncoder.encode(locationLon))
                            urlStringBuffer.append("&locationName=" + URLEncoder.encode(locationName))
                            urlStringBuffer.append("&locationGeoLink=" + URLEncoder.encode(locationGeoLink))
                            // FIXME the web view
                            // AndroidView(
                            //     factory = { ctx ->
                            //         WebView(ctx).apply {
                            //             loadUrl(urlStringBuffer.toString())
                            //         }
                            //     },
                            //     modifier = Modifier
                            //         .height(80.dp)
                            // )
                        }
                    }
                }
            }
            Text(message.text, fontSize = REGULAR_TEXT_SIZE)
        }
    }

    @Composable
    private fun LinkMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            EnrichedText(message)
            // TODO Add a preview
        }
    }

    @Composable
    private fun PollMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            Column {
                if (message.messageParameters != null && message.messageParameters!!.size > 0) {
                    for (key in message.messageParameters!!.keys) {
                        val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                        if (individualHashMap["type"] == "talk-poll") {
                            val pollId = individualHashMap["id"]
                            val pollName = individualHashMap["name"].toString()
                            Text(pollName, fontSize = AUTHOR_TEXT_SIZE, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                // NOTE: Read Only for now
                            }) {
                                Text(stringResource(R.string.message_poll_tap_to_open), fontSize = REGULAR_TEXT_SIZE)
                            }
                        }
                    }
                }
            }
        }
    }
}
