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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.painterResource
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

@Suppress("FunctionNaming", "TooManyFunctions")
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
            val processedMessages = messages.addDates(context)

            items(processedMessages) { message ->
                message.activeUser = currentUser // -_-
                when (val type = message.getCalculateMessageType()) {
                    ChatMessage.MessageType.SYSTEM_MESSAGE -> {
                        if (!message.isReaction()) {
                            SystemMessage(context, message)
                        }
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

                    ChatMessage.MessageType.DECK_CARD -> {
                        DeckMessage(context, message)
                    }

                    ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                        if (message.isDate) {
                            GenericDate(context, message)
                        } else if (message.isLinkPreview()) {
                            LinkMessage(context, message)
                        } else {
                            TextMessage(context, message)
                        }
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

    private fun ChatMessage.isReaction(): Boolean =
        systemMessageType == ChatMessage.SystemMessageType.REACTION ||
            systemMessageType == ChatMessage.SystemMessageType.REACTION_DELETED ||
            systemMessageType == ChatMessage.SystemMessageType.REACTION_REVOKED

    private fun List<ChatMessage>.addDates(context: Context): List<ChatMessage> {
        val newList = mutableListOf<ChatMessage>()
        var lastDate = Date(0)
        for (message in this) {
            val currentDate = Date(message.timestamp * LONG_1000)
            if (!DateUtils(context).isSameDate(lastDate, currentDate)) {
                newList.add(
                    ChatMessage().apply {
                        isDate = true
                        timestamp = message.timestamp
                    }
                )
                lastDate = currentDate
            }
            newList.add(message)
        }

        return newList
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
                            start = Offset(0f, this.size.height / 6),
                            end = Offset(0f, this.size.height - (this.size.height / 6)),
                            strokeWidth = 4f
                        )

                        drawContent()
                    }
                }
                .padding(8.dp)
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
                        .size(width = 48.dp, height = 48.dp)
                        .align(Alignment.CenterVertically)
                        .padding(8.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            Surface(
                modifier = Modifier
                    .defaultMinSize(60.dp, 40.dp)
                    .widthIn(60.dp, 280.dp)
                    .heightIn(40.dp, 450.dp),
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

                    Text(message.actorDisplayName.toString(), fontSize = AUTHOR_TEXT_SIZE)
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
            var processedMessageText = messageUtils.enrichChatMessageText(
                ctx,
                message,
                incoming,
                viewThemeUtils
            )

            // TODO: - this link is read only now, but should be refactored to be clickable
            // processedMessageText = messageUtils.processMessageParameters(
            //     ctx, viewThemeUtils, processedMessageText!!, message, null
            // )

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
        // Note: I would like a custom image body, but this works for now
        CommonMessageBody(context, message) {
            Column {
                message.activeUser = currentUser
                val imageUri = message.imageUrl
                val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
                val loadedImage = load(imageUri, context, errorPlaceholderImage)
                val hasCaption = (message.message != "{file}")
                val height = if (hasCaption) .8f else 1f

                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.nc_sent_an_image),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight(height)
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
                            val lat = individualHashMap["latitude"]
                            val lng = individualHashMap["longitude"]

                            if (lat != null && lng != null) {
                                val latitude = lat.toDouble()
                                val longitude = lng.toDouble()
                                OpenStreetMap(latitude, longitude)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun OpenStreetMap(latitude: Double, longitude: Double) {
        AndroidView(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            factory = { context ->
                Configuration.getInstance().userAgentValue = context.packageName
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)

                    val geoPoint = GeoPoint(latitude, longitude)
                    controller.setCenter(geoPoint)
                    controller.setZoom(15.0)

                    val marker = Marker(this)
                    marker.position = geoPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Location"
                    overlays.add(marker)

                    invalidate()
                }
            },
            update = { mapView ->
                val geoPoint = GeoPoint(latitude, longitude)
                mapView.controller.setCenter(geoPoint)

                val marker = mapView.overlays.find { it is Marker } as? Marker
                marker?.position = geoPoint
                mapView.invalidate()
            }
        )
    }

    @Composable
    private fun LinkMessage(context: Context, message: ChatMessage) {
        val color = colorResource(R.color.high_emphasis_text)

        CommonMessageBody(context, message) {
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
                    // TODO get this working with open graph
                    EnrichedText(message)
                }
            }

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
                            // val pollId = individualHashMap["id"]
                            val pollName = individualHashMap["name"].toString()
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Icon(painterResource(R.drawable.ic_baseline_bar_chart_24), "")
                                Text(pollName, fontSize = AUTHOR_TEXT_SIZE, fontWeight = FontWeight.Bold)
                            }
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

    @Composable
    private fun DeckMessage(context: Context, message: ChatMessage) {
        CommonMessageBody(context, message) {
            Column {
                if (message.messageParameters != null && message.messageParameters!!.size > 0) {
                    for (key in message.messageParameters!!.keys) {
                        val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                        if (individualHashMap["type"] == "deck-card") {
                            val cardName = individualHashMap["name"]
                            val stackName = individualHashMap["stackname"]
                            val boardName = individualHashMap["boardname"]
                            // val cardLink = individualHashMap["link"]

                            if (cardName?.isNotEmpty() == true) {
                                val cardDescription = String.format(
                                    context.resources.getString(R.string.deck_card_description),
                                    stackName,
                                    boardName
                                )
                                Row(modifier = Modifier.padding(start = 8.dp)) {
                                    Icon(painterResource(R.drawable.deck), "")
                                    Text(cardName, fontSize = AUTHOR_TEXT_SIZE, fontWeight = FontWeight.Bold)
                                }
                                Text(cardDescription, fontSize = AUTHOR_TEXT_SIZE)
                            }
                        }
                    }
                }
            }
        }
    }
}
