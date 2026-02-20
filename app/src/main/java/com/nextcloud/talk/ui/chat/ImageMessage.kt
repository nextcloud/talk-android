/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.contacts.load
import com.nextcloud.talk.utils.DateUtils

@Composable
fun ImageMessage(
    typeContent: MessageTypeContent.Image,
    message: ChatMessageUi,
    conversationThreadId: Long? = null
) {
    val hasCaption = (message.message != "{file}")

    val timeString = DateUtils(LocalContext.current).getLocalTimeStringFromTimestamp(message.timestamp)
    MessageScaffold(
        uiMessage = message,
        conversationThreadId = conversationThreadId,
        includePadding = false,
        content = {
            Column {
                val loadedImage = load(
                    imageUri = typeContent.imageUrl,
                    context = LocalContext.current,
                    errorPlaceholderImage = typeContent.drawableResourceId
                )

                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(R.string.nc_sent_an_image),
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )

                if (hasCaption) {
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
    )

    if (!hasCaption) {
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!message.incoming) {
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.size(width = 56.dp, 0.dp)) // To account for avatar size
            }
            Text(message.text, fontSize = 12.sp)
            Text(
                timeString,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding()
                    .padding(start = 4.dp)
            )
            // if (message.readStatus == ReadStatus.NONE) {
            //     val read = painterResource(R.drawable.ic_check_all)
            //     Icon(
            //         read,
            //         "",
            //         modifier = Modifier
            //             .padding(start = 4.dp)
            //             .size(16.dp)
            //             .align(Alignment.CenterVertically)
            //     )
            // }
        }
    }
}
