/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.models.json.opengraph.OpenGraphObject
import com.nextcloud.talk.ui.theme.LocalOpenGraphFetcher

private val PREVIEW_IMAGE_HEIGHT = 120.dp
private const val HTTPS_PREFIX = "https://"

@Composable
fun LinkMessage(
    typeContent: MessageTypeContent.LinkPreview,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null
) {
    val fetchOpenGraph = LocalOpenGraphFetcher.current
    val openGraphObject by produceState<OpenGraphObject?>(initialValue = null, key1 = typeContent.url) {
        if (typeContent.url.isNotEmpty()) {
            value = fetchOpenGraph(typeContent.url)
        }
    }

    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        content = {
            Column {
                EnrichedText(
                    message,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                openGraphObject?.let { og ->
                    LinkPreviewCard(og)
                }
            }
        }
    )
}

@Composable
private fun LinkPreviewCard(og: OpenGraphObject) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = og.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            og.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            og.link?.takeIf { it.isNotBlank() }?.let { link ->
                Text(
                    text = link.removePrefix(HTTPS_PREFIX),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            og.thumb?.takeIf { it.isNotBlank() }?.let { thumbUrl ->
                val loadedImage = load(
                    imageUri = thumbUrl,
                    context = LocalContext.current,
                    errorPlaceholderImage = R.drawable.ic_mimetype_image
                )
                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.nc_sent_an_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(PREVIEW_IMAGE_HEIGHT)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
