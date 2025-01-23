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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.asFlow
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.opengraph.Reference
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

@Suppress("FunctionNaming", "TooManyFunctions", "LongMethod")
@AutoInjector(NextcloudTalkApplication::class)
class ComposeChatAdapter(
    private var messagesJson: List<ChatMessageJson>? = null,
    private var messageId: String? = null
) {

    private var incomingShape: RoundedCornerShape = RoundedCornerShape(2.dp, 20.dp, 20.dp, 20.dp)
    private var outgoingShape: RoundedCornerShape = RoundedCornerShape(20.dp, 2.dp, 20.dp, 20.dp)
    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    companion object {
        private val REGULAR_TEXT_SIZE = 16.sp
        private val TIME_TEXT_SIZE = 12.sp
        private val AUTHOR_TEXT_SIZE = 12.sp
        private const val LONG_1000 = 1000
        private const val SCROLL_DELAY = 50L
        private const val QUOTE_SHAPE_OFFSET = 6
        private const val LINE_SPACING = 1.2f
        private const val CAPTION_WEIGHT = 0.8f
        private const val DEFAULT_WAVE_SIZE = 50
        private const val MAP_ZOOM = 15.0
    }

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var contactsViewModel: ContactsViewModel

    @Inject
    lateinit var chatViewModel: ChatViewModel

    @Inject
    lateinit var context: Context

    private val currentUser: User = userManager.currentUser.blockingGet()
    private val items = mutableStateListOf<ChatMessage>()
    private val colorScheme = viewThemeUtils.getColorScheme(context)
    private val highEmphasisColorInt = context.resources.getColor(R.color.high_emphasis_text, null)

    fun addMessages(messages: MutableList<ChatMessage>, append: Boolean) {
        if (messages.isEmpty()) return

        val processedMessages = messages.toMutableList()
        if (items.isNotEmpty()) {
            if (append) {
                processedMessages.add(items.first())
            } else {
                processedMessages.add(items.last())
            }
        }

        processedMessages.addDates()
        if (append) items.addAll(processedMessages) else items.addAll(0, processedMessages)
    }

    @Composable
    fun GetView() {
        val listState = rememberLazyListState()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState,
            modifier = Modifier.padding(16.dp)
        ) {
            items(items) { message ->
                message.activeUser = currentUser // -_-
                when (val type = message.getCalculateMessageType()) {
                    ChatMessage.MessageType.SYSTEM_MESSAGE -> {
                        if (!message.isReaction()) {
                            SystemMessage(message)
                        }
                    }

                    ChatMessage.MessageType.VOICE_MESSAGE -> {
                        VoiceMessage(message)
                    }

                    ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                        ImageMessage(message)
                    }

                    ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
                        GeolocationMessage(message)
                    }

                    ChatMessage.MessageType.POLL_MESSAGE -> {
                        PollMessage(message)
                    }

                    ChatMessage.MessageType.DECK_CARD -> {
                        DeckMessage(message)
                    }

                    ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                        if (message.isDate) {
                            GenericDate(message)
                        } else if (message.isLinkPreview()) {
                            LinkMessage(message)
                        } else {
                            TextMessage(message)
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
                delay(SCROLL_DELAY)
                val pos = (listState.layoutInfo.totalItemsCount / 2)
                listState.animateScrollToItem(pos)
            }
        }
    }

    private fun ChatMessage.isReaction(): Boolean =
        systemMessageType == ChatMessage.SystemMessageType.REACTION ||
            systemMessageType == ChatMessage.SystemMessageType.REACTION_DELETED ||
            systemMessageType == ChatMessage.SystemMessageType.REACTION_REVOKED

    private fun List<ChatMessage>.addDates(): List<ChatMessage> {
        val newList = mutableListOf<ChatMessage>()
        var lastDate = Date(this.first().timestamp * LONG_1000)
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
    private fun GenericDate(message: ChatMessage) {
        val dateString = DateUtils(context).getLocalDateTimeStringFromTimestamp(message.timestamp * LONG_1000)
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
                            start = Offset(0f, this.size.height / QUOTE_SHAPE_OFFSET),
                            end = Offset(0f, this.size.height - (this.size.height / QUOTE_SHAPE_OFFSET)),
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
        message: ChatMessage,
        includePadding: Boolean = true,
        content:
        @Composable
        (RowScope.() -> Unit)
    ) {
        val incoming = message.actorId != currentUser.userId
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
                val modifier = if (includePadding) Modifier.padding(8.dp, 4.dp, 8.dp, 4.dp) else Modifier
                Column(modifier = modifier) {
                    if (message.parentMessageId != null && !message.isDeleted && messagesJson != null) {
                        messagesJson!!
                            .find { it.parentMessage?.id == message.parentMessageId }
                            ?.parentMessage!!.asModel().let { CommonMessageQuote(context, it) }
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

            // Note: - this link is read only now, but should be refactored to be clickable
            // processedMessageText = messageUtils.processMessageParameters(
            //     ctx, viewThemeUtils, processedMessageText!!, message, null
            // )

            androidx.emoji2.widget.EmojiTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                setLineSpacing(0F, LINE_SPACING)
                textAlignment = TEXT_ALIGNMENT_VIEW_START
                text = processedMessageText
            }
        }, modifier = Modifier)
    }

    @Composable
    private fun TextMessage(message: ChatMessage) {
        CommonMessageBody(message) {
            EnrichedText(message)
        }
    }

    @Composable
    private fun SystemMessage(message: ChatMessage) {
        val similarMessages = sharedApplication!!.resources.getQuantityString(
            R.plurals.see_similar_system_messages,
            message.expandableChildrenAmount,
            message.expandableChildrenAmount
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val timeString = DateUtils(context).getLocalTimeStringFromTimestamp(message.timestamp)
            Row(horizontalArrangement = Arrangement.Absolute.Center, verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                Text(message.text, fontSize = AUTHOR_TEXT_SIZE, modifier = Modifier.padding(8.dp))
                Text(timeString, fontSize = TIME_TEXT_SIZE)
                Spacer(modifier = Modifier.weight(1f))
            }

            if (message.expandableChildrenAmount > 0) {
                TextButtonNoStyling(similarMessages) {
                    // NOTE: Read only for now
                }
            }
        }
    }

    @Composable
    private fun TextButtonNoStyling(text: String, onClick: () -> Unit) {
        TextButton(onClick = onClick) {
            Text(
                text,
                fontSize = AUTHOR_TEXT_SIZE,
                color = Color(highEmphasisColorInt)
            )
        }
    }

    @Composable
    private fun ImageMessage(message: ChatMessage) {
        // Note: I would like a custom image body, but this works for now
        val hasCaption = (message.message != "{file}")
        CommonMessageBody(message, includePadding = hasCaption) {
            Column {
                message.activeUser = currentUser
                val imageUri = message.imageUrl
                val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
                val loadedImage = load(imageUri, context, errorPlaceholderImage)
                val height = if (hasCaption) CAPTION_WEIGHT else 1f

                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.nc_sent_an_image),
                    modifier = Modifier
                        .fillMaxHeight(height)
                )
                Text(
                    message.text,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .widthIn(20.dp, 140.dp)
                        .padding(8.dp)
                )
            }
        }
    }

    @Composable
    private fun VoiceMessage(message: ChatMessage) {
        CommonMessageBody(message) {
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
                        setWaveData(FloatArray(DEFAULT_WAVE_SIZE) { Random.nextFloat() }) // READ ONLY for now
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
    private fun GeolocationMessage(message: ChatMessage) {
        CommonMessageBody(message) {
            Column {
                if (message.messageParameters != null && message.messageParameters!!.isNotEmpty()) {
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
                    setMultiTouchControls(true)

                    val geoPoint = GeoPoint(latitude, longitude)
                    controller.setCenter(geoPoint)
                    controller.setZoom(MAP_ZOOM)

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
    private fun LinkMessage(message: ChatMessage) {
        val color = colorResource(R.color.high_emphasis_text)
        chatViewModel.getOpenGraph(currentUser.getCredentials(), currentUser.baseUrl!!, message.extractedUrlToPreview!!)

        CommonMessageBody(message) {
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
                    val graphObject = chatViewModel.getOpenGraph.asFlow().collectAsState(
                        Reference(
                            // Dummy class
                        )
                    ).value.openGraphObject
                    graphObject?.let {
                        Text(it.name, fontSize = REGULAR_TEXT_SIZE, fontWeight = FontWeight.Bold)
                        it.description?.let { Text(it, fontSize = AUTHOR_TEXT_SIZE) }
                        it.link?.let { Text(it, fontSize = TIME_TEXT_SIZE) }
                        it.thumb?.let {
                            val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
                            val loadedImage = loadImage(it, context, errorPlaceholderImage)
                            AsyncImage(
                                model = loadedImage,
                                contentDescription = stringResource(R.string.nc_sent_an_image),
                                modifier = Modifier
                                    .height(120.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PollMessage(message: ChatMessage) {
        CommonMessageBody(message) {
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

                            TextButtonNoStyling(stringResource(R.string.message_poll_tap_to_open)) {
                                // NOTE: read only for now
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DeckMessage(message: ChatMessage) {
        CommonMessageBody(message) {
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
