package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.utils.DateUtils

private const val AUTHOR_TEXT_SIZE = 12
private const val TIME_TEXT_SIZE = 12

@Composable
fun SystemMessage(
    message: ChatMessageUi
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val timeString = DateUtils(LocalContext.current).getLocalTimeStringFromTimestamp(message.timestamp)
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                message.text,
                fontSize = AUTHOR_TEXT_SIZE.sp,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.Center)
            )
            Text(
                timeString,
                fontSize = TIME_TEXT_SIZE.sp,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            )
        }
    }
}
