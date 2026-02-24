/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Composable
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private const val REGULAR_TEXT_SIZE = 16
private const val AUTHOR_TEXT_SIZE = 12
private const val TIME_TEXT_SIZE = 12

@Composable
fun LinkMessage(
    typeContent: MessageTypeContent.LinkPreview,
    message: ChatMessageUi,
    conversationThreadId: Long? = null
) {
    // val color = colorResource(R.color.high_emphasis_text)
    // adapter.viewModel.chatViewModel.getOpenGraph(
    //     adapter.currentUser.getCredentials(),
    //     adapter.currentUser.baseUrl!!,
    //     message.extractedUrlToPreview!!
    // )
    // CommonMessageBody(message, playAnimation = state.value) {
    //     EnrichedText(message)
    //     Row(
    //         modifier = Modifier
    //             .drawWithCache {
    //                 onDrawWithContent {
    //                     drawLine(
    //                         color = color,
    //                         start = Offset.Zero,
    //                         end = Offset(0f, this.size.height),
    //                         strokeWidth = 4f,
    //                         cap = StrokeCap.Round
    //                     )
    //
    //                     drawContent()
    //                 }
    //             }
    //             .padding(8.dp)
    //             .padding(4.dp)
    //     ) {
    //         Column {
    //             val graphObject = adapter.viewModel.chatViewModel.getOpenGraph.asFlow().collectAsState(
    //                 Reference(
    //                     // Dummy class
    //                 )
    //             ).value.openGraphObject
    //             graphObject?.let {
    //                 Text(it.name, fontSize = REGULAR_TEXT_SIZE.sp, fontWeight = FontWeight.Bold)
    //                 it.description?.let { Text(it, fontSize = AUTHOR_TEXT_SIZE.sp) }
    //                 it.link?.let { Text(it, fontSize = TIME_TEXT_SIZE.sp) }
    //                 it.thumb?.let {
    //                     val errorPlaceholderImage: Int = R.drawable.ic_mimetype_image
    //                     val loadedImage = load(it, LocalContext.current, errorPlaceholderImage)
    //                     AsyncImage(
    //                         model = loadedImage,
    //                         contentDescription = stringResource(R.string.nc_sent_an_image),
    //                         modifier = Modifier
    //                             .height(120.dp)
    //                     )
    //                 }
    //             }
    //         }
    //     }
    // }
}
