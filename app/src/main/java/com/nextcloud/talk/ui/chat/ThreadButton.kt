/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

@Composable
fun ThreadButtonComposable(replyAmount: Int = 0, incoming: Boolean = true, onButtonClick: () -> Unit = {}) {
    val themedColors = LocalViewThemeUtils.current.getColorScheme(LocalContext.current)
    val backgroundColor = if (incoming) {
        colorResource(R.color.bg_message_list_incoming_bubble)
    } else {
        themedColors.surfaceVariant
    }
    val replyAmountText = if (replyAmount == 0) {
        stringResource(R.string.thread_reply)
    } else {
        pluralStringResource(
            R.plurals.thread_replies,
            replyAmount,
            replyAmount
        )
    }

    OutlinedButton(
        onClick = onButtonClick,
        modifier = Modifier
            .padding(0.dp)
            .height(24.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, themedColors.surface),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = backgroundColor,
            contentColor = colorResource(R.color.nc_incoming_text_default)
        )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reply),
            contentDescription = stringResource(R.string.open_thread),
            modifier = Modifier
                .size(20.dp)
                .padding(start = 5.dp, end = 2.dp),
            tint = colorResource(R.color.nc_incoming_text_default)
        )
        Text(
            text = replyAmountText,
            modifier = Modifier
                .padding(end = 6.dp)
        )
    }
}

@Preview
@Composable
fun ThreadButtonPreviewMultipleReplies() {
    ThreadButtonComposable(2)
}

@Preview
@Composable
fun ThreadButtonPreviewOneReply() {
    ThreadButtonComposable(1)
}

@Preview
@Composable
fun ThreadButtonPreviewZeroReplies() {
    ThreadButtonComposable(0)
}
