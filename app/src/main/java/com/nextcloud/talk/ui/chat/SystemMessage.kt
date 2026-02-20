package com.nextcloud.talk.ui.chat

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.chat.ui.model.ChatMessageUi

private const val AUTHOR_TEXT_SIZE = 12
private const val TIME_TEXT_SIZE = 12
private const val FLOAT_06 = 0.6f

@Composable
fun SystemMessage(
    message: ChatMessageUi
) {
    // val similarMessages = NextcloudTalkApplication.sharedApplication!!.resources.getQuantityString(
    //     R.plurals.see_similar_system_messages,
    //     message.expandableChildrenAmount,
    //     message.expandableChildrenAmount
    // )
    // Column(horizontalAlignment = Alignment.CenterHorizontally) {
    //     val timeString = DateUtils(LocalContext.current).getLocalTimeStringFromTimestamp(message.timestamp)
    //     Row(horizontalArrangement = Arrangement.Absolute.Center, verticalAlignment = Alignment.CenterVertically) {
    //         Spacer(modifier = Modifier.weight(1f))
    //         Text(
    //             message.text,
    //             fontSize = AUTHOR_TEXT_SIZE.sp,
    //             modifier = Modifier
    //                 .padding(8.dp)
    //                 .fillMaxWidth(FLOAT_06)
    //         )
    //         Text(
    //             timeString,
    //             fontSize = TIME_TEXT_SIZE.sp,
    //             textAlign = TextAlign.End,
    //             modifier = Modifier.align(Alignment.CenterVertically)
    //         )
    //         Spacer(modifier = Modifier.weight(1f))
    //     }
    //
    //     if (message.expandableChildrenAmount > 0) {
    //         TextButtonNoStyling(similarMessages) {
    //             // NOTE: Read only for now
    //         }
    //     }
    // }
}

@Composable
private fun TextButtonNoStyling(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text,
            fontSize = AUTHOR_TEXT_SIZE.sp,
            color = Color.White
        )
    }
}
